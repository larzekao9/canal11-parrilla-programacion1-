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

/**
 * Lector POP3 (sin cifrado, puerto 110 del servidor del curso). Cada operacion abre su propia conexion:
 * fetchAll() lee todo sin borrar; delete() identifica el mensaje por UIDL y lo borra.
 */
public class Pop3Client implements MailReader {

    private static final int TIMEOUT_MS = 20_000;

    private final String host;
    private final int port;
    private final String user;
    private final String password;

    public Pop3Client(String host, int port, String user, String password) {
        this.host = host;
        this.port = port;
        this.user = user;
        this.password = password;
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
            Map<Integer, String> uids = s.uidl();
            for (Map.Entry<Integer, String> e : uids.entrySet()) {
                s.cmd("RETR " + e.getKey());
                String raw = s.multiline();
                out.add(MailParser.parse(e.getValue(), raw));
            }
            s.cmd("QUIT");
        }
        return out;
    }

    @Override
    public void delete(MailMessage m) throws IOException {
        try (Session s = open()) {
            for (Map.Entry<Integer, String> e : s.uidl().entrySet()) {
                if (e.getValue().equals(m.id())) {
                    s.cmd("DELE " + e.getKey());
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

        Map<Integer, String> uidl() throws IOException {
            cmd("UIDL");
            Map<Integer, String> map = new LinkedHashMap<>();
            for (String l : multiline().split("\n")) {
                String[] p = l.trim().split("\\s+");
                if (p.length == 2) map.put(Integer.parseInt(p[0]), p[1]);
            }
            return map;
        }

        @Override
        public void close() {
            try { if (socket != null) socket.close(); } catch (IOException ignored) { /* nada que hacer */ }
        }
    }
}
