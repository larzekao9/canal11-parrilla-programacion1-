package canal11.testutil;

import canal11.db.DbConfig;
import canal11.db.DbConnection;
import canal11.db.SchemaInitializer;
import canal11.db.Seed;
import canal11.mail.CommandRouter;
import canal11.mail.InMemorySessionStore;
import canal11.mail.RouterFactory;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Clock;

/**
 * Base de pruebas de integracion: BD PostgreSQL real, esquema limpio en cada test, router real cableado.
 * Solo corre si DB_URL termina en "_test" (BORRA el esquema public). Usuarios: admin@c.com (1), prog@c.com (2),
 * enc@c.com (3); todos con password "secreto123" y sesion AUTH ya abierta.
 */
public abstract class DbTestBase {

    protected CommandRouter router;

    @BeforeEach
    void baseSetUp() throws Exception {
        Assumptions.assumeTrue(DbConfig.url().endsWith("_test"), "BD de pruebas no configurada (DB_URL=...*_test)");
        try (Connection c = DbConnection.open(); Statement st = c.createStatement()) {
            st.execute("DROP SCHEMA public CASCADE");
            st.execute("CREATE SCHEMA public");
            SchemaInitializer.createSchema(c);
            String h = BCrypt.hashpw("secreto123", BCrypt.gensalt(4));
            st.execute("INSERT INTO users (ci,nombre,apellido,rol,email,password) VALUES "
                    + "('1','Admin','A','administrador','admin@c.com','" + h + "'),"
                    + "('2','Prog','P','programador','prog@c.com','" + h + "'),"
                    + "('3','Enc','E','encargado','enc@c.com','" + h + "')");
            Seed.ensureProgramas(c);
        }
        router = RouterFactory.create(new InMemorySessionStore(Clock.systemUTC()));
        for (String u : new String[]{"admin@c.com", "prog@c.com", "enc@c.com"}) {
            if (!router.process("AUTH[\"secreto123\"]", u).startsWith("OK")) throw new IllegalStateException("AUTH " + u);
        }
    }

    protected String admin(String subject) { return router.process(subject, "admin@c.com"); }
    protected String prog(String subject)  { return router.process(subject, "prog@c.com"); }
    protected String enc(String subject)   { return router.process(subject, "enc@c.com"); }
}
