package canal11.mail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Buzon simulado en carpetas (reemplaza a POP3/SMTP mientras no hay servidor de correo).
 *   buzon-simulado/entrada/*.txt  -> correos "recibidos": lineas "From: ..." y "Subject: ...", luego el body.
 *   buzon-simulado/salida/*.txt   -> respuestas "enviadas": "To: ...", "Subject: ...", luego el body.
 */
public class FileMailbox implements MailReader, MailSender {

    private final Path entrada;
    private final Path salida;
    private final AtomicInteger seq = new AtomicInteger();

    public FileMailbox(Path root) {
        this.entrada = root.resolve("entrada");
        this.salida = root.resolve("salida");
        try {
            Files.createDirectories(entrada);
            Files.createDirectories(salida);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo crear el buzon simulado en " + root, e);
        }
    }

    @Override
    public List<MailMessage> fetchAll() throws IOException {
        List<Path> files;
        try (Stream<Path> s = Files.list(entrada)) {
            files = s.filter(p -> p.getFileName().toString().endsWith(".txt")).sorted().toList();
        }
        List<MailMessage> out = new ArrayList<>();
        for (Path p : files) {
            String from = "", subject = "";
            StringBuilder body = new StringBuilder();
            boolean inBody = false;
            for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                if (!inBody && line.regionMatches(true, 0, "From:", 0, 5)) from = line.substring(5).trim();
                else if (!inBody && line.regionMatches(true, 0, "Subject:", 0, 8)) subject = line.substring(8).trim();
                else { inBody = true; body.append(line).append('\n'); }
            }
            out.add(new MailMessage(p.getFileName().toString(), from, subject, body.toString()));
        }
        return out;
    }

    @Override
    public void delete(MailMessage message) throws IOException {
        Files.deleteIfExists(entrada.resolve(message.id()));
    }

    @Override
    public void send(String to, String subject, String body) throws IOException {
        String name = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + "-" + String.format("%03d", seq.incrementAndGet()) + ".txt";
        String content = "To: " + to + "\nSubject: " + subject + "\n\n" + body + "\n";
        Files.writeString(salida.resolve(name), content, StandardCharsets.UTF_8);
    }

    /** Deposita un correo en la entrada (usado por el simulador de consola). */
    public void depositar(String from, String subject) throws IOException {
        String name = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + "-" + String.format("%03d", seq.incrementAndGet()) + ".txt";
        Files.writeString(entrada.resolve(name), "From: " + from + "\nSubject: " + subject + "\n\n",
                StandardCharsets.UTF_8);
    }
}
