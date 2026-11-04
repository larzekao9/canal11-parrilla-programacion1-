package canal11.mail;

import canal11.emision.AuditoriaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class MailDaemonTest {

    @TempDir
    Path tmp;

    @Test
    void sobreviveACorreosMalformadosYResponde() throws Exception {
        FileMailbox box = new FileMailbox(tmp);
        CommandRouter router = new CommandRouter(e -> Optional.empty(), new InMemorySessionStore(),
                new AuditoriaService());
        MailDaemon daemon = new MailDaemon(box, box, router);

        box.depositar("a@x.com", "esto no es un comando");
        box.depositar("b@x.com", "HELP[");
        box.depositar("c@x.com", "HELP[]");

        assertEquals(3, daemon.processOnce());
        assertEquals(0, daemon.processOnce(), "los correos procesados se eliminan");

        try (Stream<Path> s = Files.list(tmp.resolve("salida"))) {
            assertEquals(3, s.count());
        }
        try (Stream<Path> s = Files.list(tmp.resolve("entrada"))) {
            assertEquals(0, s.count());
        }
    }
}
