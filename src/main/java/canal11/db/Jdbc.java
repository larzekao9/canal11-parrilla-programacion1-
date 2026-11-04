package canal11.db;

import canal11.util.CommandException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Utilidades JDBC: transacciones, consultas a mapas y manejo de errores de BD. Siempre con PreparedStatement. */
public final class Jdbc {

    private Jdbc() {}

    @FunctionalInterface
    public interface Work<T> {
        T run(Connection c) throws Exception;
    }

    /** Ejecuta el trabajo en una transaccion: commit si termina bien, rollback si lanza. */
    public static <T> T tx(Work<T> work) {
        try (Connection c = DbConnection.open()) {
            c.setAutoCommit(false);
            try {
                T result = work.run(c);
                c.commit();
                return result;
            } catch (Throwable t) {
                try { c.rollback(); } catch (SQLException ignored) { /* ya hay un error principal */ }
                throw translate(t);
            }
        } catch (SQLException e) {
            throw translate(e);
        }
    }

    private static RuntimeException translate(Throwable t) {
        if (t instanceof CommandException ce) return ce;
        if (t instanceof SQLException se) {
            String state = se.getSQLState() == null ? "" : se.getSQLState();
            switch (state) {
                case "23505": return new CommandException("Ya existe un registro con esos datos (valor duplicado).");
                case "23503": return new CommandException("Referencia invalida o registro en uso por otros datos.");
                case "23514": return new CommandException("Algun valor esta fuera del rango permitido.");
                case "23502": return new CommandException("Falta un dato obligatorio.");
                case "22P02", "22007", "22008": return new CommandException("Formato de dato invalido.");
                default: return new IllegalStateException("Error de BD: " + se.getMessage(), se);
            }
        }
        if (t instanceof RuntimeException re) return re;
        return new IllegalStateException(t);
    }

    public static List<Map<String, Object>> query(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        row.put(md.getColumnLabel(i).toLowerCase(), normalize(rs.getObject(i)));
                    }
                    rows.add(row);
                }
                return rows;
            }
        }
    }

    /** Primera fila o null. */
    public static Map<String, Object> one(Connection c, String sql, Object... params) throws SQLException {
        List<Map<String, Object>> rows = query(c, sql, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public static int exec(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        }
    }

    /** INSERT ... RETURNING id => devuelve el id generado. */
    public static int insert(Connection c, String sqlReturningId, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sqlReturningId)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** Snapshot JSON de una fila (sin password). tabla es siempre una constante interna. */
    public static String json(Connection c, String table, int id) throws SQLException {
        Map<String, Object> r = one(c, "SELECT (to_jsonb(t) - 'password')::text AS j FROM " + table + " t WHERE id = ?", id);
        return r == null ? null : (String) r.get("j");
    }

    public static long count(Connection c, String sql, Object... params) throws SQLException {
        Map<String, Object> r = one(c, sql, params);
        return r == null ? 0 : ((Number) r.values().iterator().next()).longValue();
    }

    private static void bind(PreparedStatement ps, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object v = params[i];
            if (v == null) ps.setNull(i + 1, Types.NULL);
            else ps.setObject(i + 1, v);
        }
    }

    private static Object normalize(Object v) {
        if (v instanceof Time t) return t.toLocalTime();
        if (v instanceof Timestamp ts) return ts.toLocalDateTime();
        if (v instanceof java.sql.Date d) return d.toLocalDate();
        return v;
    }
}
