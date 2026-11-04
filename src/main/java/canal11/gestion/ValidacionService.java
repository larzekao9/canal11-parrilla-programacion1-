package canal11.gestion;

import canal11.db.DbConfig;
import canal11.db.Jdbc;
import canal11.emision.AuditoriaService;
import canal11.mail.CommandRouter;
import canal11.mail.CommandSpec;
import canal11.model.Usuario;
import canal11.util.Args;
import canal11.util.CommandException;
import canal11.util.InputValidator;
import canal11.util.TextTable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CU5 - Validar estado de emision. Transiciones: PENDIENTE -> VALIDADO | RECHAZADO; VALIDADO -> RECHAZADO.
 * Un material RECHAZADO vuelve a PENDIENTE al corregirlo con UPDMAT. ESTEMI resume si cada bloque esta listo.
 */
public class ValidacionService {

    private final AuditoriaService auditoria;

    public ValidacionService(AuditoriaService auditoria) {
        this.auditoria = auditoria;
    }

    public void registerAll(CommandRouter r) {
        r.register(new CommandSpec("VALMAT", "VALMAT[id] | VALMAT[id,\"observaciones\"]", "Valida un material",
                CommandSpec.ADMIN_ENC, this::validar));
        r.register(new CommandSpec("RECMAT", "RECMAT[id,\"motivo\"]", "Rechaza un material (motivo obligatorio)",
                CommandSpec.ADMIN_ENC, this::rechazar));
        r.register(new CommandSpec("ESTEMI", "ESTEMI[parrilla_id]", "Estado de emision por bloque",
                CommandSpec.TODOS, this::estadoEmision));
    }

    private static int tolerancia() {
        return Integer.parseInt(DbConfig.get("validacion.tolerancia.seg", "5"));
    }

    String validar(Usuario actor, List<String> p) {
        Args.count(p, 1, 2, "VALMAT[id] | VALMAT[id,\"observaciones\"]");
        int id = InputValidator.validateId(p.get(0), "id");
        String obs = p.size() > 1 && !p.get(1).isBlank() ? p.get(1).trim() : null;
        return Jdbc.tx(c -> {
            Map<String, Object> m = Jdbc.one(c, "SELECT * FROM materiales WHERE id = ? AND activo FOR UPDATE", id);
            if (m == null) throw new CommandException("Material no encontrado: " + id);
            String estado = (String) m.get("estado");
            if (estado.equals("VALIDADO")) throw new CommandException("El material ya esta VALIDADO.");
            if (estado.equals("RECHAZADO"))
                throw new CommandException("El material esta RECHAZADO; corrijalo con UPDMAT (vuelve a PENDIENTE) y valide de nuevo.");

            List<String> fallos = new ArrayList<>();
            Path path = Path.of((String) m.get("ruta_archivo"));
            if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
                fallos.add("el archivo no existe o no es legible: " + path);
            } else {
                try {
                    String hash = canal11.util.FileHash.sha256(path);
                    if (!hash.equals(String.valueOf(m.get("hash_sha256")).trim()))
                        fallos.add("el archivo cambio desde que se registro (hash distinto); actualicelo con UPDMAT");
                } catch (IOException e) {
                    fallos.add("no se pudo leer el archivo: " + e.getMessage());
                }
            }
            int dur = ((Number) m.get("duracion_seg")).intValue();
            for (Map<String, Object> b : Jdbc.query(c,
                    "SELECT pa.fecha, b.hora_inicio, EXTRACT(EPOCH FROM (b.hora_fin - b.hora_inicio))::int AS seg "
                  + "FROM parrilla_bloques b JOIN parrillas pa ON pa.id = b.parrilla_id "
                  + "WHERE b.material_id = ? AND pa.estado IN ('BORRADOR','PUBLICADA') AND pa.fecha >= CURRENT_DATE", id)) {
                int seg = ((Number) b.get("seg")).intValue();
                if (Math.abs(seg - dur) > tolerancia()) {
                    fallos.add("la duracion (" + dur + " s) no coincide con el bloque del " + b.get("fecha") + " "
                            + TextTable.fmt(b.get("hora_inicio")) + " (" + seg + " s, tolerancia " + tolerancia() + " s)");
                }
            }
            if (!fallos.isEmpty()) throw new CommandException("No se puede validar el material " + id + ":\n- " + String.join("\n- ", fallos));

            String antes = Jdbc.json(c, "materiales", id);
            Jdbc.exec(c, "UPDATE materiales SET estado = 'VALIDADO', validado_por = ?, validado_at = now(), observaciones = COALESCE(?, observaciones) WHERE id = ?",
                    actor.id(), obs, id);
            auditoria.registrar(c, actor.id(), "MATERIAL_VALIDAR", "materiales", id, antes, Jdbc.json(c, "materiales", id), Args.cmdText("VALMAT", p));
            return "OK: material " + id + " VALIDADO por " + actor.email();
        });
    }

    String rechazar(Usuario actor, List<String> p) {
        Args.exact(p, 2, "RECMAT[id,\"motivo\"]");
        int id = InputValidator.validateId(p.get(0), "id");
        String motivo = InputValidator.validateText(p.get(1), "motivo", 500);
        return Jdbc.tx(c -> {
            Map<String, Object> m = Jdbc.one(c, "SELECT * FROM materiales WHERE id = ? AND activo FOR UPDATE", id);
            if (m == null) throw new CommandException("Material no encontrado: " + id);
            if (m.get("estado").equals("RECHAZADO")) throw new CommandException("El material ya esta RECHAZADO.");
            String antes = Jdbc.json(c, "materiales", id);
            Jdbc.exec(c, "UPDATE materiales SET estado = 'RECHAZADO', validado_por = ?, validado_at = now(), observaciones = ? WHERE id = ?",
                    actor.id(), motivo, id);
            auditoria.registrar(c, actor.id(), "MATERIAL_RECHAZAR", "materiales", id, antes, Jdbc.json(c, "materiales", id), Args.cmdText("RECMAT", p));
            long enPublicadas = Jdbc.count(c, "SELECT count(*) FROM parrilla_bloques b JOIN parrillas pa ON pa.id = b.parrilla_id "
                    + "WHERE b.material_id = ? AND pa.estado = 'PUBLICADA'", id);
            return "OK: material " + id + " RECHAZADO" + (enPublicadas > 0
                    ? "\nALERTA: esta en " + enPublicadas + " bloque(s) de parrillas PUBLICADAS; reemplacelo con UPDBLO." : "");
        });
    }

    String estadoEmision(Usuario actor, List<String> p) {
        Args.exact(p, 1, "ESTEMI[parrilla_id]");
        int id = InputValidator.validateId(p.get(0), "parrilla_id");
        return Jdbc.tx(c -> {
            Map<String, Object> pa = Jdbc.one(c, "SELECT * FROM parrillas WHERE id = ?", id);
            if (pa == null) throw new CommandException("Parrilla no encontrada: " + id);
            boolean publicada = "PUBLICADA".equals(pa.get("estado"));
            List<Map<String, Object>> bloques = Jdbc.query(c,
                    "SELECT b.id, b.hora_inicio, b.hora_fin, b.tipo, pr.nombre AS programa, m.id AS material_id, m.estado AS mat_estado, "
                  + "(SELECT count(*) FROM pauta_spots s JOIN materiales ms ON ms.id = s.material_id "
                  + "   WHERE s.parrilla_bloque_id = b.id AND ms.estado <> 'VALIDADO') AS spots_pend "
                  + "FROM parrilla_bloques b JOIN programas pr ON pr.id = b.programa_id LEFT JOIN materiales m ON m.id = b.material_id "
                  + "WHERE b.parrilla_id = ? ORDER BY b.hora_inicio", id);
            int listos = 0;
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map<String, Object> b : bloques) {
                String st;
                if ("pausa_comercial".equals(b.get("tipo"))) {
                    st = ((Number) b.get("spots_pend")).intValue() > 0 ? "ALERTA" : "LISTO";
                } else if (b.get("material_id") == null) {
                    st = "SIN_MATERIAL";
                } else {
                    st = switch ((String) b.get("mat_estado")) {
                        case "VALIDADO" -> "LISTO";
                        case "PENDIENTE" -> "PENDIENTE";
                        default -> publicada ? "ALERTA" : "RECHAZADO";
                    };
                }
                if (st.equals("LISTO")) listos++;
                Map<String, Object> row = new LinkedHashMap<>(b);
                row.put("estado", st);
                rows.add(row);
            }
            return "Estado de emision de la parrilla " + id + " (" + pa.get("fecha") + ", " + pa.get("estado") + "):\n"
                    + TextTable.render(rows, "id", "hora_inicio", "hora_fin", "tipo", "programa", "material_id", "estado")
                    + "\nResumen: " + listos + " de " + rows.size() + " bloques listos, " + (rows.size() - listos) + " con problema.";
        });
    }
}
