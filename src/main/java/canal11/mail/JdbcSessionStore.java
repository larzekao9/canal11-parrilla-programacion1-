package canal11.mail;

import canal11.db.DbConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Sesiones persistidas en auth_sessions: sobreviven a reinicios del daemon. */
public class JdbcSessionStore implements SessionStore {

    @Override
    public boolean isActive(int userId) {
        String sql = "SELECT 1 FROM auth_sessions WHERE user_id = ? AND expires_at > now()";
        try (Connection c = DbConnection.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error de BD", e);
        }
    }

    @Override
    public void open(int userId, int minutes) {
        String sql = "INSERT INTO auth_sessions (user_id, expires_at) VALUES (?, now() + make_interval(mins => ?)) "
                   + "ON CONFLICT (user_id) DO UPDATE SET expires_at = EXCLUDED.expires_at";
        try (Connection c = DbConnection.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, minutes);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Error de BD", e);
        }
    }
}
