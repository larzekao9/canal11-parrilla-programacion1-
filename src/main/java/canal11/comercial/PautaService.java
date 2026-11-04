package canal11.comercial;

import canal11.db.Jdbc;
import canal11.emision.AuditoriaService;
import canal11.mail.CommandRouter;
import canal11.mail.CommandSpec;
import canal11.model.Usuario;
import canal11.util.Args;
import canal11.util.CommandException;
import canal11.util.InputValidator;
import canal11.util.TextTable;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CU6 (parte 2) - Pautas publicitarias y spots comerciales.
 * Un spot (material tipo spot, VALIDADO) se coloca en una pausa comercial de la parrilla de una fecha dentro de la vigencia
 * de una pauta ACTIVA, respetando la capacidad de la pausa, sin solapes y sin superar los pases contratados.
 */
public class PautaService {

    static final List<String> ESTADOS = List.of("ACTIVA", "PAUSADA", "FINALIZADA");
    static final Set<String> CAMPOS = Set.of("nombre", "fecha_inicio", "fecha_fin", "tarifa_spot", "max_pases", "estado");
    private static final String[] COLS = {"id", "cliente", "nombre", "fecha_inicio", "fecha_fin", "tarifa_spot", "max_pases", "usados", "estado"};

    private final AuditoriaService auditoria;

    public PautaService(AuditoriaService auditoria) {
        this.auditoria = auditoria;
    }

    public void registerAll(CommandRouter r) {
        var W = CommandSpec.ADMIN_PROG;
        var R = CommandSpec.TODOS;
        r.register(new CommandSpec("INSPAU", "INSPAU[cliente_id,\"nombre\",\"fecha_inicio\",\"fecha_fin\",tarifa,max_pases]", "Crea una pauta", W, this::insert));
        r.register(new CommandSpec("LISPAU", "LISPAU[\"*\"] | LISPAU[cliente_id]", "Lista pautas", R, this::list));
        r.register(new CommandSpec("GETPAU", "GETPAU[id]", "Detalle de una pauta", R, this::get));
        r.register(new CommandSpec("UPDPAU", "UPDPAU[id,\"campo=valor\",...]", "Actualiza una pauta", W, this::update));
        r.register(new CommandSpec("DELPAU", "DELPAU[id]", "Elimina una pauta sin spots", W, this::delete));
        r.register(new CommandSpec("INSSPO", "INSSPO[pauta_id,material_id,bloque_id,\"HH:MM:SS\"]", "Inserta un spot en una pausa comercial", W, this::insSpot));
        r.register(new CommandSpec("DELSPO", "DELSPO[spot_id]", "Quita un spot programado", W, this::delSpot));
        r.register(new CommandSpec("LISSPO", "LISSPO[pauta_id]", "Spots de una pauta", R, this::listSpots));
        r.register(new CommandSpec("PAUPAR", "PAUPAR[parrilla_id]", "Pauta del dia por pausa comercial", R, this::pautaDelDia));
    }

    // ------------------------------------------------------------------ pautas

    String insert(Usuario actor, List<String> p) {
        Args.exact(p, 6, "INSPAU[cliente_id,\"nombre\",\"fecha_inicio\",\"fecha_fin\",tarifa,max_pases]");
        int clienteId = InputValidator.validateId(p.get(0), "cliente_id");
        String nombre = InputValidator.validateText(p.get(1), "nombre", 160);
        LocalDate fi = InputValidator.validateDate(p.get(2), "fecha_inicio");
        LocalDate ff = InputValidator.validateDate(p.get(3), "fecha_fin");
        BigDecimal tarifa = tarifa(p.get(4));
        int max = InputValidator.validatePositive(p.get(5), "max_pases");
        if (ff.isBefore(fi)) throw new CommandException("fecha_fin no puede ser anterior a fecha_inicio");
        return Jdbc.tx(c -> {
            if (Jdbc.count(c, "SELECT 1 FROM clientes WHERE id = ? AND activo", clienteId) == 0)
                throw new CommandException("cliente_id no existe o esta inactivo: " + clienteId);
            int id = Jdbc.insert(c, "INSERT INTO pautas (cliente_id, nombre, fecha_inicio, fecha_fin, tarifa_spot, max_pases, creado_por) "
                    + "VALUES (?,?,?,?,?,?,?) RETURNING id", clienteId, nombre, fi, ff, tarifa, max, actor.id());
            auditoria.registrar(c, actor.id(), "PAUTA_CREAR", "pautas", id, null, Jdbc.json(c, "pautas", id), Args.cmdText("INSPAU", p));
            return "OK: pauta creada con ID " + id;
        });
    }

    private static final String SELECT_PAUTA = "SELECT pt.id, cl.razon_social AS cliente, pt.nombre, pt.fecha_inicio, pt.fecha_fin, pt.tarifa_spot, pt.max_pases, "
            + "(SELECT count(*) FROM pauta_spots s WHERE s.pauta_id = pt.id) AS usados, pt.estado "
            + "FROM pautas pt JOIN clientes cl ON cl.id = pt.cliente_id";

    String list(Usuario actor, List<String> p) {
        Args.exact(p, 1, "LISPAU[\"*\"] | LISPAU[cliente_id]");
        return Jdbc.tx(c -> {
            List<Map<String, Object>> rows = Args.isAll(p)
                    ? Jdbc.query(c, SELECT_PAUTA + " ORDER BY pt.id")
                    : Jdbc.query(c, SELECT_PAUTA + " WHERE pt.cliente_id = ? ORDER BY pt.id", InputValidator.validateId(p.get(0), "cliente_id"));
            return "Listando pautas (" + rows.size() + "):\n" + TextTable.render(rows, COLS);
        });
    }

    String get(Usuario actor, List<String> p) {
        Args.exact(p, 1, "GETPAU[id]");
        int id = InputValidator.validateId(p.get(0), "id");
        return Jdbc.tx(c -> {
            Map<String, Object> r = Jdbc.one(c, SELECT_PAUTA + " WHERE pt.id = ?", id);
            if (r == null) throw new CommandException("Pauta no encontrada: " + id);
            return TextTable.detail(r, COLS) + "\nPases disponibles: " + (((Number) r.get("max_pases")).longValue() - ((Number) r.get("usados")).longValue());
        });
    }

    String update(Usuario actor, List<String> p) {
        String usage = "UPDPAU[id,\"campo=valor\",...]";
        if (p.isEmpty()) throw new CommandException("Uso: " + usage);
        int id = InputValidator.validateId(p.get(0), "id");
        Map<String, String> f = Args.fields(p, 1, CAMPOS, usage);
        return Jdbc.tx(c -> {
            Map<String, Object> cur = Jdbc.one(c, "SELECT * FROM pautas WHERE id = ? FOR UPDATE", id);
            if (cur == null) throw new CommandException("Pauta no encontrada: " + id);
            String antes = Jdbc.json(c, "pautas", id);
            LocalDate fi = (LocalDate) cur.get("fecha_inicio"), ff = (LocalDate) cur.get("fecha_fin");
            List<String> sets = new ArrayList<>();
            List<Object> vals = new ArrayList<>();
            int max = ((Number) cur.get("max_pases")).intValue();
            for (Map.Entry<String, String> e : f.entrySet()) {
                String v = e.getValue();
                Object val;
                switch (e.getKey()) {
                    case "nombre" -> val = InputValidator.validateText(v, "nombre", 160);
                    case "fecha_inicio" -> { fi = InputValidator.validateDate(v, "fecha_inicio"); val = fi; }
                    case "fecha_fin" -> { ff = InputValidator.validateDate(v, "fecha_fin"); val = ff; }
                    case "tarifa_spot" -> val = tarifa(v);
                    case "max_pases" -> { max = InputValidator.validatePositive(v, "max_pases"); val = max; }
                    default -> val = InputValidator.validateEnum(v, "estado", ESTADOS);
                }
                sets.add(e.getKey() + " = ?");
                vals.add(val);
            }
            if (ff.isBefore(fi)) throw new CommandException("fecha_fin no puede ser anterior a fecha_inicio");
            long usados = Jdbc.count(c, "SELECT count(*) FROM pauta_spots WHERE pauta_id = ?", id);
            if (max < usados) throw new CommandException("max_pases (" + max + ") es menor que los spots ya programados (" + usados + ").");
            if (Jdbc.count(c, "SELECT count(*) FROM pauta_spots s JOIN parrilla_bloques b ON b.id = s.parrilla_bloque_id "
                    + "JOIN parrillas pa ON pa.id = b.parrilla_id WHERE s.pauta_id = ? AND (pa.fecha < ? OR pa.fecha > ?)", id, fi, ff) > 0)
                throw new CommandException("Hay spots programados fuera de la nueva vigencia; quitelos primero (DELSPO).");
            vals.add(id);
            Jdbc.exec(c, "UPDATE pautas SET " + String.join(", ", sets) + " WHERE id = ?", vals.toArray());
            auditoria.registrar(c, actor.id(), "PAUTA_ACTUALIZAR", "pautas", id, antes, Jdbc.json(c, "pautas", id), Args.cmdText("UPDPAU", p));
            return "OK: pauta " + id + " actualizada";
        });
    }

    String delete(Usuario actor, List<String> p) {
        Args.exact(p, 1, "DELPAU[id]");
        int id = InputValidator.validateId(p.get(0), "id");
        return Jdbc.tx(c -> {
            if (Jdbc.one(c, "SELECT id FROM pautas WHERE id = ? FOR UPDATE", id) == null) throw new CommandException("Pauta no encontrada: " + id);
            if (Jdbc.count(c, "SELECT 1 FROM pauta_spots WHERE pauta_id = ?", id) > 0)
                throw new CommandException("La pauta tiene spots; use UPDPAU[" + id + ",\"estado=FINALIZADA\"] en lugar de eliminarla.");
            String antes = Jdbc.json(c, "pautas", id);
            Jdbc.exec(c, "DELETE FROM pautas WHERE id = ?", id);
            auditoria.registrar(c, actor.id(), "PAUTA_ELIMINAR", "pautas", id, antes, null, Args.cmdText("DELPAU", p));
            return "OK: pauta " + id + " eliminada";
        });
    }

    // ------------------------------------------------------------------ spots

    String insSpot(Usuario actor, List<String> p) {
        Args.exact(p, 4, "INSSPO[pauta_id,material_id,bloque_id,\"HH:MM:SS\"]");
        int pautaId = InputValidator.validateId(p.get(0), "pauta_id");
        int materialId = InputValidator.validateId(p.get(1), "material_id");
        int bloqueId = InputValidator.validateId(p.get(2), "bloque_id");
        LocalTime hora = InputValidator.validateTime(p.get(3), "hora");
        return Jdbc.tx(c -> {
            Map<String, Object> pauta = Jdbc.one(c, "SELECT * FROM pautas WHERE id = ? FOR UPDATE", pautaId);
            if (pauta == null) throw new CommandException("Pauta no encontrada: " + pautaId);
            if (!"ACTIVA".equals(pauta.get("estado"))) throw new CommandException("La pauta esta " + pauta.get("estado") + "; solo las ACTIVAS reciben spots.");

            Map<String, Object> b = Jdbc.one(c, "SELECT b.*, pa.fecha, pa.estado AS parrilla_estado FROM parrilla_bloques b "
                    + "JOIN parrillas pa ON pa.id = b.parrilla_id WHERE b.id = ? FOR UPDATE OF b", bloqueId);
            if (b == null) throw new CommandException("Bloque no encontrado: " + bloqueId);
            if (!"pausa_comercial".equals(b.get("tipo"))) throw new CommandException("El bloque " + bloqueId + " no es una pausa comercial.");
            if ("CERRADA".equals(b.get("parrilla_estado"))) throw new CommandException("La parrilla del bloque esta CERRADA.");
            LocalDate fecha = (LocalDate) b.get("fecha");
            if (fecha.isBefore((LocalDate) pauta.get("fecha_inicio")) || fecha.isAfter((LocalDate) pauta.get("fecha_fin")))
                throw new CommandException("La fecha de la parrilla (" + fecha + ") esta fuera de la vigencia de la pauta ("
                        + pauta.get("fecha_inicio") + " a " + pauta.get("fecha_fin") + ").");

            Map<String, Object> m = Jdbc.one(c, "SELECT * FROM materiales WHERE id = ? AND activo", materialId);
            if (m == null) throw new CommandException("material_id no existe o esta de baja: " + materialId);
            if (!"spot".equals(m.get("tipo"))) throw new CommandException("El material " + materialId + " no es de tipo spot.");
            if (!"VALIDADO".equals(m.get("estado"))) throw new CommandException("El material " + materialId + " no esta VALIDADO (estado: " + m.get("estado") + ").");
            int dur = ((Number) m.get("duracion_seg")).intValue();

            LocalTime bIni = (LocalTime) b.get("hora_inicio"), bFin = (LocalTime) b.get("hora_fin");
            int ini = hora.toSecondOfDay(), fin = ini + dur;
            if (ini < bIni.toSecondOfDay() || fin > bFin.toSecondOfDay())
                throw new CommandException("El spot (" + TextTable.fmt(hora) + ", " + dur + " s) no cabe en la pausa " + TextTable.fmt(bIni) + " - " + TextTable.fmt(bFin) + ".");

            long usados = Jdbc.count(c, "SELECT count(*) FROM pauta_spots WHERE pauta_id = ?", pautaId);
            if (usados >= ((Number) pauta.get("max_pases")).longValue())
                throw new CommandException("La pauta ya uso todos sus pases contratados (" + pauta.get("max_pases") + ").");

            for (Map<String, Object> s : Jdbc.query(c, "SELECT s.id, s.hora_programada, m.duracion_seg FROM pauta_spots s JOIN materiales m ON m.id = s.material_id "
                    + "WHERE s.parrilla_bloque_id = ?", bloqueId)) {
                int sIni = ((LocalTime) s.get("hora_programada")).toSecondOfDay();
                int sFin = sIni + ((Number) s.get("duracion_seg")).intValue();
                if (ini < sFin && fin > sIni)
                    throw new CommandException("El spot se solapa con el spot " + s.get("id") + " (" + TextTable.fmt(s.get("hora_programada")) + ").");
            }
            int id = Jdbc.insert(c, "INSERT INTO pauta_spots (pauta_id, material_id, parrilla_bloque_id, hora_programada) VALUES (?,?,?,?) RETURNING id",
                    pautaId, materialId, bloqueId, hora);
            auditoria.registrar(c, actor.id(), "SPOT_CREAR", "pauta_spots", id, null, Jdbc.json(c, "pauta_spots", id), Args.cmdText("INSSPO", p));
            return "OK: spot " + id + " insertado en la pausa " + TextTable.fmt(bIni) + " del " + fecha + " a las " + TextTable.fmt(hora);
        });
    }

    String delSpot(Usuario actor, List<String> p) {
        Args.exact(p, 1, "DELSPO[spot_id]");
        int id = InputValidator.validateId(p.get(0), "spot_id");
        return Jdbc.tx(c -> {
            Map<String, Object> s = Jdbc.one(c, "SELECT s.*, pa.estado AS parrilla_estado FROM pauta_spots s JOIN parrilla_bloques b ON b.id = s.parrilla_bloque_id "
                    + "JOIN parrillas pa ON pa.id = b.parrilla_id WHERE s.id = ? FOR UPDATE OF s", id);
            if (s == null) throw new CommandException("Spot no encontrado: " + id);
            if (!"PROGRAMADO".equals(s.get("estado"))) throw new CommandException("El spot ya fue " + s.get("estado") + "; no se puede quitar.");
            if ("CERRADA".equals(s.get("parrilla_estado"))) throw new CommandException("La parrilla esta CERRADA.");
            String antes = Jdbc.json(c, "pauta_spots", id);
            Jdbc.exec(c, "DELETE FROM pauta_spots WHERE id = ?", id);
            auditoria.registrar(c, actor.id(), "SPOT_ELIMINAR", "pauta_spots", id, antes, null, Args.cmdText("DELSPO", p));
            return "OK: spot " + id + " eliminado";
        });
    }

    String listSpots(Usuario actor, List<String> p) {
        Args.exact(p, 1, "LISSPO[pauta_id]");
        int id = InputValidator.validateId(p.get(0), "pauta_id");
        return Jdbc.tx(c -> {
            if (Jdbc.one(c, "SELECT id FROM pautas WHERE id = ?", id) == null) throw new CommandException("Pauta no encontrada: " + id);
            List<Map<String, Object>> rows = Jdbc.query(c, "SELECT s.id, pa.fecha, s.hora_programada AS hora, m.titulo, m.duracion_seg AS seg, s.estado, b.id AS bloque_id "
                    + "FROM pauta_spots s JOIN materiales m ON m.id = s.material_id JOIN parrilla_bloques b ON b.id = s.parrilla_bloque_id "
                    + "JOIN parrillas pa ON pa.id = b.parrilla_id WHERE s.pauta_id = ? ORDER BY pa.fecha, s.hora_programada", id);
            return "Spots de la pauta " + id + " (" + rows.size() + "):\n" + TextTable.render(rows, "id", "fecha", "hora", "titulo", "seg", "estado", "bloque_id");
        });
    }

    String pautaDelDia(Usuario actor, List<String> p) {
        Args.exact(p, 1, "PAUPAR[parrilla_id]");
        int id = InputValidator.validateId(p.get(0), "parrilla_id");
        return Jdbc.tx(c -> {
            Map<String, Object> pa = Jdbc.one(c, "SELECT * FROM parrillas WHERE id = ?", id);
            if (pa == null) throw new CommandException("Parrilla no encontrada: " + id);
            StringBuilder sb = new StringBuilder("Pauta de la parrilla " + id + " (" + pa.get("fecha") + ", " + pa.get("estado") + "):");
            List<Map<String, Object>> pausas = Jdbc.query(c, "SELECT id, hora_inicio, hora_fin, EXTRACT(EPOCH FROM (hora_fin - hora_inicio))::int AS cap "
                    + "FROM parrilla_bloques WHERE parrilla_id = ? AND tipo = 'pausa_comercial' ORDER BY hora_inicio", id);
            if (pausas.isEmpty()) return sb.append("\n(la parrilla no tiene pausas comerciales)").toString();
            for (Map<String, Object> pz : pausas) {
                List<Map<String, Object>> spots = Jdbc.query(c, "SELECT s.id, s.hora_programada AS hora, cl.razon_social AS cliente, m.titulo, m.duracion_seg AS seg, s.estado "
                        + "FROM pauta_spots s JOIN pautas pt ON pt.id = s.pauta_id JOIN clientes cl ON cl.id = pt.cliente_id "
                        + "JOIN materiales m ON m.id = s.material_id WHERE s.parrilla_bloque_id = ? ORDER BY s.hora_programada", pz.get("id"));
                int usados = spots.stream().mapToInt(s -> ((Number) s.get("seg")).intValue()).sum();
                int cap = ((Number) pz.get("cap")).intValue();
                sb.append("\n\nPausa ").append(pz.get("id")).append(" ").append(TextTable.fmt(pz.get("hora_inicio"))).append(" - ")
                  .append(TextTable.fmt(pz.get("hora_fin"))).append(" | usados ").append(usados).append(" s de ").append(cap)
                  .append(" s (disponibles ").append(cap - usados).append(" s)\n")
                  .append(TextTable.render(spots, "id", "hora", "cliente", "titulo", "seg", "estado"));
            }
            return sb.toString();
        });
    }

    private static BigDecimal tarifa(String v) {
        try {
            BigDecimal t = new BigDecimal(v.trim());
            if (t.signum() <= 0) throw new CommandException("tarifa debe ser mayor a 0");
            return t;
        } catch (NumberFormatException e) {
            throw new CommandException("tarifa debe ser un numero (ej. 150.50)");
        }
    }
}
