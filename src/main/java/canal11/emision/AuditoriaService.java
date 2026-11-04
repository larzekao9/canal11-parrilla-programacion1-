package canal11.emision;

import canal11.db.DbConnection;
import canal11.db.Jdbc;
import canal11.mail.CommandSpec;
import canal11.mail.CommandRouter;
import canal11.model.Usuario;
import canal11.util.Args;
import canal11.util.CommandException;
import canal11.util.InputValidator;
import canal11.util.TextTable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Servicio transversal de auditoria (CU7 / RF-29). Solo inserta en auditoria_logs (la tabla es de solo insercion).
 * - registrar(Connection, ...) participa de la transaccion del comando (atomico con el cambio auditado).
 * - registrar(...) sin conexion usa la suya; un fallo se informa por stderr sin tumbar el comando (AUTH, etc.).
 * Clase no final para poder sustituirla en tests.
 */
public class AuditoriaService {

    private static final int MAX_FILAS = 100;

    /**
     * @param antes   JSON (texto) del estado previo o null
     * @param despues JSON (texto) del estado posterior o null
     */
    public void registrar(Integer userId, String accion, String entidad, Integer entidadId,
                          String antes, String despues, String comando) {
        try (Connection c = DbConnection.open()) {
            registrar(c, userId, accion, entidad, entidadId, antes, despues, comando);
        } catch (SQLException e) {
            System.err.println("[auditoria] no se pudo registrar " + accion + ": " + e.getMessage());
        }
    }

    public void registrar(Connection c, Integer userId, String accion, String entidad, Integer entidadId,
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

    private static void setNullableInt(PreparedStatement ps, int idx, Integer v) throws SQLException {
        if (v == null) ps.setNull(idx, Types.INTEGER); else ps.setInt(idx, v);
    }

    // ------------------------------------------------------------------ consultas (solo administrador)

    public void registerAll(CommandRouter r) {
        r.register(new CommandSpec("LISAUD", "LISAUD[\"desde\",\"hasta\"] | [usuario_id] | [\"entidad\"] | [\"*\"]",
                "Lista la auditoria", CommandSpec.ADMIN, this::listar));
        r.register(new CommandSpec("GETAUD", "GETAUD[\"entidad\",id]",
                "Historial de un registro", CommandSpec.ADMIN, this::historial));
    }

    String listar(Usuario u, List<String> p) {
        Args.count(p, 1, 2, "LISAUD[\"desde\",\"hasta\"] | LISAUD[usuario_id] | LISAUD[\"entidad\"] | LISAUD[\"*\"]");
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (p.size() == 2) {
            LocalDate d = InputValidator.validateDate(p.get(0), "desde");
            LocalDate h = InputValidator.validateDate(p.get(1), "hasta");
            if (h.isBefore(d)) throw new CommandException("hasta no puede ser anterior a desde");
            where.append(" AND a.created_at >= ? AND a.created_at < ?");
            params.add(d.atStartOfDay());
            params.add(h.plusDays(1).atStartOfDay());
        } else if (!Args.isAll(p)) {
            String v = p.get(0).trim();
            if (v.matches("\\d+")) {
                where.append(" AND a.user_id = ?");
                params.add(Integer.parseInt(v));
            } else {
                where.append(" AND a.entidad = ?");
                params.add(InputValidator.validateText(v, "entidad", 40).toLowerCase());
            }
        }
        String sql = "SELECT a.id, a.created_at AS fecha, COALESCE(x.email,'-') AS usuario, a.accion, "
                   + "COALESCE(a.entidad,'-') AS entidad, a.entidad_id, a.comando "
                   + "FROM auditoria_logs a LEFT JOIN users x ON x.id = a.user_id" + where
                   + " ORDER BY a.id DESC LIMIT " + MAX_FILAS;
        return Jdbc.tx(c -> {
            List<Map<String, Object>> rows = Jdbc.query(c, sql, params.toArray());
            registrar(c, u.id(), "CONSULTA_AUDITORIA", "auditoria_logs", null, null, null, "LISAUD");
            return "Auditoria (ultimos " + MAX_FILAS + " como maximo):\n"
                    + TextTable.render(rows, "id", "fecha", "usuario", "accion", "entidad", "entidad_id", "comando");
        });
    }

    String historial(Usuario u, List<String> p) {
        Args.exact(p, 2, "GETAUD[\"entidad\",id]");
        String entidad = InputValidator.validateText(p.get(0), "entidad", 40).toLowerCase();
        int id = InputValidator.validateId(p.get(1), "id");
        return Jdbc.tx(c -> {
            List<Map<String, Object>> rows = Jdbc.query(c,
                    "SELECT a.id, a.created_at AS fecha, COALESCE(x.email,'-') AS usuario, a.accion, "
                  + "a.antes::text AS antes, a.despues::text AS despues "
                  + "FROM auditoria_logs a LEFT JOIN users x ON x.id = a.user_id "
                  + "WHERE a.entidad = ? AND a.entidad_id = ? ORDER BY a.id", entidad, id);
            if (rows.isEmpty()) return "(sin historial para " + entidad + " " + id + ")";
            StringBuilder sb = new StringBuilder("Historial de " + entidad + " " + id + ":\n");
            for (Map<String, Object> r : rows) {
                sb.append("#").append(r.get("id")).append(' ').append(TextTable.fmt(r.get("fecha")))
                  .append(" | ").append(r.get("usuario")).append(" | ").append(r.get("accion")).append('\n');
                sb.append("   antes:   ").append(TextTable.fmt(r.get("antes"))).append('\n');
                sb.append("   despues: ").append(TextTable.fmt(r.get("despues"))).append('\n');
            }
            return sb.toString().stripTrailing();
        });
    }
}
