package canal11;

import canal11.db.DbConnection;
import canal11.db.Seed;
import canal11.db.SchemaInitializer;

import java.sql.Connection;

/** Crea las tablas y el administrador semilla (idempotente). */
public class App {
    public static void main(String[] args) throws Exception {
        try (Connection c = DbConnection.open()) {
            SchemaInitializer.createSchema(c);
            System.out.println("Esquema listo.");
            System.out.println(SchemaInitializer.ensureAdminSeed(c)
                    ? "Administrador semilla creado."
                    : "Ya existe un administrador; no se creo semilla.");
            System.out.println("Programas base agregados: " + Seed.ensureProgramas(c));
        }
    }
}
