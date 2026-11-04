package canal11.mail;

/** Destino de respuestas. Implementaciones: FileMailbox (simulado) y, mas adelante, SmtpClient. */
public interface MailSender {
    void send(String to, String subject, String body) throws Exception;
}
