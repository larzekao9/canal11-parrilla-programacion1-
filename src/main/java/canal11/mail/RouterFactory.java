package canal11.mail;

import canal11.emision.AuditoriaService;
import canal11.gestion.UsuarioRepository;
import canal11.gestion.UsuarioService;

/**
 * Punto unico de cableado de la aplicacion real. Cada fase agrega aqui el registro de sus comandos
 * (ej. Fase 2: new UsuarioCommands(...).registerAll(router)).
 */
public final class RouterFactory {

    private RouterFactory() {}

    public static CommandRouter create() {
        AuditoriaService auditoria = new AuditoriaService();
        CommandRouter router = new CommandRouter(new UsuarioRepository(), new JdbcSessionStore(), auditoria);
        new UsuarioService(auditoria).registerAll(router);   // CU1
        auditoria.registerAll(router);                        // LISAUD / GETAUD (CU7, consulta)
        // Resto de CU: registrar aqui.
        return router;
    }
}
