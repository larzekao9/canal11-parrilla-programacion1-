package canal11.gestion;

import canal11.db.DbConnection;
import canal11.model.Usuario;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/** Repositorio JDBC de usuarios. En la Fase 2 (CU1) se amplia con crear/listar/actualizar. */
public class UsuarioRepository implements UserLookup {

    private static final String COLS = "id, ci, nombre, apellido, rol, telefono, email, password, activo";

    @Override
    public Optional<Usuario> findByEmail(String email) {
        return findOne("SELECT " + COLS + " FROM users WHERE lower(email) = lower(?)", email);
    }

    public Optional<Usuario> findById(int id) {
        try (Connection c = DbConnection.open();
             PreparedStatement ps = c.prepareStatement("SELECT " + COLS + " FROM users WHERE id = ?")) {
            ps.setInt(1, id);
            return readOne(ps);
        } catch (SQLException e) {
            throw new IllegalStateException("Error de BD", e);
        }
    }

    private Optional<Usuario> findOne(String sql, String param) {
        try (Connection c = DbConnection.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, param);
            return readOne(ps);
        } catch (SQLException e) {
            throw new IllegalStateException("Error de BD", e);
        }
    }

    private Optional<Usuario> readOne(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) return Optional.empty();
            return Optional.of(map(rs));
        }
    }

    static Usuario map(ResultSet rs) throws SQLException {
        return new Usuario(rs.getInt("id"), rs.getString("ci"), rs.getString("nombre"),
                rs.getString("apellido"), rs.getString("rol"), rs.getString("telefono"),
                rs.getString("email"), rs.getString("password"), rs.getBoolean("activo"));
    }
}
