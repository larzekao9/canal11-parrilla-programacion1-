package canal11.gestion.repository;

import canal11.db.Jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/** SQL del CU5: validar/rechazar materiales y estado de emision de una parrilla. */
public class ValidacionRepository {

    public Map<String, Object> materialActivoForUpdate(Connection c, int id) throws SQLException {
        return Jdbc.one(c, "SELECT * FROM materiales WHERE id = ? AND activo FOR UPDATE", id);
    }

    /** Bloques vigentes (borrador/publicada, de hoy en adelante) que usan el material, con su duracion en segundos. */
    public List<Map<String, Object>> bloquesVigentesDeMaterial(Connection c, int materialId) throws SQLException {
        return Jdbc.query(c,
                "SELECT pa.fecha, b.hora_inicio, EXTRACT(EPOCH FROM (b.hora_fin - b.hora_inicio))::int AS seg "
              + "FROM parrilla_bloques b JOIN parrillas pa ON pa.id = b.parrilla_id "
              + "WHERE b.material_id = ? AND pa.estado IN ('BORRADOR','PUBLICADA') AND pa.fecha >= CURRENT_DATE", materialId);
    }

    public void marcarValidado(Connection c, int id, int validadoPor, String obs) throws SQLException {
        Jdbc.exec(c, "UPDATE materiales SET estado = 'VALIDADO', validado_por = ?, validado_at = now(), observaciones = COALESCE(?, observaciones) WHERE id = ?",
                validadoPor, obs, id);
    }

    public void marcarRechazado(Connection c, int id, int validadoPor, String motivo) throws SQLException {
        Jdbc.exec(c, "UPDATE materiales SET estado = 'RECHAZADO', validado_por = ?, validado_at = now(), observaciones = ? WHERE id = ?",
                validadoPor, motivo, id);
    }

    public long bloquesEnPublicadas(Connection c, int materialId) throws SQLException {
        return Jdbc.count(c, "SELECT count(*) FROM parrilla_bloques b JOIN parrillas pa ON pa.id = b.parrilla_id "
                + "WHERE b.material_id = ? AND pa.estado = 'PUBLICADA'", materialId);
    }

    public Map<String, Object> parrillaById(Connection c, int id) throws SQLException {
        return Jdbc.one(c, "SELECT * FROM parrillas WHERE id = ?", id);
    }

    public List<Map<String, Object>> bloquesConEstadoMaterial(Connection c, int parrillaId) throws SQLException {
        return Jdbc.query(c,
                "SELECT b.id, b.hora_inicio, b.hora_fin, b.tipo, pr.nombre AS programa, m.id AS material_id, m.estado AS mat_estado, "
              + "(SELECT count(*) FROM pauta_spots s JOIN materiales ms ON ms.id = s.material_id "
              + "   WHERE s.parrilla_bloque_id = b.id AND ms.estado <> 'VALIDADO') AS spots_pend "
              + "FROM parrilla_bloques b JOIN programas pr ON pr.id = b.programa_id LEFT JOIN materiales m ON m.id = b.material_id "
              + "WHERE b.parrilla_id = ? ORDER BY b.hora_inicio", parrillaId);
    }

    public String snapshotMaterial(Connection c, int id) throws SQLException {
        return Jdbc.json(c, "materiales", id);
    }
}
