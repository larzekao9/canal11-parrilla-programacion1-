package canal11.mail;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Prueba los clientes POP3/SMTP contra servidores falsos en localhost (sin red externa ni credenciales reales). */
class MailProtocolTest {

    /** Servidor TCP de un solo uso: ejecuta el guion en un hilo y registra las lineas recibidas. */
    static class FakeServer implements AutoCloseable {
        final ServerSocket ss = new ServerSocket(0);
        final List<String> received = new ArrayList<>();
        final Thread t;

        FakeServer(Script script) throws IOException {
            t = new Thread(() -> {
                try (Socket s = ss.accept()) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                    Writer out = new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8);
                    script.run(in, out, received);
                } catch (IOException ignored) { /* el cliente cierra */ }
            });
            t.setDaemon(true);
            t.start();
        }

        int port() { return ss.getLocalPort(); }
        public void close() throws IOException { ss.close(); }
    }

    interface Script { void run(BufferedReader in, Writer out, List<String> log) throws IOException; }

    static void send(Writer w, String s) throws IOException { w.write(s + "\r\n"); w.flush(); }

    /** Buzon falso con N mensajes: responde STAT, UIDL n, TOP n 0, DELE n y registra todo lo recibido. */
    static Script buzon(String[] headers, List<Integer> borrados) {
        return (in, out, log) -> {
            send(out, "+OK hola");
            String l;
            while ((l = in.readLine()) != null) {
                log.add(l);
                if (l.startsWith("USER") || l.startsWith("PASS")) send(out, "+OK");
                else if (l.equals("STAT")) send(out, "+OK " + headers.length + " 1000");
                else if (l.startsWith("UIDL ")) { int n = Integer.parseInt(l.substring(5)); send(out, "+OK " + n + " uid-" + n); }
                else if (l.startsWith("TOP ")) { int n = Integer.parseInt(l.split(" ")[1]); send(out, "+OK"); out.write(headers[n - 1]); send(out, "."); }
                else if (l.startsWith("DELE ")) { borrados.add(Integer.parseInt(l.substring(5))); send(out, "+OK"); }
                else if (l.equals("QUIT")) { send(out, "+OK bye"); break; }
            }
        };
    }

    static String mail(String from, String subject) {
        return "From: " + from + "\r\nSubject: " + subject + "\r\n\r\n";
    }

    @Test
    void pop3SoloExaminaLaVentanaRecienteYSoloDevuelveLoAceptado() throws Exception {
        // 1000 mensajes simulados; solo los 3 ultimos entran en la ventana. Spam y no-comandos no se devuelven.
        String[] msgs = new String[1000];
        for (int i = 0; i < 1000; i++) msgs[i] = mail("spam@x.com", "Oferta " + i);
        msgs[997] = mail("Ana <ana@c.com>", "HELP[]");                     // comando de usuario registrado
        msgs[998] = mail("spam@x.com", "LISUSU[\"*\"]");                   // comando, pero remitente desconocido
        msgs[999] = mail("ana@c.com", "hola, una pregunta");              // usuario registrado pero no es comando
        msgs[10] = mail("ana@c.com", "HELP[]");                           // fuera de la ventana: no debe verse
        List<Integer> borrados = new ArrayList<>();
        try (FakeServer s = new FakeServer(buzon(msgs, borrados))) {
            Pop3Client c = new Pop3Client("localhost", s.port(), "grupo12sa", "x", 3,
                    m -> SubjectParser.esValido(m.subject()) && m.from().equals("ana@c.com"));
            List<MailMessage> res = c.fetchAll();
            assertEquals(1, res.size());
            assertEquals("HELP[]", res.get(0).subject());
            assertEquals("ana@c.com", res.get(0).from());
            assertEquals("uid-998", res.get(0).id());
            assertTrue(s.received.stream().noneMatch(x -> x.startsWith("RETR")), "no debe descargar mensajes completos");
            assertTrue(s.received.stream().noneMatch(x -> x.startsWith("DELE")), "fetchAll no borra nada");
            assertEquals(3, s.received.stream().filter(x -> x.startsWith("TOP")).count(), "solo 3 mensajes examinados");
        }
    }

    @Test
    void pop3BorraSoloElMensajeProcesado() throws Exception {
        String[] msgs = new String[50];
        for (int i = 0; i < 50; i++) msgs[i] = mail("spam@x.com", "Oferta " + i);
        List<Integer> borrados = new ArrayList<>();
        try (FakeServer s = new FakeServer(buzon(msgs, borrados))) {
            new Pop3Client("localhost", s.port(), "u", "p", 10, m -> true).delete(new MailMessage("uid-48", "a", "b", ""));
            s.t.join(2000);
        }
        assertEquals(List.of(48), borrados);
    }

    @Test
    void pop3LoginInvalidoLanzaError() throws Exception {
        try (FakeServer s = new FakeServer((in, out, log) -> {
            send(out, "+OK hola");
            in.readLine(); send(out, "+OK");
            in.readLine(); send(out, "-ERR autenticacion fallida");
        })) {
            IOException e = assertThrows(IOException.class, () -> new Pop3Client("localhost", s.port(), "u", "mala").ping());
            assertTrue(e.getMessage().contains("autenticacion fallida"));
        }
    }

    @Test
    void smtpEnviaMensajeConDotStuffingYUtf8() throws Exception {
        try (FakeServer s = new FakeServer((in, out, log) -> {
            send(out, "220 listo");
            String l;
            while ((l = in.readLine()) != null) {
                log.add(l);
                if (l.startsWith("EHLO")) { send(out, "250-hola"); send(out, "250 OK"); }
                else if (l.startsWith("MAIL FROM") || l.startsWith("RCPT TO")) send(out, "250 OK");
                else if (l.equals("DATA")) {
                    send(out, "354 siga");
                    while (!(l = in.readLine()).equals(".")) log.add(l);
                    send(out, "250 encolado");
                } else if (l.equals("QUIT")) { send(out, "221 chao"); break; }
            }
        })) {
            new SmtpClient("localhost", s.port(), "grupo12sa@tecnoweb.org.bo", null, null)
                    .send("ana@c.com", "RE: Edición", "OK: listo\n.linea con punto\nñandú");
            s.t.join(2000);
            assertTrue(s.received.contains("MAIL FROM:<grupo12sa@tecnoweb.org.bo>"));
            assertTrue(s.received.contains("RCPT TO:<ana@c.com>"));
            assertTrue(s.received.stream().anyMatch(x -> x.startsWith("Subject: =?UTF-8?B?")));
            assertTrue(s.received.contains("..linea con punto"), "dot-stuffing: " + s.received);
            assertTrue(s.received.contains("ñandú"));
        }
    }

    @Test
    void smtpRechazoLanzaError() throws Exception {
        try (FakeServer s = new FakeServer((in, out, log) -> {
            send(out, "220 listo");
            in.readLine(); send(out, "250 OK");
            in.readLine(); send(out, "250 OK");
            in.readLine(); send(out, "550 destinatario rechazado");
        })) {
            IOException e = assertThrows(IOException.class,
                    () -> new SmtpClient("localhost", s.port(), "a@b.com", null, null).send("x@y.com", "s", "b"));
            assertTrue(e.getMessage().contains("550"));
        }
    }
}
