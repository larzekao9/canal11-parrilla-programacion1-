package canal11.mail;

import java.util.List;

/** Origen de correos. Implementaciones: FileMailbox (simulado) y, mas adelante, Pop3Client. */
public interface MailReader {
    List<MailMessage> fetchAll() throws Exception;

    void delete(MailMessage message) throws Exception;
}
