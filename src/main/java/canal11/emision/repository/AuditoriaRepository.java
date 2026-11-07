package canal11.emision.repository;

import canal11.db.Jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Map;

/** SQL de auditoria_logs. La tabla es de solo insercion. */
public class AuditoriaRepository {

    public void insert(Connection c, Integer userId, String accion, String entidad, Integer entidadId,
                       String antes, String despues, String comando) throws SQLException {
        String sql = "INSERT INTO auditoria_logs (user_id, accion, entidad, entidad_id, antes, despues, comando) "
                   + "VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            setNullableInt(ps, 1, userId);
            ps.setString(2, accion);
            ps.setString(3, entidad);
            setNullableInt(ps, 4, entidadId);
            ps.setString(5, antes);
            ps.setString(6, despues);
            ps.setString(7, comando);
            ps.executeUpdate();
        }
    }

    /** whereClause: fragmento " WHERE ..." con marcadores ?, armado por el service con condiciones fijas. */
    public List<Map<String, Object>> listar(Connection c, String whereClause, List<Object> params, int limite) throws SQLException {
        String sql = "SELECT a.id, a.created_at AS fecha, COALESCE(x.email,'-') AS usuario, a.accion, "
                   + "COALESCE(a.entidad,'-') AS entidad, a.entidad_id, a.comando "
                   + "FROM auditoria_logs a LEFT JOIN users x ON x.id = a.user_id" + whereClause
                   + " ORDER BY a.id DESC LIMIT " + limite;
        return Jdbc.query(c, sql, params.toArray());
    }

    public List<Map<String, Object>> historial(Connection c, String entidad, int id) throws SQLException {
        return Jdbc.query(c,
                "SELECT a.id, a.created_at AS fecha, COALESCE(x.email,'-') AS usuario, a.accion, "
              + "a.antes::text AS antes, a.despues::text AS despues "
              + "FROM auditoria_logs a LEFT JOIN users x ON x.id = a.user_id "
              + "WHERE a.entidad = ? AND a.entidad_id = ? ORDER BY a.id", entidad, id);
    }

    private static void setNullableInt(PreparedStatement ps, int idx, Integer v) throws SQLException {
        if (v == null) ps.setNull(idx, Types.INTEGER); else ps.setInt(idx, v);
    }
}
