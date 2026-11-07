package canal11.programacion.service;

import canal11.db.Jdbc;
import canal11.emision.service.AuditoriaService;
import canal11.programacion.repository.ParrillaRepository;
import canal11.mail.CommandRouter;
import canal11.mail.CommandSpec;
import canal11.model.Usuario;
import canal11.util.Args;
import canal11.util.CommandException;
import canal11.util.InputValidator;
import canal11.util.TextTable;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CU3 - Confeccionar y controlar la parrilla de programacion.
 * Estados: BORRADOR -> PUBLICADA -> CERRADA. En PUBLICADA cada cambio sube la version y se audita; CERRADA es solo lectura.
 * Toda modificacion corre en una transaccion con la parrilla bloqueada (FOR UPDATE) para evitar ediciones concurrentes.
 */
public class ParrillaService {

    public static final List<String> TIPOS_BLOQUE = List.of("programa", "pausa_comercial", "promo");
    static final Set<String> CAMPOS_BLOQUE = Set.of("hora_inicio", "hora_fin", "programa_id", "material_id", "tipo");

    private final AuditoriaService auditoria;
    private final ParrillaRepository repo = new ParrillaRepository();

    public ParrillaService(AuditoriaService auditoria) {
        this.auditoria = auditoria;
    }

    public void registerAll(CommandRouter r) {
        var W = CommandSpec.ADMIN_PROG;
        var R = CommandSpec.TODOS;
        r.register(new CommandSpec("INSPAR", "INSPAR[\"YYYY-MM-DD\"]", "Crea la parrilla (borrador) de una fecha", W, this::insert));
        r.register(new CommandSpec("LISPAR", "LISPAR[\"*\"] | LISPAR[\"desde\",\"hasta\"]", "Lista parrillas", R, this::list));
        r.register(new CommandSpec("GETPAR", "GETPAR[id]", "Parrilla con sus bloques", R, this::get));
        r.register(new CommandSpec("DELPAR", "DELPAR[id]", "Elimina una parrilla en borrador", W, this::delete));
        r.register(new CommandSpec("DUPPAR", "DUPPAR[id_origen,\"YYYY-MM-DD\"]", "Copia una parrilla a otra fecha", W, this::duplicar));
        r.register(new CommandSpec("INSBLO", "INSBLO[parrilla_id,programa_id,\"HH:MM\",material_id|0,\"tipo\"(,\"HH:MM fin\")]",
                "Agrega un bloque (tipo: programa|pausa_comercial|promo)", W, this::insBloque));
        r.register(new CommandSpec("UPDBLO", "UPDBLO[bloque_id,\"campo=valor\",...]",
                "Edita un bloque (hora_inicio, hora_fin, programa_id, material_id, tipo)", W, this::updBloque));
        r.register(new CommandSpec("DELBLO", "DELBLO[bloque_id]", "Quita un bloque", W, this::delBloque));
        r.register(new CommandSpec("VALPAR", "VALPAR[id]", "Revisa solapes, huecos y materiales", R, this::validar));
        r.register(new CommandSpec("PUBPAR", "PUBPAR[id]", "Publica la parrilla", W, this::publicar));
        r.register(new CommandSpec("CERPAR", "CERPAR[id]", "Cierra la parrilla (solo lectura)", W, this::cerrar));
    }

    // ------------------------------------------------------------------ parrilla

    String insert(Usuario actor, List<String> p) {
        Args.exact(p, 1, "INSPAR[\"YYYY-MM-DD\"]");
        LocalDate fecha = InputValidator.validateDate(p.get(0), "fecha");
        return Jdbc.tx(c -> {
            if (repo.existeFecha(c, fecha))
                throw new CommandException("Ya existe una parrilla para el " + fecha + ".");
            int id = repo.insert(c, fecha, actor.id());
            auditoria.registrar(c, actor.id(), "PARRILLA_CREAR", "parrillas", id, null, repo.snapshot(c, id), Args.cmdText("INSPAR", p));
            return "OK: parrilla creada con ID " + id + " para el " + fecha + " (BORRADOR)";
        });
    }

    String list(Usuario actor, List<String> p) {
        Args.count(p, 1, 2, "LISPAR[\"*\"] | LISPAR[\"desde\",\"hasta\"]");
        return Jdbc.tx(c -> {
            List<Map<String, Object>> rows;
            if (p.size() == 2) {
                LocalDate d = InputValidator.validateDate(p.get(0), "desde");
                LocalDate h = InputValidator.validateDate(p.get(1), "hasta");
                if (h.isBefore(d)) throw new CommandException("hasta no puede ser anterior a desde");
                rows = repo.listEntre(c, d, h);
            } else if (Args.isAll(p)) {
                rows = repo.listUltimas(c);
            } else {
                throw new CommandException("Uso: LISPAR[\"*\"] | LISPAR[\"desde\",\"hasta\"]");
            }
            return "Listando parrillas (" + rows.size() + "):\n" + TextTable.render(rows, "id", "fecha", "estado", "version", "bloques");
        });
    }

    String get(Usuario actor, List<String> p) {
        Args.exact(p, 1, "GETPAR[id]");
        int id = InputValidator.validateId(p.get(0), "id");
        return Jdbc.tx(c -> {
            Map<String, Object> pa = repo.findById(c, id);
            if (pa == null) throw new CommandException("Parrilla no encontrada: " + id);
            List<Map<String, Object>> bl = repo.bloquesConMaterial(c, id);
            return "Parrilla " + id + " | fecha " + pa.get("fecha") + " | " + pa.get("estado") + " | version " + pa.get("version") + "\n"
                    + TextTable.render(bl, "orden", "id", "hora_inicio", "hora_fin", "tipo", "programa", "material_id", "mat_estado");
        });
    }

    String delete(Usuario actor, List<String> p) {
        Args.exact(p, 1, "DELPAR[id]");
        int id = InputValidator.validateId(p.get(0), "id");
        return Jdbc.tx(c -> {
            Map<String, Object> pa = lock(c, id);
            if (!"BORRADOR".equals(pa.get("estado"))) throw new CommandException("Solo se pueden eliminar parrillas en BORRADOR.");
            if (repo.tieneSpots(c, id))
                throw new CommandException("La parrilla tiene spots de pauta; quitelos primero.");
            String antes = repo.snapshot(c, id);
            repo.deleteConBloques(c, id);
            auditoria.registrar(c, actor.id(), "PARRILLA_ELIMINAR", "parrillas", id, antes, null, Args.cmdText("DELPAR", p));
            return "OK: parrilla " + id + " eliminada";
        });
    }

    String duplicar(Usuario actor, List<String> p) {
        Args.exact(p, 2, "DUPPAR[id_origen,\"YYYY-MM-DD\"]");
        int origen = InputValidator.validateId(p.get(0), "id_origen");
        LocalDate fecha = InputValidator.validateDate(p.get(1), "fecha");
        return Jdbc.tx(c -> {
            if (!repo.existe(c, origen)) throw new CommandException("Parrilla origen no encontrada: " + origen);
            if (repo.existeFecha(c, fecha)) throw new CommandException("Ya existe una parrilla para el " + fecha + ".");
            int id = repo.insert(c, fecha, actor.id());
            int n = repo.copiarBloques(c, id, origen);
            auditoria.registrar(c, actor.id(), "PARRILLA_DUPLICAR", "parrillas", id, null, repo.snapshot(c, id), Args.cmdText("DUPPAR", p));
            return "OK: parrilla creada con ID " + id + " para el " + fecha + " copiando " + n + " bloque(s) de la parrilla " + origen + " (sin spots)";
        });
    }

    // ------------------------------------------------------------------ bloques

    String insBloque(Usuario actor, List<String> p) {
        String usage = "INSBLO[parrilla_id,programa_id,\"HH:MM\",material_id|0,\"tipo\"(,\"HH:MM fin\")]";
        Args.count(p, 5, 6, usage);
        int parrillaId = InputValidator.validateId(p.get(0), "parrilla_id");
        int programaId = InputValidator.validateId(p.get(1), "programa_id");
        LocalTime inicio = InputValidator.validateTime(p.get(2), "hora_inicio");
        int materialId = InputValidator.validateNonNegative(p.get(3), "material_id");
        String tipo = InputValidator.validateEnum(p.get(4), "tipo", TIPOS_BLOQUE);
        LocalTime finParam = p.size() == 6 ? InputValidator.validateTime(p.get(5), "hora_fin") : null;
        return Jdbc.tx(c -> {
            Map<String, Object> pa = lockEditable(c, parrillaId);
            LocalTime fin = validarBloque(c, pa, 0, programaId, inicio, finParam, materialId, tipo);
            int id = repo.insertBloque(c, parrillaId, programaId, materialId > 0 ? materialId : null, inicio, fin, tipo);
            renumerar(c, parrillaId);
            auditoria.registrar(c, actor.id(), "BLOQUE_CREAR", "parrilla_bloques", id, null, repo.snapshotBloque(c, id), Args.cmdText("INSBLO", p));
            subirVersion(c, actor, pa, Args.cmdText("INSBLO", p));
            return "OK: bloque creado con ID " + id + " (" + TextTable.fmt(inicio) + " - " + TextTable.fmt(fin) + ")";
        });
    }

    String updBloque(Usuario actor, List<String> p) {
        String usage = "UPDBLO[bloque_id,\"campo=valor\",...]";
        if (p.isEmpty()) throw new CommandException("Uso: " + usage);
        int id = InputValidator.validateId(p.get(0), "bloque_id");
        Map<String, String> f = Args.fields(p, 1, CAMPOS_BLOQUE, usage);
        return Jdbc.tx(c -> {
            Map<String, Object> b = repo.bloqueById(c, id);
            if (b == null) throw new CommandException("Bloque no encontrado: " + id);
            int parrillaId = ((Number) b.get("parrilla_id")).intValue();
            Map<String, Object> pa = lockEditable(c, parrillaId);
            b = repo.bloqueById(c, id); // releer con la parrilla bloqueada
            String antes = repo.snapshotBloque(c, id);

            LocalTime inicio = (LocalTime) b.get("hora_inicio"), fin = (LocalTime) b.get("hora_fin");
            int programaId = ((Number) b.get("programa_id")).intValue();
            int materialId = b.get("material_id") == null ? 0 : ((Number) b.get("material_id")).intValue();
            String tipo = (String) b.get("tipo");
            long duracionActualSeg = java.time.Duration.between(inicio, fin).getSeconds();
            boolean finExplicito = f.containsKey("hora_fin");
            for (Map.Entry<String, String> e : f.entrySet()) {
                String v = e.getValue();
                switch (e.getKey()) {
                    case "hora_inicio" -> inicio = InputValidator.validateTime(v, "hora_inicio");
                    case "hora_fin" -> fin = InputValidator.validateTime(v, "hora_fin");
                    case "programa_id" -> programaId = InputValidator.validateId(v, "programa_id");
                    case "material_id" -> materialId = InputValidator.validateNonNegative(v, "material_id");
                    default -> tipo = InputValidator.validateEnum(v, "tipo", TIPOS_BLOQUE);
                }
            }
            if (f.containsKey("hora_inicio") && !finExplicito) { // mover el bloque conserva su duracion
                fin = inicio.plusSeconds(duracionActualSeg);
                if (!fin.isAfter(inicio)) throw new CommandException("El bloque cruza la medianoche; indique hora_fin explicita (maximo 23:59:59).");
            }
            fin = validarBloque(c, pa, id, programaId, inicio, fin, materialId, tipo);
            exigirSpotsCompatibles(c, id, tipo, inicio, fin);
            repo.updateBloque(c, id, programaId, materialId > 0 ? materialId : null, inicio, fin, tipo);
            renumerar(c, parrillaId);
            auditoria.registrar(c, actor.id(), "BLOQUE_ACTUALIZAR", "parrilla_bloques", id, antes, repo.snapshotBloque(c, id), Args.cmdText("UPDBLO", p));
            subirVersion(c, actor, pa, Args.cmdText("UPDBLO", p));
            return "OK: bloque " + id + " actualizado (" + TextTable.fmt(inicio) + " - " + TextTable.fmt(fin) + ")";
        });
    }

    String delBloque(Usuario actor, List<String> p) {
        Args.exact(p, 1, "DELBLO[bloque_id]");
        int id = InputValidator.validateId(p.get(0), "bloque_id");
        return Jdbc.tx(c -> {
            Map<String, Object> b = repo.bloqueById(c, id);
            if (b == null) throw new CommandException("Bloque no encontrado: " + id);
            int parrillaId = ((Number) b.get("parrilla_id")).intValue();
            Map<String, Object> pa = lockEditable(c, parrillaId);
            if (repo.bloqueTieneSpots(c, id))
                throw new CommandException("El bloque tiene spots de pauta; quitelos primero (DELSPO).");
            String antes = repo.snapshotBloque(c, id);
            repo.deleteBloque(c, id);
            renumerar(c, parrillaId);
            auditoria.registrar(c, actor.id(), "BLOQUE_ELIMINAR", "parrilla_bloques", id, antes, null, Args.cmdText("DELBLO", p));
            subirVersion(c, actor, pa, Args.cmdText("DELBLO", p));
            return "OK: bloque " + id + " eliminado";
        });
    }

    // ------------------------------------------------------------------ control de estado

    String validar(Usuario actor, List<String> p) {
        Args.exact(p, 1, "VALPAR[id]");
        int id = InputValidator.validateId(p.get(0), "id");
        return Jdbc.tx(c -> {
            Map<String, Object> pa = repo.findById(c, id);
            if (pa == null) throw new CommandException("Parrilla no encontrada: " + id);
            Analisis a = analizar(c, id);
            StringBuilder sb = new StringBuilder("Validacion de la parrilla " + id + " (" + pa.get("fecha") + ", " + pa.get("estado") + "):\n");
            sb.append(a.errores.isEmpty() ? "OK: sin errores.\n" : "ERRORES (" + a.errores.size() + "):\n- " + String.join("\n- ", a.errores) + "\n");
            sb.append(a.huecos.isEmpty() ? "Sin huecos en el dia." : "ALERTAS - huecos sin programacion:\n- " + String.join("\n- ", a.huecos));
            return sb.toString().stripTrailing();
        });
    }

    String publicar(Usuario actor, List<String> p) {
        Args.exact(p, 1, "PUBPAR[id]");
        int id = InputValidator.validateId(p.get(0), "id");
        return Jdbc.tx(c -> {
            Map<String, Object> pa = lock(c, id);
            if (!"BORRADOR".equals(pa.get("estado"))) throw new CommandException("Solo se publican parrillas en BORRADOR (estado actual: " + pa.get("estado") + ").");
            Analisis a = analizar(c, id);
            if (!a.errores.isEmpty()) throw new CommandException("No se puede publicar la parrilla " + id + ":\n- " + String.join("\n- ", a.errores));
            String antes = repo.snapshot(c, id);
            repo.publicar(c, id);
            auditoria.registrar(c, actor.id(), "PARRILLA_PUBLICAR", "parrillas", id, antes, repo.snapshot(c, id), Args.cmdText("PUBPAR", p));
            return "OK: parrilla " + id + " PUBLICADA" + (a.huecos.isEmpty() ? "" : "\nALERTA: hay " + a.huecos.size() + " hueco(s) sin programacion (VALPAR[" + id + "]).");
        });
    }

    String cerrar(Usuario actor, List<String> p) {
        Args.exact(p, 1, "CERPAR[id]");
        int id = InputValidator.validateId(p.get(0), "id");
        return Jdbc.tx(c -> {
            Map<String, Object> pa = lock(c, id);
            if (!"PUBLICADA".equals(pa.get("estado"))) throw new CommandException("Solo se cierran parrillas PUBLICADAS (estado actual: " + pa.get("estado") + ").");
            String antes = repo.snapshot(c, id);
            repo.cerrar(c, id);
            auditoria.registrar(c, actor.id(), "PARRILLA_CERRAR", "parrillas", id, antes, repo.snapshot(c, id), Args.cmdText("CERPAR", p));
            return "OK: parrilla " + id + " CERRADA (solo lectura)";
        });
    }

    // ------------------------------------------------------------------ nucleo compartido

    private Map<String, Object> lock(Connection c, int id) throws SQLException {
        Map<String, Object> pa = repo.findByIdForUpdate(c, id);
        if (pa == null) throw new CommandException("Parrilla no encontrada: " + id);
        return pa;
    }

    private Map<String, Object> lockEditable(Connection c, int id) throws SQLException {
        Map<String, Object> pa = lock(c, id);
        if ("CERRADA".equals(pa.get("estado"))) throw new CommandException("La parrilla " + id + " esta CERRADA (solo lectura).");
        return pa;
    }

    /** En una parrilla PUBLICADA cada modificacion sube la version y deja traza en la auditoria de la parrilla. */
    private void subirVersion(Connection c, Usuario actor, Map<String, Object> pa, String comando) throws SQLException {
        if (!"PUBLICADA".equals(pa.get("estado"))) return;
        int id = ((Number) pa.get("id")).intValue();
        String antes = repo.snapshot(c, id);
        repo.subirVersion(c, id);
        auditoria.registrar(c, actor.id(), "PARRILLA_MODIFICADA", "parrillas", id, antes, repo.snapshot(c, id), comando);
    }

    /** orden = posicion del bloque segun su hora de inicio. */
    private void renumerar(Connection c, int parrillaId) throws SQLException {
        repo.renumerar(c, parrillaId);
    }

    /**
     * Valida un bloque (nuevo o editado) y devuelve su hora_fin definitiva.
     * excluirBloque = 0 para un bloque nuevo.
     */
    private LocalTime validarBloque(Connection c, Map<String, Object> pa, int excluirBloque, int programaId,
                                           LocalTime inicio, LocalTime fin, int materialId, String tipo) throws SQLException {
        Map<String, Object> prog = repo.programaActivo(c, programaId);
        if (prog == null) throw new CommandException("programa_id no existe o esta inactivo: " + programaId);
        if (fin == null) {
            fin = inicio.plusMinutes(((Number) prog.get("duracion_min")).longValue());
            if (!fin.isAfter(inicio)) {
                throw new CommandException("El programa dura " + prog.get("duracion_min") + " min y cruza la medianoche; indique hora_fin explicita (maximo 23:59:59).");
            }
        }
        if (!fin.isAfter(inicio)) throw new CommandException("hora_fin debe ser mayor a hora_inicio.");

        if (tipo.equals("pausa_comercial")) {
            if (materialId != 0) throw new CommandException("Una pausa comercial no lleva material (los spots se agregan con INSSPO).");
        } else {
            boolean publicada = "PUBLICADA".equals(pa.get("estado"));
            if (materialId > 0) {
                Map<String, Object> m = repo.materialActivo(c, materialId);
                if (m == null) throw new CommandException("material_id no existe o esta de baja: " + materialId);
                if ("spot".equals(m.get("tipo"))) throw new CommandException("Un spot solo puede ir en pautas (INSSPO), no en un bloque.");
                if (publicada && !"VALIDADO".equals(m.get("estado")))
                    throw new CommandException("La parrilla esta PUBLICADA: el material debe estar VALIDADO (estado: " + m.get("estado") + ").");
            } else if (publicada) {
                throw new CommandException("La parrilla esta PUBLICADA: el bloque requiere un material VALIDADO.");
            }
        }
        List<Map<String, Object>> solapes = repo.solapes(c, ((Number) pa.get("id")).intValue(), excluirBloque, fin, inicio);
        if (!solapes.isEmpty()) {
            Map<String, Object> s = solapes.get(0);
            throw new CommandException("El bloque se solapa con el bloque " + s.get("id") + " (" + TextTable.fmt(s.get("hora_inicio"))
                    + " - " + TextTable.fmt(s.get("hora_fin")) + ").");
        }
        return fin;
    }

    /** Un bloque con spots debe seguir siendo pausa comercial y contenerlos. */
    private void exigirSpotsCompatibles(Connection c, int bloqueId, String tipo, LocalTime inicio, LocalTime fin) throws SQLException {
        if (!repo.bloqueTieneSpots(c, bloqueId)) return;
        if (!tipo.equals("pausa_comercial")) throw new CommandException("El bloque tiene spots; debe seguir siendo pausa_comercial.");
        long fuera = repo.spotsFueraDeHorario(c, bloqueId, inicio, fin);
        if (fuera > 0) throw new CommandException("Hay " + fuera + " spot(s) que quedarian fuera del nuevo horario del bloque.");
    }

    private record Analisis(List<String> errores, List<String> huecos) {}

    private Analisis analizar(Connection c, int parrillaId) throws SQLException {
        List<String> errores = new ArrayList<>(), huecos = new ArrayList<>();
        List<Map<String, Object>> bl = repo.bloquesParaAnalisis(c, parrillaId);
        if (bl.isEmpty()) {
            errores.add("la parrilla no tiene bloques");
            return new Analisis(errores, huecos);
        }
        LocalTime cursor = LocalTime.MIN;
        for (Map<String, Object> b : bl) {
            LocalTime ini = (LocalTime) b.get("hora_inicio"), fin = (LocalTime) b.get("hora_fin");
            if (ini.isBefore(cursor)) errores.add("el bloque " + b.get("id") + " se solapa con el anterior (" + TextTable.fmt(ini) + ")");
            else if (ini.isAfter(cursor)) huecos.add(TextTable.fmt(cursor) + " - " + TextTable.fmt(ini));
            if (fin.isAfter(cursor)) cursor = fin;
            if (!"pausa_comercial".equals(b.get("tipo"))) {
                if (b.get("material_id") == null) errores.add("el bloque " + b.get("id") + " (" + TextTable.fmt(ini) + ") no tiene material");
                else if (!"VALIDADO".equals(b.get("mat_estado")))
                    errores.add("el bloque " + b.get("id") + " (" + TextTable.fmt(ini) + ") usa el material " + b.get("material_id") + " en estado " + b.get("mat_estado"));
            }
        }
        if (cursor.isBefore(LocalTime.of(23, 59, 59))) huecos.add(TextTable.fmt(cursor) + " - 24:00:00");
        return new Analisis(errores, huecos);
    }
}
