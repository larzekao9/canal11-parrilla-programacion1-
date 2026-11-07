package canal11.emision.repository;

import canal11.db.Jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** SQL de emisiones_reales y de las consultas de conciliacion (programado vs emitido). */
public class EmisionRepository {

    /** Spot con su fecha/hora programada y estado de su parrilla, bloqueado. */
    public Map<String, Object> spotForUpdate(Connection c, int spotId) throws SQLException {
        return Jdbc.one(c, "SELECT s.id, s.hora_programada, pa.fecha, pa.estado AS pestado FROM pauta_spots s "
                + "JOIN parrilla_bloques b ON b.id = s.parrilla_bloque_id JOIN parrillas pa ON pa.id = b.parrilla_id WHERE s.id = ? FOR UPDATE OF s", spotId);
    }

    public Map<String, Object> bloqueForUpdate(Connection c, int bloqueId) throws SQLException {
        return Jdbc.one(c, "SELECT b.id, b.hora_inicio, pa.fecha, pa.estado AS pestado FROM parrilla_bloques b "
                + "JOIN parrillas pa ON pa.id = b.parrilla_id WHERE b.id = ? FOR UPDATE OF b", bloqueId);
    }

    public boolean spotTieneEmision(Connection c, int spotId) throws SQLException {
        return Jdbc.count(c, "SELECT 1 FROM emisiones_reales WHERE pauta_spot_id = ?", spotId) > 0;
    }

    public boolean bloqueTieneEmision(Connection c, int bloqueId) throws SQLException {
        return Jdbc.count(c, "SELECT 1 FROM emisiones_reales WHERE parrilla_bloque_id = ?", bloqueId) > 0;
    }

    public int insert(Connection c, Integer bloqueId, Integer spotId, LocalDateTime inicio, LocalDateTime fin,
                      String estado, int desv, String obs, int registradoPor) throws SQLException {
        return Jdbc.insert(c, "INSERT INTO emisiones_reales (parrilla_bloque_id, pauta_spot_id, inicio_real, fin_real, estado, desviacion_seg, observacion, registrado_por) "
                + "VALUES (?,?,?,?,?,?,?,?) RETURNING id", bloqueId, spotId, inicio, fin, estado, desv, obs, registradoPor);
    }

    /** Emision con la fecha y hora programadas de su bloque o spot, bloqueada para corregirla. */
    public Map<String, Object> findConProgramadoForUpdate(Connection c, int id) throws SQLException {
        return Jdbc.one(c, "SELECT e.*, COALESCE(pa1.fecha, pa2.fecha) AS fecha, COALESCE(b1.hora_inicio, s.hora_programada) AS hora_prog "
                + "FROM emisiones_reales e LEFT JOIN parrilla_bloques b1 ON b1.id = e.parrilla_bloque_id LEFT JOIN parrillas pa1 ON pa1.id = b1.parrilla_id "
                + "LEFT JOIN pauta_spots s ON s.id = e.pauta_spot_id LEFT JOIN parrilla_bloques b2 ON b2.id = s.parrilla_bloque_id "
                + "LEFT JOIN parrillas pa2 ON pa2.id = b2.parrilla_id WHERE e.id = ? FOR UPDATE OF e", id);
    }

    public void update(Connection c, int id, LocalDateTime inicio, LocalDateTime fin, String estado, int desv, String obs) throws SQLException {
        Jdbc.exec(c, "UPDATE emisiones_reales SET inicio_real = ?, fin_real = ?, estado = ?, desviacion_seg = ?, observacion = ? WHERE id = ?",
                inicio, fin, estado, desv, obs, id);
    }

    public void actualizarEstadoSpot(Connection c, int spotId, String estadoSpot) throws SQLException {
        Jdbc.exec(c, "UPDATE pauta_spots SET estado = ? WHERE id = ?", estadoSpot, spotId);
    }

    public String snapshot(Connection c, int id) throws SQLException {
        return Jdbc.json(c, "emisiones_reales", id);
    }

    public Map<String, Object> parrillaById(Connection c, int id) throws SQLException {
        return Jdbc.one(c, "SELECT * FROM parrillas WHERE id = ?", id);
    }

    public List<Map<String, Object>> listByParrilla(Connection c, int parrillaId) throws SQLException {
        return Jdbc.query(c,
                "SELECT e.id, CASE WHEN e.pauta_spot_id IS NULL THEN 'bloque ' || e.parrilla_bloque_id ELSE 'spot ' || e.pauta_spot_id END AS objetivo, "
              + "e.inicio_real, e.fin_real, e.estado, e.desviacion_seg AS desv, e.observacion "
              + "FROM emisiones_reales e LEFT JOIN parrilla_bloques b1 ON b1.id = e.parrilla_bloque_id "
              + "LEFT JOIN pauta_spots s ON s.id = e.pauta_spot_id LEFT JOIN parrilla_bloques b2 ON b2.id = s.parrilla_bloque_id "
              + "WHERE COALESCE(b1.parrilla_id, b2.parrilla_id) = ? ORDER BY e.inicio_real", parrillaId);
    }

    /** Bloques de programa/promo (no pausas) con su emision real, si existe. */
    public List<Map<String, Object>> bloquesConEmision(Connection c, int parrillaId) throws SQLException {
        return Jdbc.query(c,
                "SELECT b.id, b.tipo, pa.fecha, b.hora_inicio, e.inicio_real, e.estado AS est, e.desviacion_seg AS desv "
              + "FROM parrilla_bloques b JOIN parrillas pa ON pa.id = b.parrilla_id LEFT JOIN emisiones_reales e ON e.parrilla_bloque_id = b.id "
              + "WHERE b.parrilla_id = ? AND b.tipo <> 'pausa_comercial' ORDER BY b.hora_inicio", parrillaId);
    }

    /** Spots de la parrilla con su emision real, si existe. */
    public List<Map<String, Object>> spotsConEmision(Connection c, int parrillaId) throws SQLException {
        return Jdbc.query(c,
                "SELECT s.id, pa.fecha, s.hora_programada, e.inicio_real, e.estado AS est, e.desviacion_seg AS desv "
              + "FROM pauta_spots s JOIN parrilla_bloques b ON b.id = s.parrilla_bloque_id JOIN parrillas pa ON pa.id = b.parrilla_id "
              + "LEFT JOIN emisiones_reales e ON e.pauta_spot_id = s.id WHERE b.parrilla_id = ? ORDER BY s.hora_programada", parrillaId);
    }
}
