package canal11.mail;

import canal11.db.DbConfig;

import java.nio.file.Path;
import java.util.List;

/**
 * Bucle principal: leer correos -> procesar -> responder -> borrar -> dormir.
 * Un error en un correo (o en el buzon) nunca detiene el bucle.
 */
public class MailDaemon {

    private final MailReader reader;
    private final MailSender sender;
    private final CommandRouter router;

    public MailDaemon(MailReader reader, MailSender sender, CommandRouter router) {
        this.reader = reader;
        this.sender = sender;
        this.router = router;
    }

    /** Procesa todos los correos pendientes una vez. Devuelve cuantos proceso. */
    public int processOnce() {
        List<MailMessage> messages;
        try {
            messages = reader.fetchAll();
        } catch (Exception e) {
            System.err.println("[daemon] no se pudo leer el buzon: " + e);
            return 0;
        }
        for (MailMessage m : messages) {
            String respuesta;
            try {
                respuesta = router.process(m.subject(), m.from());
            } catch (Throwable t) { // router ya captura todo; red de seguridad
                respuesta = "Error: ocurrio un problema interno.";
                System.err.println("[daemon] fallo inesperado: " + t);
            }
            try {
                sender.send(m.from(), "RE: " + m.subject(), respuesta);
            } catch (Exception e) {
                System.err.println("[daemon] no se pudo responder a " + m.from() + ": " + e);
            }
            try {
                reader.delete(m);
            } catch (Exception e) {
                System.err.println("[daemon] no se pudo borrar " + m.id() + ": " + e);
            }
        }
        return messages.size();
    }

    public void run(long intervalMs) throws InterruptedException {
        System.out.println("[daemon] iniciado; revisando cada " + intervalMs + " ms. Ctrl+C para detener.");
        while (true) {
            int n = processOnce();
            if (n > 0) System.out.println("[daemon] procesados " + n + " correo(s)");
            Thread.sleep(intervalMs);
        }
    }

    public static void main(String[] args) throws Exception {
        FileMailbox mailbox = new FileMailbox(Path.of(DbConfig.get("mail.simulado.dir", "buzon-simulado")));
        CommandRouter router = RouterFactory.create();
        long interval = Long.parseLong(DbConfig.get("mail.intervalo.ms", "10000"));
        new MailDaemon(mailbox, mailbox, router).run(interval);
    }
}
