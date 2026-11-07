package canal11.reporte.repository;

import canal11.db.Jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Consultas de solo lectura para los reportes del CU8. */
public class ReporteRepository {

    public List<Map<String, Object>> horasPorTipo(Connection c, LocalDate desde, LocalDate hasta) throws SQLException {
        return Jdbc.query(c,
                "SELECT pr.tipo, count(*) AS bloques, round(sum(EXTRACT(EPOCH FROM (b.hora_fin - b.hora_inicio))) / 3600.0, 2) AS horas "
              + "FROM parrilla_bloques b JOIN parrillas pa ON pa.id = b.parrilla_id JOIN programas pr ON pr.id = b.programa_id "
              + "WHERE pa.fecha BETWEEN ? AND ? GROUP BY pr.tipo ORDER BY horas DESC", desde, hasta);
    }

    public List<Map<String, Object>> horasPorDia(Connection c, LocalDate desde, LocalDate hasta) throws SQLException {
        return Jdbc.query(c,
                "SELECT pa.fecha, pa.estado, count(b.id) AS bloques, COALESCE(round(sum(EXTRACT(EPOCH FROM (b.hora_fin - b.hora_inicio))) / 3600.0, 2), 0) AS horas "
              + "FROM parrillas pa LEFT JOIN parrilla_bloques b ON b.parrilla_id = pa.id WHERE pa.fecha BETWEEN ? AND ? "
              + "GROUP BY pa.id, pa.fecha, pa.estado ORDER BY pa.fecha", desde, hasta);
    }

    public List<Map<String, Object>> materialesPorEstadoTipo(Connection c, LocalDateTime desde, LocalDateTime hasta) throws SQLException {
        return Jdbc.query(c,
                "SELECT estado, tipo, count(*) AS cantidad, round(sum(duracion_seg) / 60.0, 1) AS minutos FROM materiales "
              + "WHERE activo AND registrado_at >= ? AND registrado_at < ? GROUP BY estado, tipo ORDER BY estado, tipo", desde, hasta);
    }

    public List<Map<String, Object>> materialesPendientes(Connection c, LocalDateTime desde, LocalDateTime hasta) throws SQLException {
        return Jdbc.query(c,
                "SELECT id, titulo, tipo, duracion_seg, registrado_at FROM materiales WHERE activo AND estado = 'PENDIENTE' "
              + "AND registrado_at >= ? AND registrado_at < ? ORDER BY registrado_at LIMIT 50", desde, hasta);
    }

    public boolean clienteExiste(Connection c, int id) throws SQLException {
        return Jdbc.count(c, "SELECT 1 FROM clientes WHERE id = ?", id) > 0;
    }

    /** clienteId opcional: si es null se reporta a todos los clientes. */
    public List<Map<String, Object>> pautaPorCliente(Connection c, LocalDate desde, LocalDate hasta, Integer clienteId) throws SQLException {
        List<Object> params = new ArrayList<>(List.of(desde, hasta));
        String filtro = "";
        if (clienteId != null) {
            filtro = " AND cl.id = ?";
            params.add(clienteId);
        }
        return Jdbc.query(c,
                "SELECT cl.razon_social AS cliente, count(*) AS spots, "
              + "count(*) FILTER (WHERE s.estado = 'PROGRAMADO') AS programados, "
              + "count(*) FILTER (WHERE s.estado = 'EMITIDO') AS emitidos, "
              + "count(*) FILTER (WHERE s.estado = 'NO_EMITIDO') AS no_emitidos, "
              + "COALESCE(sum(pt.tarifa_spot) FILTER (WHERE s.estado = 'EMITIDO'), 0) AS monto "
              + "FROM pauta_spots s JOIN pautas pt ON pt.id = s.pauta_id JOIN clientes cl ON cl.id = pt.cliente_id "
              + "JOIN parrilla_bloques b ON b.id = s.parrilla_bloque_id JOIN parrillas pa ON pa.id = b.parrilla_id "
              + "WHERE pa.fecha BETWEEN ? AND ?" + filtro + " GROUP BY cl.id, cl.razon_social ORDER BY cl.razon_social", params.toArray());
    }

    public List<Map<String, Object>> parrillasPublicadasOCerradas(Connection c, LocalDate desde, LocalDate hasta) throws SQLException {
        return Jdbc.query(c,
                "SELECT id, fecha, estado FROM parrillas WHERE fecha BETWEEN ? AND ? AND estado IN ('PUBLICADA','CERRADA') ORDER BY fecha", desde, hasta);
    }

    public List<Map<String, Object>> actividadPorUsuario(Connection c, LocalDateTime desde, LocalDateTime hasta) throws SQLException {
        return Jdbc.query(c,
                "SELECT COALESCE(x.email, '(sistema)') AS usuario, count(*) AS acciones, min(a.created_at) AS primera, max(a.created_at) AS ultima "
              + "FROM auditoria_logs a LEFT JOIN users x ON x.id = a.user_id WHERE a.created_at >= ? AND a.created_at < ? "
              + "GROUP BY x.email ORDER BY acciones DESC", desde, hasta);
    }

    public List<Map<String, Object>> accionesFrecuentes(Connection c, LocalDateTime desde, LocalDateTime hasta) throws SQLException {
        return Jdbc.query(c,
                "SELECT accion, count(*) AS cantidad FROM auditoria_logs WHERE created_at >= ? AND created_at < ? GROUP BY accion ORDER BY cantidad DESC LIMIT 15", desde, hasta);
    }

    // ------------------------------------------------------------------ resumen del dia

    public Map<String, Object> parrillaDelDia(Connection c, LocalDate fecha) throws SQLException {
        return Jdbc.one(c, "SELECT pa.id, pa.estado, pa.version, count(b.id) AS bloques, "
                + "COALESCE(round(sum(EXTRACT(EPOCH FROM (b.hora_fin - b.hora_inicio))) / 3600.0, 2), 0) AS horas "
                + "FROM parrillas pa LEFT JOIN parrilla_bloques b ON b.parrilla_id = pa.id WHERE pa.fecha = ? GROUP BY pa.id", fecha);
    }

    public long contarSpotsDeParrilla(Connection c, Object parrillaId) throws SQLException {
        return Jdbc.count(c, "SELECT count(*) FROM pauta_spots s JOIN parrilla_bloques b ON b.id = s.parrilla_bloque_id WHERE b.parrilla_id = ?", parrillaId);
    }

    public long contarMaterialesPendientes(Connection c) throws SQLException {
        return Jdbc.count(c, "SELECT count(*) FROM materiales WHERE activo AND estado = 'PENDIENTE'");
    }

    public long contarAlertasMaterialRechazado(Connection c, LocalDate desde) throws SQLException {
        return Jdbc.count(c, "SELECT count(*) FROM parrilla_bloques b JOIN parrillas pa ON pa.id = b.parrilla_id JOIN materiales m ON m.id = b.material_id "
                + "WHERE pa.estado = 'PUBLICADA' AND pa.fecha >= ? AND m.estado = 'RECHAZADO'", desde);
    }

    public long contarParrillasPublicadasDesde(Connection c, LocalDate desde) throws SQLException {
        return Jdbc.count(c, "SELECT count(*) FROM parrillas WHERE estado = 'PUBLICADA' AND fecha >= ?", desde);
    }
}
