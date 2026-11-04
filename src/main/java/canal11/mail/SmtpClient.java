package canal11.mail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;

/** Envio SMTP simple (texto plano UTF-8). Autenticacion AUTH LOGIN opcional si se configuran usuario y password. */
public class SmtpClient implements MailSender {

    private static final int TIMEOUT_MS = 20_000;

    private final String host;
    private final int port;
    private final String from;
    private final String user;
    private final String password;

    public SmtpClient(String host, int port, String from, String user, String password) {
        this.host = host;
        this.port = port;
        this.from = from;
        this.user = user;
        this.password = password;
    }

    /** Verifica conexion y saludo EHLO sin enviar nada. */
    public void ping() throws IOException {
        try (Conn c = new Conn()) {
            c.ehlo();
            c.cmd("QUIT", 221);
        }
    }

    @Override
    public void send(String to, String subject, String body) throws IOException {
        try (Conn c = new Conn()) {
            c.ehlo();
            if (user != null && !user.isBlank()) {
                c.cmd("AUTH LOGIN", 334);
                c.cmd(b64(user), 334);
                c.cmd(b64(password == null ? "" : password), 235);
            }
            c.cmd("MAIL FROM:<" + from + ">", 250);
            c.cmd("RCPT TO:<" + to + ">", 250, 251);
            c.cmd("DATA", 354);
            StringBuilder msg = new StringBuilder();
            msg.append("From: ").append(from).append("\r\n");
            msg.append("To: ").append(to).append("\r\n");
            msg.append("Subject: ").append(encodeHeader(subject)).append("\r\n");
            msg.append("Date: ").append(DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now())).append("\r\n");
            msg.append("MIME-Version: 1.0\r\n");
            msg.append("Content-Type: text/plain; charset=UTF-8\r\n");
            msg.append("Content-Transfer-Encoding: 8bit\r\n\r\n");
            for (String line : body.replace("\r\n", "\n").split("\n", -1)) {
                if (line.startsWith(".")) line = "." + line; // dot-stuffing
                msg.append(line).append("\r\n");
            }
            msg.append(".\r\n");
            c.raw(msg.toString());
            c.expect(250);
            c.cmd("QUIT", 221);
        }
    }

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String encodeHeader(String s) {
        boolean ascii = s.chars().allMatch(ch -> ch < 128);
        return ascii ? s.replaceAll("[\\r\\n]+", " ")
                : "=?UTF-8?B?" + b64(s.replaceAll("[\\r\\n]+", " ")) + "?=";
    }

    private final class Conn implements AutoCloseable {
        private final Socket socket = new Socket();
        private final BufferedReader in;
        private final OutputStream out;

        Conn() throws IOException {
            socket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
            socket.setSoTimeout(TIMEOUT_MS);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = socket.getOutputStream();
            expect(220);
        }

        void ehlo() throws IOException {
            String name = "canal11";
            try { name = InetAddress.getLocalHost().getHostName(); } catch (Exception ignored) { /* usa el valor por defecto */ }
            raw("EHLO " + name + "\r\n");
            expect(250);
        }

        void cmd(String c, int... codes) throws IOException {
            raw(c + "\r\n");
            expect(codes);
        }

        void raw(String s) throws IOException {
            out.write(s.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }

        /** Lee una respuesta (posiblemente multilinea "250-...") y valida el codigo. */
        void expect(int... codes) throws IOException {
            String line;
            do {
                line = in.readLine();
                if (line == null) throw new IOException("El servidor SMTP cerro la conexion");
            } while (line.length() > 3 && line.charAt(3) == '-');
            int code;
            try { code = Integer.parseInt(line.substring(0, 3)); } catch (RuntimeException e) { throw new IOException("SMTP: respuesta invalida: " + line); }
            for (int c : codes) if (c == code) return;
            throw new IOException("SMTP: " + line);
        }

        @Override
        public void close() {
            try { socket.close(); } catch (IOException ignored) { /* nada que hacer */ }
        }
    }
}
