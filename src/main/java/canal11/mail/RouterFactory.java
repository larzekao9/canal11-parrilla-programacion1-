package canal11.mail;

import canal11.emision.AuditoriaService;
import canal11.gestion.UsuarioRepository;

/**
 * Punto unico de cableado de la aplicacion real. Cada fase agrega aqui el registro de sus comandos
 * (ej. Fase 2: new UsuarioCommands(...).registerAll(router)).
 */
public final class RouterFactory {

    private RouterFactory() {}

    public static CommandRouter create() {
        AuditoriaService auditoria = new AuditoriaService();
        CommandRouter router = new CommandRouter(new UsuarioRepository(), new JdbcSessionStore(), auditoria);
        // Fase 2+: registrar comandos de cada CU aqui.
        return router;
    }
}
