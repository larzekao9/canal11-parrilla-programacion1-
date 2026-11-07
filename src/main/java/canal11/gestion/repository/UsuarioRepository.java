package canal11.gestion.repository;

import canal11.db.DbConnection;
import canal11.db.Jdbc;
import canal11.model.Usuario;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Repositorio JDBC de usuarios: login (findBy*) y SQL del CU1 (metodos con Connection de la transaccion del service). */
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

    public static Usuario map(ResultSet rs) throws SQLException {
        return new Usuario(rs.getInt("id"), rs.getString("ci"), rs.getString("nombre"),
                rs.getString("apellido"), rs.getString("rol"), rs.getString("telefono"),
                rs.getString("email"), rs.getString("password"), rs.getBoolean("activo"));
    }

    private static final String PUBLIC_COLS = "id, ci, nombre, apellido, rol, telefono, email, activo";

    public boolean existeCi(Connection c, String ci) throws SQLException {
        return Jdbc.count(c, "SELECT 1 FROM users WHERE ci = ?", ci) > 0;
    }

    public boolean existeEmail(Connection c, String emailMinuscula, Integer excluirId) throws SQLException {
        return excluirId == null
                ? Jdbc.count(c, "SELECT 1 FROM users WHERE lower(email) = ?", emailMinuscula) > 0
                : Jdbc.count(c, "SELECT 1 FROM users WHERE lower(email) = ? AND id <> ?", emailMinuscula, excluirId) > 0;
    }

    public int insert(Connection c, String ci, String nombre, String apellido, String rol, String tel, String email, String passwordHash) throws SQLException {
        return Jdbc.insert(c, "INSERT INTO users (ci, nombre, apellido, rol, telefono, email, password) "
                + "VALUES (?,?,?,?,?,?,?) RETURNING id", ci, nombre, apellido, rol, tel, email, passwordHash);
    }

    public List<Map<String, Object>> list(Connection c) throws SQLException {
        return Jdbc.query(c, "SELECT " + PUBLIC_COLS + " FROM users ORDER BY id");
    }

    public List<Map<String, Object>> listByRol(Connection c, String rol) throws SQLException {
        return Jdbc.query(c, "SELECT " + PUBLIC_COLS + " FROM users WHERE rol = ? ORDER BY id", rol);
    }

    public Map<String, Object> findPublicById(Connection c, int id) throws SQLException {
        return Jdbc.one(c, "SELECT " + PUBLIC_COLS + " FROM users WHERE id = ?", id);
    }

    public Map<String, Object> findByIdForUpdate(Connection c, int id) throws SQLException {
        return Jdbc.one(c, "SELECT * FROM users WHERE id = ? FOR UPDATE", id);
    }

    /** sets: fragmentos "columna = ?" generados por el service con nombres de una lista cerrada. */
    public void update(Connection c, int id, List<String> sets, List<Object> vals) throws SQLException {
        List<Object> all = new ArrayList<>(vals);
        all.add(id);
        Jdbc.exec(c, "UPDATE users SET " + String.join(", ", sets) + " WHERE id = ?", all.toArray());
    }

    /** Desactiva al usuario y cierra sus sesiones AUTH. */
    public void desactivar(Connection c, int id) throws SQLException {
        Jdbc.exec(c, "UPDATE users SET activo = FALSE WHERE id = ?", id);
        Jdbc.exec(c, "DELETE FROM auth_sessions WHERE user_id = ?", id);
    }

    public boolean existeOtroAdminActivo(Connection c, int excluirId) throws SQLException {
        return Jdbc.count(c, "SELECT 1 FROM users WHERE rol = 'administrador' AND activo AND id <> ?", excluirId) > 0;
    }

    public String snapshot(Connection c, int id) throws SQLException {
        return Jdbc.json(c, "users", id);
    }
}
