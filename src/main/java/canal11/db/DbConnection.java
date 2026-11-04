package canal11.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Fabrica de conexiones JDBC. Cada llamada abre una conexion nueva; el llamador la cierra (try-with-resources). */
public final class DbConnection {

    private DbConnection() {}

    public static Connection open() throws SQLException {
        return DriverManager.getConnection(DbConfig.url(), DbConfig.user(), DbConfig.password());
    }
}
