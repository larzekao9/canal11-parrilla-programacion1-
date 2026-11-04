package canal11.emision;

import canal11.db.DbConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Servicio transversal de auditoria: solo inserta en auditoria_logs.
 * Fase 1: version minima. La Fase 8 agrega consultas (LISAUD/GETAUD).
 * Un fallo al auditar no debe tumbar el comando principal: se informa por stderr.
 * Clase no final para poder sustituirla en tests.
 */
public class AuditoriaService {

    /**
     * @param antes   JSON (texto) del estado previo o null
     * @param despues JSON (texto) del estado posterior o null
     */
    public void registrar(Integer userId, String accion, String entidad, Integer entidadId,
                          String antes, String despues, String comando) {
        String sql = "INSERT INTO auditoria_logs (user_id, accion, entidad, entidad_id, antes, despues, comando) "
                   + "VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)";
        try (Connection c = DbConnection.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            setNullableInt(ps, 1, userId);
            ps.setString(2, accion);
            ps.setString(3, entidad);
            setNullableInt(ps, 4, entidadId);
            ps.setString(5, antes);
            ps.setString(6, despues);
            ps.setString(7, comando);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[auditoria] no se pudo registrar " + accion + ": " + e.getMessage());
        }
    }

    private static void setNullableInt(PreparedStatement ps, int idx, Integer v) throws SQLException {
        if (v == null) ps.setNull(idx, Types.INTEGER); else ps.setInt(idx, v);
    }
}
