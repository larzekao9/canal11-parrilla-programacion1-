package canal11.mail;

import canal11.emision.AuditoriaService;
import canal11.model.Usuario;
import canal11.util.CommandException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CommandRouterTest {

    /** Reloj manual para probar la expiracion de 30 min. */
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-20T10:00:00Z");
        public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(java.time.ZoneId z) { return this; }
        public Instant instant() { return now; }
    }

    /** Auditoria que no toca la BD. */
    static class FakeAuditoria extends AuditoriaService {
        final List<String> acciones = new ArrayList<>();
        @Override
        public void registrar(Integer u, String accion, String e, Integer id, String a, String d, String c) {
            acciones.add(accion + "|" + c);
        }
    }

    MutableClock clock;
    FakeAuditoria auditoria;
    CommandRouter router;

    static Usuario user(int id, String rol, String email, boolean activo) {
        return new Usuario(id, "ci" + id, "Nombre" + id, "Ap", rol, null, email,
                BCrypt.hashpw("secreto123", BCrypt.gensalt(4)), activo);
    }

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        auditoria = new FakeAuditoria();
        Map<String, Usuario> db = Map.of(
                "admin@c.com", user(1, Usuario.ADMIN, "admin@c.com", true),
                "prog@c.com", user(2, Usuario.PROGRAMADOR, "prog@c.com", true),
                "baja@c.com", user(3, Usuario.ENCARGADO, "baja@c.com", false));
        router = new CommandRouter(e -> Optional.ofNullable(db.get(e.toLowerCase())),
                new InMemorySessionStore(clock), auditoria);
        router.register(new CommandSpec("SOLOADM", "SOLOADM[]", "Solo admin", CommandSpec.ADMIN,
                (u, p) -> "OK: hecho " + p));
        router.register(new CommandSpec("BOOM", "BOOM[]", "Falla", CommandSpec.TODOS,
                (u, p) -> { throw new IllegalStateException("secreto interno"); }));
        router.register(new CommandSpec("NEG", "NEG[]", "Error de negocio", CommandSpec.TODOS,
                (u, p) -> { throw new CommandException("regla violada"); }));
    }

    @Test
    void subjectMalformado() {
        assertTrue(router.process("hola mundo", "admin@c.com").startsWith("Comando no reconocido"));
        assertTrue(router.process(null, "admin@c.com").startsWith("Comando no reconocido"));
        assertTrue(router.process("", null).startsWith("Comando no reconocido"));
    }

    @Test
    void remitenteDesconocido() {
        assertTrue(router.process("HELP[]", "x@x.com").contains("correo no registrado"));
        assertTrue(router.process("HELP[]", null).contains("correo no registrado"));
    }

    @Test
    void usuarioInactivo() {
        assertTrue(router.process("AUTH[\"secreto123\"]", "baja@c.com").contains("inactivo"));
    }

    @Test
    void comandoSinAuthPideAuth() {
        assertTrue(router.process("SOLOADM[]", "admin@c.com").contains("Autenticacion requerida"));
    }

    @Test
    void authCorrectoHabilitaYIncorrectoNo() {
        assertTrue(router.process("AUTH[\"mala\"]", "admin@c.com").contains("Credenciales invalidas"));
        assertTrue(router.process("SOLOADM[]", "admin@c.com").contains("Autenticacion requerida"));
        assertTrue(router.process("AUTH[\"secreto123\"]", "admin@c.com").startsWith("OK"));
        assertEquals("OK: hecho []", router.process("SOLOADM[]", "admin@c.com"));
    }

    @Test
    void sesionExpiraA30Min() {
        router.process("AUTH[\"secreto123\"]", "admin@c.com");
        clock.now = clock.now.plus(Duration.ofMinutes(29));
        assertTrue(router.process("SOLOADM[]", "admin@c.com").startsWith("OK"));
        clock.now = clock.now.plus(Duration.ofMinutes(2));
        assertTrue(router.process("SOLOADM[]", "admin@c.com").contains("Autenticacion requerida"));
    }

    @Test
    void permisoPorRol() {
        router.process("AUTH[\"secreto123\"]", "prog@c.com");
        assertTrue(router.process("SOLOADM[]", "prog@c.com").contains("Permiso denegado para rol: programador"));
    }

    @Test
    void comandoDesconocido() {
        assertTrue(router.process("NOEXISTE[]", "admin@c.com").startsWith("Comando no reconocido"));
    }

    @Test
    void errorDeNegocioYErrorInterno() {
        router.process("AUTH[\"secreto123\"]", "admin@c.com");
        assertEquals("Error: regla violada", router.process("NEG[]", "admin@c.com"));
        String r = router.process("BOOM[]", "admin@c.com");
        assertTrue(r.startsWith("Error:"));
        assertFalse(r.contains("secreto interno")); // no filtra detalles internos
    }

    @Test
    void helpListaSoloLoPermitidoSinNecesitarAuth() {
        String admin = router.process("HELP[]", "admin@c.com");
        assertTrue(admin.contains("SOLOADM[]"));
        String prog = router.process("help[]", "prog@c.com");
        assertFalse(prog.contains("SOLOADM[]"));
        assertTrue(prog.contains("NEG[]"));
    }

    @Test
    void authSeAuditaSinPassword() {
        router.process("AUTH[\"secreto123\"]", "admin@c.com");
        router.process("AUTH[\"mala\"]", "admin@c.com");
        assertEquals(List.of("AUTH|AUTH[\"***\"]", "AUTH_FALLIDO|AUTH[\"***\"]"), auditoria.acciones);
    }

    @Test
    void authSinParametro() {
        assertTrue(router.process("AUTH[]", "admin@c.com").startsWith("Error: Uso:"));
    }
}
