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

    @Test
    void pop3LeeMensajesYBorra() throws Exception {
        String raw = "From: Ana <ana@c.com>\r\nSubject: HELP[]\r\n\r\ncuerpo\r\n..punto\r\n";
        // dos conexiones: fetchAll y delete
        try (FakeServer s1 = new FakeServer((in, out, log) -> {
            send(out, "+OK hola");
            String l;
            while ((l = in.readLine()) != null) {
                log.add(l);
                if (l.startsWith("USER") || l.startsWith("PASS")) send(out, "+OK");
                else if (l.equals("UIDL")) { send(out, "+OK"); send(out, "1 uid-aaa"); send(out, "."); }
                else if (l.startsWith("RETR")) { send(out, "+OK"); out.write(raw); send(out, "."); }
                else if (l.equals("QUIT")) { send(out, "+OK bye"); break; }
            }
        })) {
            Pop3Client c = new Pop3Client("localhost", s1.port(), "grupo12sa", "x");
            List<MailMessage> msgs = c.fetchAll();
            assertEquals(1, msgs.size());
            assertEquals("ana@c.com", msgs.get(0).from());
            assertEquals("HELP[]", msgs.get(0).subject());
            assertEquals("uid-aaa", msgs.get(0).id());
            assertTrue(s1.received.contains("USER grupo12sa"));
        }
        List<String> log2 = new ArrayList<>();
        try (FakeServer s2 = new FakeServer((in, out, log) -> {
            send(out, "+OK hola");
            String l;
            while ((l = in.readLine()) != null) {
                log.add(l);
                if (l.startsWith("USER") || l.startsWith("PASS") || l.startsWith("DELE")) send(out, "+OK");
                else if (l.equals("UIDL")) { send(out, "+OK"); send(out, "1 otro"); send(out, "2 uid-aaa"); send(out, "."); }
                else if (l.equals("QUIT")) { send(out, "+OK bye"); break; }
            }
        })) {
            new Pop3Client("localhost", s2.port(), "u", "p").delete(new MailMessage("uid-aaa", "a", "b", ""));
            s2.t.join(2000);
            log2.addAll(s2.received);
        }
        assertTrue(log2.contains("DELE 2"), "debe borrar el mensaje 2: " + log2);
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
