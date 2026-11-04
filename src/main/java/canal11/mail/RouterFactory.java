package canal11.mail;

import canal11.emision.AuditoriaService;
import canal11.gestion.UsuarioRepository;
import canal11.gestion.ProgramaService;
import canal11.gestion.UsuarioService;

/**
 * Punto unico de cableado de la aplicacion real. Cada fase agrega aqui el registro de sus comandos
 * (ej. Fase 2: new UsuarioCommands(...).registerAll(router)).
 */
public final class RouterFactory {

    private RouterFactory() {}

    public static CommandRouter create() {
        return create(new JdbcSessionStore());
    }

    /** Variante con almacen de sesiones inyectable (tests). */
    public static CommandRouter create(SessionStore sessions) {
        AuditoriaService auditoria = new AuditoriaService();
        CommandRouter router = new CommandRouter(new UsuarioRepository(), sessions, auditoria);
        new UsuarioService(auditoria).registerAll(router);   // CU1
        auditoria.registerAll(router);                        // LISAUD / GETAUD (CU7, consulta)
        new ProgramaService(auditoria).registerAll(router);   // CU2
        // Resto de CU: registrar aqui.
        return router;
    }
}
