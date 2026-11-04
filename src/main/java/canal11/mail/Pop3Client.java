package canal11.mail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Lector POP3 (sin cifrado, puerto 110 del servidor del curso). Cada operacion abre su propia conexion.
 * SEGURIDAD: el buzon puede tener cientos de miles de correos ajenos (spam), asi que:
 *  - solo examina los ultimos {@code ventana} mensajes (STAT + UIDL n + TOP n 0, sin descargar todo);
 *  - solo devuelve los que cumplan {@code aceptar} (p. ej. subject con forma COMANDO[...] y remitente registrado);
 *  - el resto NO se toca (ni se responde ni se borra); delete() solo borra lo que el sistema proceso.
 */
public class Pop3Client implements MailReader {

    private static final int TIMEOUT_MS = 20_000;

    private final String host;
    private final int port;
    private final String user;
    private final String password;
    private final int ventana;
    private final Predicate<MailMessage> aceptar;

    public Pop3Client(String host, int port, String user, String password) {
        this(host, port, user, password, 200, m -> true);
    }

    public Pop3Client(String host, int port, String user, String password, int ventana, Predicate<MailMessage> aceptar) {
        this.host = host;
        this.port = port;
        this.user = user;
        this.password = password;
        this.ventana = Math.max(1, ventana);
        this.aceptar = aceptar;
    }

    /** Verifica conexion y login; devuelve la cantidad de mensajes en el buzon. */
    public int ping() throws IOException {
        try (Session s = open()) {
            String[] stat = s.cmd("STAT").split(" ");
            return Integer.parseInt(stat[1]);
        }
    }

    @Override
    public List<MailMessage> fetchAll() throws IOException {
        List<MailMessage> out = new ArrayList<>();
        try (Session s = open()) {
            int total = Integer.parseInt(s.cmd("STAT").split(" ")[0]);
            for (int n = Math.max(1, total - ventana + 1); n <= total; n++) {
                String uid = s.cmd("UIDL " + n).split("\\s+")[1];
                s.cmd("TOP " + n + " 0");
                MailMessage m = MailParser.parse(uid, s.multiline());
                if (aceptar.test(m)) out.add(m);
            }
            s.cmd("QUIT");
        }
        return out;
    }

    @Override
    public void delete(MailMessage m) throws IOException {
        try (Session s = open()) {
            int total = Integer.parseInt(s.cmd("STAT").split(" ")[0]);
            for (int n = total; n >= Math.max(1, total - ventana + 1); n--) {
                if (s.cmd("UIDL " + n).split("\\s+")[1].equals(m.id())) {
                    s.cmd("DELE " + n);
                    break;
                }
            }
            s.cmd("QUIT"); // el servidor aplica los DELE al cerrar con QUIT
        }
    }

    private Session open() throws IOException {
        Session s = new Session();
        try {
            s.connect();
            s.cmd("USER " + user);
            s.cmd("PASS " + password);
            return s;
        } catch (IOException e) {
            s.close();
            throw e;
        }
    }

    private final class Session implements AutoCloseable {
        private Socket socket;
        private BufferedReader in;
        private OutputStream out;

        void connect() throws IOException {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
            socket.setSoTimeout(TIMEOUT_MS);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
            out = socket.getOutputStream();
            status(in.readLine());
        }

        /** Envia un comando y devuelve el texto tras "+OK". Lanza si el servidor responde -ERR. */
        String cmd(String c) throws IOException {
            out.write((c + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
            return status(in.readLine());
        }

        private String status(String line) throws IOException {
            if (line == null) throw new IOException("El servidor POP3 cerro la conexion");
            if (!line.startsWith("+OK")) throw new IOException("POP3: " + line);
            return line.length() > 3 ? line.substring(3).trim() : "";
        }

        String multiline() throws IOException {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                if (line.equals(".")) break;
                if (line.startsWith("..")) line = line.substring(1); // des-escape de punto
                sb.append(line).append('\n');
            }
            return new String(sb.toString().getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        }

        @Override
        public void close() {
            try { if (socket != null) socket.close(); } catch (IOException ignored) { /* nada que hacer */ }
        }
    }
}
