package canal11.emision;

import canal11.db.DbConfig;
import canal11.db.Jdbc;
import canal11.mail.CommandRouter;
import canal11.mail.CommandSpec;
import canal11.model.Usuario;
import canal11.util.Args;
import canal11.util.CommandException;
import canal11.util.InputValidator;
import canal11.util.TextTable;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CU7 - Conciliar emision real. Registra lo que realmente salio al aire (bloques y spots) y lo compara con lo programado.
 * desviacion_seg = inicio real - inicio programado (positivo = retraso). Los cambios quedan auditados (AuditoriaService).
 */
public class EmisionService {

    public static final List<String> ESTADOS = List.of("EMITIDO", "PARCIAL", "NO_EMITIDO", "REEMPLAZADO");
    static final Set<String> CAMPOS = Set.of("inicio_real", "fin_real", "estado", "observacion");

    private final AuditoriaService auditoria;

    public EmisionService(AuditoriaService auditoria) {
        this.auditoria = auditoria;
    }

    public void registerAll(CommandRouter r) {
        var W = CommandSpec.ADMIN_PROG;
        r.register(new CommandSpec("INSEMI", "INSEMI[bloque_id|\"spot:ID\",\"inicio\",\"fin|-\",\"estado\"(,\"observacion\")]",
                "Registra una emision real (fechas: YYYY-MM-DD HH:MM:SS)", W, this::insert));
        r.register(new CommandSpec("UPDEMI", "UPDEMI[id,\"campo=valor\",...]", "Corrige una emision (inicio_real, fin_real, estado, observacion)", W, this::update));
        r.register(new CommandSpec("LISEMI", "LISEMI[parrilla_id]", "Emisiones registradas de una parrilla", W, this::list));
        r.register(new CommandSpec("CONPAR", "CONPAR[parrilla_id]", "Conciliacion: programado vs emitido", W, this::conciliar));
    }

    static int toleranciaSeg() {
        return Integer.parseInt(DbConfig.get("emision.tolerancia.seg", "30"));
    }

    // ------------------------------------------------------------------ registro

    String insert(Usuario actor, List<String> p) {
        String usage = "INSEMI[bloque_id|\"spot:ID\",\"inicio\",\"fin|-\",\"estado\"(,\"observacion\")]";
        Args.count(p, 4, 5, usage);
        String target = p.get(0).trim().toLowerCase();
        boolean esSpot = target.startsWith("spot:");
        int targetId = InputValidator.validateId(esSpot ? target.substring(5) : target, esSpot ? "spot_id" : "bloque_id");
        LocalDateTime inicio = dateTime(p.get(1), "inicio");
        LocalDateTime fin = p.get(2).isBlank() || p.get(2).trim().equals("-") ? null : dateTime(p.get(2), "fin");
        String estado = InputValidator.validateEnum(p.get(3), "estado", ESTADOS);
        String obs = p.size() == 5 && !p.get(4).isBlank() ? p.get(4).trim() : null;
        if (fin != null && !fin.isAfter(inicio)) throw new CommandException("fin debe ser posterior a inicio");

        return Jdbc.tx(c -> {
            LocalDateTime programado;
            Integer bloqueId = null, spotId = null;
            String estadoParrilla;
            if (esSpot) {
                Map<String, Object> s = Jdbc.one(c, "SELECT s.id, s.hora_programada, pa.fecha, pa.estado AS pestado FROM pauta_spots s "
                        + "JOIN parrilla_bloques b ON b.id = s.parrilla_bloque_id JOIN parrillas pa ON pa.id = b.parrilla_id WHERE s.id = ? FOR UPDATE OF s", targetId);
                if (s == null) throw new CommandException("Spot no encontrado: " + targetId);
                spotId = targetId;
                programado = ((LocalDate) s.get("fecha")).atTime((LocalTime) s.get("hora_programada"));
                estadoParrilla = (String) s.get("pestado");
                if (Jdbc.count(c, "SELECT 1 FROM emisiones_reales WHERE pauta_spot_id = ?", spotId) > 0)
                    throw new CommandException("El spot ya tiene una emision registrada; use UPDEMI para corregirla.");
            } else {
                Map<String, Object> b = Jdbc.one(c, "SELECT b.id, b.hora_inicio, pa.fecha, pa.estado AS pestado FROM parrilla_bloques b "
                        + "JOIN parrillas pa ON pa.id = b.parrilla_id WHERE b.id = ? FOR UPDATE OF b", targetId);
                if (b == null) throw new CommandException("Bloque no encontrado: " + targetId);
                bloqueId = targetId;
                programado = ((LocalDate) b.get("fecha")).atTime((LocalTime) b.get("hora_inicio"));
                estadoParrilla = (String) b.get("pestado");
                if (Jdbc.count(c, "SELECT 1 FROM emisiones_reales WHERE parrilla_bloque_id = ?", bloqueId) > 0)
                    throw new CommandException("El bloque ya tiene una emision registrada; use UPDEMI para corregirla.");
            }
            if ("BORRADOR".equals(estadoParrilla)) throw new CommandException("La parrilla esta en BORRADOR; solo se registran emisiones de parrillas publicadas.");
            int desv = (int) Duration.between(programado, inicio).getSeconds();
            int id = Jdbc.insert(c, "INSERT INTO emisiones_reales (parrilla_bloque_id, pauta_spot_id, inicio_real, fin_real, estado, desviacion_seg, observacion, registrado_por) "
                    + "VALUES (?,?,?,?,?,?,?,?) RETURNING id", bloqueId, spotId, inicio, fin, estado, desv, obs, actor.id());
            if (spotId != null) actualizarEstadoSpot(c, spotId, estado);
            auditoria.registrar(c, actor.id(), "EMISION_REGISTRAR", "emisiones_reales", id, null, Jdbc.json(c, "emisiones_reales", id), Args.cmdText("INSEMI", p));
            return "OK: emision " + id + " registrada (" + estado + ", desviacion " + desv + " s" + (Math.abs(desv) <= toleranciaSeg() ? ", a tiempo)" : ")");
        });
    }

    String update(Usuario actor, List<String> p) {
        String usage = "UPDEMI[id,\"campo=valor\",...]";
        if (p.isEmpty()) throw new CommandException("Uso: " + usage);
        int id = InputValidator.validateId(p.get(0), "id");
        Map<String, String> f = Args.fields(p, 1, CAMPOS, usage);
        return Jdbc.tx(c -> {
            Map<String, Object> cur = Jdbc.one(c, "SELECT e.*, COALESCE(pa1.fecha, pa2.fecha) AS fecha, COALESCE(b1.hora_inicio, s.hora_programada) AS hora_prog "
                    + "FROM emisiones_reales e LEFT JOIN parrilla_bloques b1 ON b1.id = e.parrilla_bloque_id LEFT JOIN parrillas pa1 ON pa1.id = b1.parrilla_id "
                    + "LEFT JOIN pauta_spots s ON s.id = e.pauta_spot_id LEFT JOIN parrilla_bloques b2 ON b2.id = s.parrilla_bloque_id "
                    + "LEFT JOIN parrillas pa2 ON pa2.id = b2.parrilla_id WHERE e.id = ? FOR UPDATE OF e", id);
            if (cur == null) throw new CommandException("Emision no encontrada: " + id);
            String antes = Jdbc.json(c, "emisiones_reales", id);
            LocalDateTime inicio = (LocalDateTime) cur.get("inicio_real"), fin = (LocalDateTime) cur.get("fin_real");
            String estado = (String) cur.get("estado"), obs = (String) cur.get("observacion");
            for (Map.Entry<String, String> e : f.entrySet()) {
                String v = e.getValue();
                switch (e.getKey()) {
                    case "inicio_real" -> inicio = dateTime(v, "inicio_real");
                    case "fin_real" -> fin = v.isBlank() || v.equals("-") ? null : dateTime(v, "fin_real");
                    case "estado" -> estado = InputValidator.validateEnum(v, "estado", ESTADOS);
                    default -> obs = v.isBlank() ? null : v;
                }
            }
            if (fin != null && !fin.isAfter(inicio)) throw new CommandException("fin debe ser posterior a inicio");
            LocalDateTime programado = ((LocalDate) cur.get("fecha")).atTime((LocalTime) cur.get("hora_prog"));
            int desv = (int) Duration.between(programado, inicio).getSeconds();
            Jdbc.exec(c, "UPDATE emisiones_reales SET inicio_real = ?, fin_real = ?, estado = ?, desviacion_seg = ?, observacion = ? WHERE id = ?",
                    inicio, fin, estado, desv, obs, id);
            if (cur.get("pauta_spot_id") != null) actualizarEstadoSpot(c, ((Number) cur.get("pauta_spot_id")).intValue(), estado);
            auditoria.registrar(c, actor.id(), "EMISION_CORREGIR", "emisiones_reales", id, antes, Jdbc.json(c, "emisiones_reales", id), Args.cmdText("UPDEMI", p));
            return "OK: emision " + id + " actualizada (" + estado + ", desviacion " + desv + " s)";
        });
    }

    /** EMITIDO o PARCIAL cuentan como emitido; NO_EMITIDO y REEMPLAZADO como no emitido. */
    private static void actualizarEstadoSpot(Connection c, int spotId, String estadoEmision) throws SQLException {
        String st = estadoEmision.equals("EMITIDO") || estadoEmision.equals("PARCIAL") ? "EMITIDO" : "NO_EMITIDO";
        Jdbc.exec(c, "UPDATE pauta_spots SET estado = ? WHERE id = ?", st, spotId);
    }

    // ------------------------------------------------------------------ consultas

    String list(Usuario actor, List<String> p) {
        Args.exact(p, 1, "LISEMI[parrilla_id]");
        int id = InputValidator.validateId(p.get(0), "parrilla_id");
        return Jdbc.tx(c -> {
            exigirParrilla(c, id);
            List<Map<String, Object>> rows = Jdbc.query(c,
                    "SELECT e.id, CASE WHEN e.pauta_spot_id IS NULL THEN 'bloque ' || e.parrilla_bloque_id ELSE 'spot ' || e.pauta_spot_id END AS objetivo, "
                  + "e.inicio_real, e.fin_real, e.estado, e.desviacion_seg AS desv, e.observacion "
                  + "FROM emisiones_reales e LEFT JOIN parrilla_bloques b1 ON b1.id = e.parrilla_bloque_id "
                  + "LEFT JOIN pauta_spots s ON s.id = e.pauta_spot_id LEFT JOIN parrilla_bloques b2 ON b2.id = s.parrilla_bloque_id "
                  + "WHERE COALESCE(b1.parrilla_id, b2.parrilla_id) = ? ORDER BY e.inicio_real", id);
            return "Emisiones de la parrilla " + id + " (" + rows.size() + "):\n"
                    + TextTable.render(rows, "id", "objetivo", "inicio_real", "fin_real", "estado", "desv", "observacion");
        });
    }

    String conciliar(Usuario actor, List<String> p) {
        Args.exact(p, 1, "CONPAR[parrilla_id]");
        int id = InputValidator.validateId(p.get(0), "parrilla_id");
        return Jdbc.tx(c -> {
            Map<String, Object> pa = exigirParrilla(c, id);
            List<Map<String, Object>> rows = filasConciliacion(c, id);
            int total = rows.size(), emitidos = 0, parciales = 0, noEmitidos = 0, reemplazados = 0, aTiempo = 0;
            for (Map<String, Object> r : rows) {
                switch ((String) r.get("estado")) {
                    case "EMITIDO" -> emitidos++;
                    case "PARCIAL" -> parciales++;
                    case "REEMPLAZADO" -> reemplazados++;
                    default -> noEmitidos++;
                }
                if (r.get("desv") != null && Math.abs(((Number) r.get("desv")).intValue()) <= toleranciaSeg()
                        && (r.get("estado").equals("EMITIDO") || r.get("estado").equals("PARCIAL"))) aTiempo++;
            }
            double pct = total == 0 ? 0 : emitidos * 100.0 / total;
            return "Conciliacion de la parrilla " + id + " (" + pa.get("fecha") + ", " + pa.get("estado") + "):\n"
                    + TextTable.render(rows, "tipo", "ref", "programado", "real", "desv", "estado")
                    + String.format("%nResumen: %d elementos | EMITIDO %d | PARCIAL %d | REEMPLAZADO %d | NO_EMITIDO %d | a tiempo (+-%d s) %d%nCumplimiento: %.1f%%",
                            total, emitidos, parciales, reemplazados, noEmitidos, toleranciaSeg(), aTiempo, pct);
        });
    }

    /**
     * Elementos conciliables de una parrilla: bloques de programa/promo y spots. Sin registro => NO_EMITIDO.
     * Tambien lo usa el reporte de cumplimiento (CU8).
     */
    public static List<Map<String, Object>> filasConciliacion(Connection c, int parrillaId) throws SQLException {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> b : Jdbc.query(c,
                "SELECT b.id, b.tipo, pa.fecha, b.hora_inicio, e.inicio_real, e.estado AS est, e.desviacion_seg AS desv "
              + "FROM parrilla_bloques b JOIN parrillas pa ON pa.id = b.parrilla_id LEFT JOIN emisiones_reales e ON e.parrilla_bloque_id = b.id "
              + "WHERE b.parrilla_id = ? AND b.tipo <> 'pausa_comercial' ORDER BY b.hora_inicio", parrillaId)) {
            out.add(fila("bloque", b.get("id"), ((LocalDate) b.get("fecha")).atTime((LocalTime) b.get("hora_inicio")), b));
        }
        for (Map<String, Object> s : Jdbc.query(c,
                "SELECT s.id, pa.fecha, s.hora_programada, e.inicio_real, e.estado AS est, e.desviacion_seg AS desv "
              + "FROM pauta_spots s JOIN parrilla_bloques b ON b.id = s.parrilla_bloque_id JOIN parrillas pa ON pa.id = b.parrilla_id "
              + "LEFT JOIN emisiones_reales e ON e.pauta_spot_id = s.id WHERE b.parrilla_id = ? ORDER BY s.hora_programada", parrillaId)) {
            out.add(fila("spot", s.get("id"), ((LocalDate) s.get("fecha")).atTime((LocalTime) s.get("hora_programada")), s));
        }
        return out;
    }

    private static Map<String, Object> fila(String tipo, Object ref, LocalDateTime programado, Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tipo", tipo);
        m.put("ref", ref);
        m.put("programado", programado);
        m.put("real", r.get("inicio_real"));
        m.put("desv", r.get("desv"));
        m.put("estado", r.get("est") == null ? "NO_EMITIDO" : r.get("est"));
        return m;
    }

    private static Map<String, Object> exigirParrilla(Connection c, int id) throws SQLException {
        Map<String, Object> pa = Jdbc.one(c, "SELECT * FROM parrillas WHERE id = ?", id);
        if (pa == null) throw new CommandException("Parrilla no encontrada: " + id);
        return pa;
    }

    static LocalDateTime dateTime(String v, String field) {
        if (v == null || v.isBlank()) throw new CommandException("Falta el campo " + field);
        try {
            return LocalDateTime.parse(v.trim().replace(' ', 'T'));
        } catch (DateTimeParseException e) {
            throw new CommandException(field + " invalido, use formato YYYY-MM-DD HH:MM:SS");
        }
    }
}
