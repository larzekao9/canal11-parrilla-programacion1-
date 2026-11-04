package canal11.gestion;

import canal11.db.DbConfig;
import canal11.db.DbConnection;
import canal11.db.SchemaInitializer;
import canal11.emision.AuditoriaService;
import canal11.mail.CommandRouter;
import canal11.mail.InMemorySessionStore;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Clock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integracion contra PostgreSQL real. Solo corre si la BD configurada (DB_URL) termina en "_test",
 * porque BORRA todo el esquema public. Ej: DB_URL=jdbc:postgresql://localhost:5432/canal11_test
 */
class UsuarioServiceDbTest {

    CommandRouter router;

    @BeforeEach
    void setUp() throws Exception {
        Assumptions.assumeTrue(DbConfig.url().endsWith("_test"), "BD de pruebas no configurada (DB_URL=...*_test)");
        try (Connection c = DbConnection.open(); Statement st = c.createStatement()) {
            st.execute("DROP SCHEMA public CASCADE");
            st.execute("CREATE SCHEMA public");
            SchemaInitializer.createSchema(c);
            st.execute("INSERT INTO users (ci,nombre,apellido,rol,email,password) VALUES "
                    + "('1','Admin','A','administrador','admin@c.com','" + BCrypt.hashpw("admin12345", BCrypt.gensalt(4)) + "')");
        }
        AuditoriaService aud = new AuditoriaService();
        router = new CommandRouter(new UsuarioRepository(), new InMemorySessionStore(Clock.systemUTC()), aud);
        new UsuarioService(aud).registerAll(router);
        aud.registerAll(router);
        assertTrue(router.process("AUTH[\"admin12345\"]", "admin@c.com").startsWith("OK"));
    }

    String adm(String subject) { return router.process(subject, "admin@c.com"); }

    @Test
    void crudCompletoConReglas() {
        assertEquals("OK: usuario creado con ID 2",
                adm("INSUSU[\"22\",\"Ana\",\"Rojas\",\"programador\",\"70000001\",\"ana@c.com\",\"secreto123\"]"));
        assertTrue(adm("INSUSU[\"22\",\"X\",\"Y\",\"encargado\",\"\",\"x@c.com\",\"secreto123\"]").contains("ya existe") ||
                   adm("INSUSU[\"22\",\"X\",\"Y\",\"encargado\",\"\",\"x@c.com\",\"secreto123\"]").contains("Ya existe"));
        assertTrue(adm("INSUSU[\"33\",\"X\",\"Y\",\"jefe\",\"\",\"x@c.com\",\"secreto123\"]").contains("rol invalido"));
        assertTrue(adm("INSUSU[\"33\",\"X\",\"Y\",\"encargado\",\"\",\"x@c.com\",\"corta\"]").contains("minimo 8"));
        assertTrue(adm("LISUSU[\"*\"]").contains("ana@c.com"));
        assertTrue(adm("GETUSU[2]").contains("programador"));
        assertTrue(adm("UPDUSU[2,\"rol=encargado\",\"telefono=71111111\"]").startsWith("OK"));
        assertTrue(adm("LISUSU[\"encargado\"]").contains("71111111"));
    }

    @Test
    void protegeAlUltimoAdministradorYASiMismo() {
        assertTrue(adm("DELUSU[1]").contains("a si mismo"));
        assertTrue(adm("UPDUSU[1,\"rol=encargado\"]").contains("al menos un administrador"));
        assertTrue(adm("UPDUSU[1,\"activo=false\"]").contains("a si mismo"));
    }

    @Test
    void usuarioDesactivadoNoPuedeOperarYPasswordNoSeAudita() {
        adm("INSUSU[\"22\",\"Ana\",\"Rojas\",\"programador\",\"\",\"ana@c.com\",\"secreto123\"]");
        assertTrue(adm("DELUSU[2]").startsWith("OK"));
        assertEquals("Error: usuario inactivo.", router.process("HELP[]", "ana@c.com"));
        String aud = adm("LISAUD[\"users\"]");
        assertTrue(aud.contains("INSUSU"));
        assertFalse(aud.contains("secreto123"));
        assertTrue(aud.contains("\"***\""));
        assertTrue(adm("GETAUD[\"users\",2]").contains("USUARIO_DESACTIVAR"));
    }

    @Test
    void rolProgramadorNoGestionaUsuarios() {
        adm("INSUSU[\"22\",\"Ana\",\"Rojas\",\"programador\",\"\",\"ana@c.com\",\"secreto123\"]");
        assertTrue(router.process("AUTH[\"secreto123\"]", "ana@c.com").startsWith("OK"));
        assertTrue(router.process("LISUSU[\"*\"]", "ana@c.com").contains("Permiso denegado"));
    }

    @Test
    void auditoriaEsDeSoloInsercion() throws Exception {
        adm("HELP[]");
        try (Connection c = DbConnection.open(); Statement st = c.createStatement()) {
            assertThrows(java.sql.SQLException.class, () -> st.execute("DELETE FROM auditoria_logs"));
        }
    }
}
