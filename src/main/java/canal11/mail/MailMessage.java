package canal11.mail;

/** Correo entrante. id identifica el mensaje en el buzon (para borrarlo). */
public record MailMessage(String id, String from, String subject, String body) {}
