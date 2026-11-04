package canal11.reporte;

import canal11.db.Jdbc;
import canal11.emision.AuditoriaService;
import canal11.emision.EmisionService;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** CU8 - Reportes y estadisticas. Consultas agregadas en SQL; la respuesta es texto plano por correo. */
public class ReporteService {

    static final int MAX_DIAS = 366;

    private final AuditoriaService auditoria;

    public ReporteService(AuditoriaService auditoria) {
        this.auditoria = auditoria;
    }

    public void registerAll(CommandRouter r) {
        r.register(new CommandSpec("REPPRO", "REPPRO[\"desde\",\"hasta\"]", "Horas programadas por tipo de programa y por dia", CommandSpec.ADMIN_PROG, this::programacion));
        r.register(new CommandSpec("REPCON", "REPCON[\"desde\",\"hasta\"]", "Materiales registrados por estado y tipo", CommandSpec.ADMIN_ENC, this::contenidos));
        r.register(new CommandSpec("REPPAU", "REPPAU[\"desde\",\"hasta\"(,cliente_id)]", "Spots programados/emitidos y monto por cliente", CommandSpec.ADMIN_PROG, this::pauta));
        r.register(new CommandSpec("REPCUM", "REPCUM[\"desde\",\"hasta\"]", "Cumplimiento programado vs emitido", CommandSpec.ADMIN_PROG, this::cumplimiento));
        r.register(new CommandSpec("REPAUD", "REPAUD[\"desde\",\"hasta\"]", "Actividad por usuario", CommandSpec.ADMIN, this::actividad));
        r.register(new CommandSpec("RESDIA", "RESDIA[\"*\"]", "Resumen del dia", CommandSpec.TODOS, this::resumenDia));
    }

    private record Rango(LocalDate desde, LocalDate hasta) {}

    private static Rango rango(List<String> p, int max, String usage) {
        Args.count(p, 2, max, usage);
        LocalDate d = InputValidator.validateDate(p.get(0), "desde");
        LocalDate h = InputValidator.validateDate(p.get(1), "hasta");
        if (h.isBefore(d)) throw new CommandException("hasta no puede ser anterior a desde");
        if (ChronoUnit.DAYS.between(d, h) > MAX_DIAS) throw new CommandException("El rango maximo es de " + MAX_DIAS + " dias.");
        return new Rango(d, h);
    }

    private static String cab(String titulo, Rango r) {
        return titulo + " (" + r.desde + " a " + r.hasta + ")\n";
    }

    private void auditar(Connection c, Usuario u, String cmd, List<String> p) throws SQLException {
        auditoria.registrar(c, u.id(), "REPORTE", null, null, null, null, Args.cmdText(cmd, p));
    }

    // ------------------------------------------------------------------ reportes

    String programacion(Usuario u, List<String> p) {
        Rango r = rango(p, 2, "REPPRO[\"desde\",\"hasta\"]");
        return Jdbc.tx(c -> {
            List<Map<String, Object>> porTipo = Jdbc.query(c,
                    "SELECT pr.tipo, count(*) AS bloques, round(sum(EXTRACT(EPOCH FROM (b.hora_fin - b.hora_inicio))) / 3600.0, 2) AS horas "
                  + "FROM parrilla_bloques b JOIN parrillas pa ON pa.id = b.parrilla_id JOIN programas pr ON pr.id = b.programa_id "
                  + "WHERE pa.fecha BETWEEN ? AND ? GROUP BY pr.tipo ORDER BY horas DESC", r.desde, r.hasta);
            List<Map<String, Object>> porDia = Jdbc.query(c,
                    "SELECT pa.fecha, pa.estado, count(b.id) AS bloques, COALESCE(round(sum(EXTRACT(EPOCH FROM (b.hora_fin - b.hora_inicio))) / 3600.0, 2), 0) AS horas "
                  + "FROM parrillas pa LEFT JOIN parrilla_bloques b ON b.parrilla_id = pa.id WHERE pa.fecha BETWEEN ? AND ? "
                  + "GROUP BY pa.id, pa.fecha, pa.estado ORDER BY pa.fecha", r.desde, r.hasta);
            auditar(c, u, "REPPRO", p);
            if (porDia.isEmpty()) return cab("Reporte de programacion", r) + "(sin parrillas en el rango)";
            return cab("Reporte de programacion", r) + "Horas por tipo de programa:\n" + TextTable.render(porTipo, "tipo", "bloques", "horas")
                    + "\n\nPor dia:\n" + TextTable.render(porDia, "fecha", "estado", "bloques", "horas");
        });
    }

    String contenidos(Usuario u, List<String> p) {
        Rango r = rango(p, 2, "REPCON[\"desde\",\"hasta\"]");
        return Jdbc.tx(c -> {
            var desde = r.desde.atStartOfDay();
            var hasta = r.hasta.plusDays(1).atStartOfDay();
            List<Map<String, Object>> resumen = Jdbc.query(c,
                    "SELECT estado, tipo, count(*) AS cantidad, round(sum(duracion_seg) / 60.0, 1) AS minutos FROM materiales "
                  + "WHERE activo AND registrado_at >= ? AND registrado_at < ? GROUP BY estado, tipo ORDER BY estado, tipo", desde, hasta);
            List<Map<String, Object>> pend = Jdbc.query(c,
                    "SELECT id, titulo, tipo, duracion_seg, registrado_at FROM materiales WHERE activo AND estado = 'PENDIENTE' "
                  + "AND registrado_at >= ? AND registrado_at < ? ORDER BY registrado_at LIMIT 50", desde, hasta);
            auditar(c, u, "REPCON", p);
            if (resumen.isEmpty()) return cab("Reporte de contenidos", r) + "(sin materiales registrados en el rango)";
            return cab("Reporte de contenidos", r) + TextTable.render(resumen, "estado", "tipo", "cantidad", "minutos")
                    + "\n\nPendientes de validacion (" + pend.size() + "):\n" + TextTable.render(pend, "id", "titulo", "tipo", "duracion_seg", "registrado_at");
        });
    }

    String pauta(Usuario u, List<String> p) {
        Rango r = rango(p, 3, "REPPAU[\"desde\",\"hasta\"(,cliente_id)]");
        Integer clienteId = p.size() == 3 ? InputValidator.validateId(p.get(2), "cliente_id") : null;
        return Jdbc.tx(c -> {
            List<Object> params = new ArrayList<>(List.of(r.desde, r.hasta));
            String filtro = "";
            if (clienteId != null) {
                if (Jdbc.count(c, "SELECT 1 FROM clientes WHERE id = ?", clienteId) == 0) throw new CommandException("Cliente no encontrado: " + clienteId);
                filtro = " AND cl.id = ?";
                params.add(clienteId);
            }
            List<Map<String, Object>> rows = Jdbc.query(c,
                    "SELECT cl.razon_social AS cliente, count(*) AS spots, "
                  + "count(*) FILTER (WHERE s.estado = 'PROGRAMADO') AS programados, "
                  + "count(*) FILTER (WHERE s.estado = 'EMITIDO') AS emitidos, "
                  + "count(*) FILTER (WHERE s.estado = 'NO_EMITIDO') AS no_emitidos, "
                  + "COALESCE(sum(pt.tarifa_spot) FILTER (WHERE s.estado = 'EMITIDO'), 0) AS monto "
                  + "FROM pauta_spots s JOIN pautas pt ON pt.id = s.pauta_id JOIN clientes cl ON cl.id = pt.cliente_id "
                  + "JOIN parrilla_bloques b ON b.id = s.parrilla_bloque_id JOIN parrillas pa ON pa.id = b.parrilla_id "
                  + "WHERE pa.fecha BETWEEN ? AND ?" + filtro + " GROUP BY cl.id, cl.razon_social ORDER BY cl.razon_social", params.toArray());
            auditar(c, u, "REPPAU", p);
            if (rows.isEmpty()) return cab("Reporte de pauta publicitaria", r) + "(sin spots en el rango)";
            return cab("Reporte de pauta publicitaria", r) + TextTable.render(rows, "cliente", "spots", "programados", "emitidos", "no_emitidos", "monto")
                    + "\nMonto = spots EMITIDOS x tarifa de su pauta.";
        });
    }

    String cumplimiento(Usuario u, List<String> p) {
        Rango r = rango(p, 2, "REPCUM[\"desde\",\"hasta\"]");
        return Jdbc.tx(c -> {
            List<Map<String, Object>> parrillas = Jdbc.query(c,
                    "SELECT id, fecha, estado FROM parrillas WHERE fecha BETWEEN ? AND ? AND estado IN ('PUBLICADA','CERRADA') ORDER BY fecha", r.desde, r.hasta);
            List<Map<String, Object>> rows = new ArrayList<>();
            long tot = 0, emi = 0;
            for (Map<String, Object> pa : parrillas) {
                List<Map<String, Object>> filas = EmisionService.filasConciliacion(c, ((Number) pa.get("id")).intValue());
                long t = filas.size(), e = filas.stream().filter(f -> "EMITIDO".equals(f.get("estado"))).count();
                tot += t;
                emi += e;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("fecha", pa.get("fecha"));
                row.put("parrilla", pa.get("id"));
                row.put("estado", pa.get("estado"));
                row.put("programados", t);
                row.put("emitidos", e);
                row.put("cumplimiento", t == 0 ? "-" : String.format("%.1f%%", e * 100.0 / t));
                rows.add(row);
            }
            auditar(c, u, "REPCUM", p);
            if (rows.isEmpty()) return cab("Reporte de cumplimiento", r) + "(sin parrillas publicadas o cerradas en el rango)";
            return cab("Reporte de cumplimiento", r) + TextTable.render(rows, "fecha", "parrilla", "estado", "programados", "emitidos", "cumplimiento")
                    + String.format("%nTotal: %d programados, %d emitidos, cumplimiento %.1f%%", tot, emi, tot == 0 ? 0.0 : emi * 100.0 / tot);
        });
    }

    String actividad(Usuario u, List<String> p) {
        Rango r = rango(p, 2, "REPAUD[\"desde\",\"hasta\"]");
        return Jdbc.tx(c -> {
            var desde = r.desde.atStartOfDay();
            var hasta = r.hasta.plusDays(1).atStartOfDay();
            List<Map<String, Object>> rows = Jdbc.query(c,
                    "SELECT COALESCE(x.email, '(sistema)') AS usuario, count(*) AS acciones, min(a.created_at) AS primera, max(a.created_at) AS ultima "
                  + "FROM auditoria_logs a LEFT JOIN users x ON x.id = a.user_id WHERE a.created_at >= ? AND a.created_at < ? "
                  + "GROUP BY x.email ORDER BY acciones DESC", desde, hasta);
            List<Map<String, Object>> porAccion = Jdbc.query(c,
                    "SELECT accion, count(*) AS cantidad FROM auditoria_logs WHERE created_at >= ? AND created_at < ? GROUP BY accion ORDER BY cantidad DESC LIMIT 15", desde, hasta);
            auditar(c, u, "REPAUD", p);
            if (rows.isEmpty()) return cab("Reporte de actividad", r) + "(sin actividad en el rango)";
            return cab("Reporte de actividad", r) + TextTable.render(rows, "usuario", "acciones", "primera", "ultima")
                    + "\n\nAcciones mas frecuentes:\n" + TextTable.render(porAccion, "accion", "cantidad");
        });
    }

    String resumenDia(Usuario u, List<String> p) {
        Args.exact(p, 1, "RESDIA[\"*\"]");
        return Jdbc.tx(c -> {
            LocalDate hoy = LocalDate.now();
            StringBuilder sb = new StringBuilder("Resumen del dia " + hoy + ":\n");
            Map<String, Object> pa = Jdbc.one(c, "SELECT pa.id, pa.estado, pa.version, count(b.id) AS bloques, "
                    + "COALESCE(round(sum(EXTRACT(EPOCH FROM (b.hora_fin - b.hora_inicio))) / 3600.0, 2), 0) AS horas "
                    + "FROM parrillas pa LEFT JOIN parrilla_bloques b ON b.parrilla_id = pa.id WHERE pa.fecha = ? GROUP BY pa.id", hoy);
            if (pa == null) {
                sb.append("- Parrilla de hoy: NO CREADA (INSPAR[\"").append(hoy).append("\"])\n");
            } else {
                sb.append("- Parrilla de hoy: ID ").append(pa.get("id")).append(", ").append(pa.get("estado")).append(", version ").append(pa.get("version"))
                  .append(", ").append(pa.get("bloques")).append(" bloques, ").append(pa.get("horas")).append(" h programadas\n");
                long spots = Jdbc.count(c, "SELECT count(*) FROM pauta_spots s JOIN parrilla_bloques b ON b.id = s.parrilla_bloque_id WHERE b.parrilla_id = ?", pa.get("id"));
                sb.append("- Spots de hoy: ").append(spots).append("\n");
            }
            sb.append("- Materiales pendientes de validacion: ").append(Jdbc.count(c, "SELECT count(*) FROM materiales WHERE activo AND estado = 'PENDIENTE'")).append("\n");
            long alertas = Jdbc.count(c, "SELECT count(*) FROM parrilla_bloques b JOIN parrillas pa ON pa.id = b.parrilla_id JOIN materiales m ON m.id = b.material_id "
                    + "WHERE pa.estado = 'PUBLICADA' AND pa.fecha >= ? AND m.estado = 'RECHAZADO'", hoy);
            sb.append("- ALERTAS (material rechazado en parrillas publicadas): ").append(alertas).append("\n");
            sb.append("- Parrillas publicadas desde hoy: ").append(Jdbc.count(c, "SELECT count(*) FROM parrillas WHERE estado = 'PUBLICADA' AND fecha >= ?", hoy));
            return sb.toString();
        });
    }
}
