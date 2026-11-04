package canal11;

import canal11.db.DbConnection;
import canal11.db.Seed;
import canal11.db.SchemaInitializer;

import java.sql.Connection;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Crea las tablas y el administrador semilla (idempotente). */
public class App {
    public static void main(String[] args) throws Exception {
        try (Connection c = DbConnection.open()) {
            // Si db.url trae ?currentSchema=xxx, el esquema se crea solo (las tablas quedan aisladas de las demas del servidor).
            Matcher m = Pattern.compile("currentSchema=([A-Za-z_][A-Za-z0-9_]*)").matcher(canal11.db.DbConfig.url());
            if (m.find()) {
                try (Statement st = c.createStatement()) {
                    st.execute("CREATE SCHEMA IF NOT EXISTS " + m.group(1));
                }
                System.out.println("Esquema '" + m.group(1) + "' listo.");
            }
            SchemaInitializer.createSchema(c);
            System.out.println("Esquema listo.");
            System.out.println(SchemaInitializer.ensureAdminSeed(c)
                    ? "Administrador semilla creado."
                    : "Ya existe un administrador; no se creo semilla.");
            System.out.println("Programas base agregados: " + Seed.ensureProgramas(c));
        }
    }
}
