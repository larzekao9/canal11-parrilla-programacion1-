package canal11.mail;

import canal11.db.DbConfig;

/** Construye los clientes de correo reales desde config.properties / variables de entorno. */
public final class MailFactory {

    private MailFactory() {}

    public static String pop3Host()  { return DbConfig.get("mail.pop3.host", "mail.tecnoweb.org.bo"); }
    public static int pop3Port()     { return Integer.parseInt(DbConfig.get("mail.pop3.port", "110")); }
    public static String smtpHost()  { return DbConfig.get("mail.smtp.host", "mail.tecnoweb.org.bo"); }
    public static int smtpPort()     { return Integer.parseInt(DbConfig.get("mail.smtp.port", "25")); }
    public static String mailUser()  { return DbConfig.require("mail.user"); }

    /** Cliente para diagnostico (sin filtro). */
    public static Pop3Client pop3() {
        return new Pop3Client(pop3Host(), pop3Port(), mailUser(), DbConfig.require("mail.password"));
    }

    /** Cliente para el daemon: ventana reciente y solo correos aceptados por el filtro. */
    public static Pop3Client pop3(java.util.function.Predicate<MailMessage> aceptar) {
        int ventana = Integer.parseInt(DbConfig.get("mail.ventana", "200"));
        return new Pop3Client(pop3Host(), pop3Port(), mailUser(), DbConfig.require("mail.password"), ventana, aceptar);
    }

    public static SmtpClient smtp() {
        String from = DbConfig.get("mail.from", DbConfig.get("mail.user", "grupo12sa") + "@tecnoweb.org.bo");
        return new SmtpClient(smtpHost(), smtpPort(), from,
                DbConfig.get("mail.smtp.user", null), DbConfig.get("mail.smtp.password", null));
    }
}
