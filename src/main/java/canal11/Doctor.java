package canal11;

import canal11.db.DbConfig;
import canal11.db.DbConnection;
import canal11.mail.MailFactory;
import canal11.mail.Pop3Client;
import canal11.mail.SmtpClient;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Diagnostico de conexiones: BD, POP3 y SMTP (sin enviar nada). Nunca imprime contrasenas.
 *   mvn -q compile exec:java -Dexec.mainClass=canal11.Doctor
 */
public class Doctor {

    public static void main(String[] args) {
        System.out.println("== Diagnostico Canal 11 ==");
        boolean ok = true;

        System.out.println("\n[BD] " + DbConfig.url() + "  usuario=" + DbConfig.user());
        try (Connection c = DbConnection.open(); Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT current_database(), version()")) {
                rs.next();
                System.out.println("  OK conectado a " + rs.getString(1));
            }
            try (ResultSet rs = st.executeQuery("SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'")) {
                rs.next();
                int n = rs.getInt(1);
                System.out.println("  tablas en public: " + n + (n == 0 ? "  (ejecute canal11.App para crear el esquema)" : ""));
            }
        } catch (Exception e) {
            ok = false;
            System.out.println("  FALLO: " + e.getMessage());
        }

        System.out.println("\n[POP3] " + MailFactory.pop3Host() + ":" + MailFactory.pop3Port() + "  usuario=" + DbConfig.get("mail.user", "(sin configurar)"));
        try {
            Pop3Client pop = MailFactory.pop3();
            System.out.println("  OK login correcto; mensajes en el buzon: " + pop.ping());
        } catch (Exception e) {
            ok = false;
            System.out.println("  FALLO: " + e.getMessage());
        }

        System.out.println("\n[SMTP] " + MailFactory.smtpHost() + ":" + MailFactory.smtpPort());
        try {
            SmtpClient smtp = MailFactory.smtp();
            smtp.ping();
            System.out.println("  OK el servidor responde (no se envio ningun correo)");
        } catch (Exception e) {
            ok = false;
            System.out.println("  FALLO: " + e.getMessage()
                    + (MailFactory.smtpPort() == 2525 ? "  (recuerde mantener abierto el tunel SSH)" : ""));
        }

        System.out.println(ok ? "\nTodo listo." : "\nHay conexiones con problemas; revise config.properties.");
    }
}
