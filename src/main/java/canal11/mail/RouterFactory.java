package canal11.mail;

import canal11.emision.service.AuditoriaService;
import canal11.gestion.repository.UsuarioRepository;
import canal11.comercial.service.ClienteService;
import canal11.comercial.service.PautaService;
import canal11.emision.service.EmisionService;
import canal11.gestion.service.MaterialService;
import canal11.reporte.service.ReporteService;
import canal11.gestion.service.ProgramaService;
import canal11.gestion.service.ValidacionService;
import canal11.programacion.service.ParrillaService;
import canal11.gestion.service.UsuarioService;

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
        new MaterialService(auditoria).registerAll(router);   // CU4
        new ValidacionService(auditoria).registerAll(router); // CU5
        new ParrillaService(auditoria).registerAll(router);   // CU3
        new ClienteService(auditoria).registerAll(router);    // CU6
        new PautaService(auditoria).registerAll(router);      // CU6
        new EmisionService(auditoria).registerAll(router);    // CU7
        new ReporteService(auditoria).registerAll(router);    // CU8
        return router;
    }
}
