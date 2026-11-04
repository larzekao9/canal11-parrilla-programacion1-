package canal11.mail;

import canal11.emision.AuditoriaService;
import canal11.gestion.UserLookup;
import canal11.model.Usuario;
import canal11.util.CommandException;
import org.mindrot.jbcrypt.BCrypt;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Orquesta: subject -> usuario (por email) -> comando -> permiso por rol -> sesion AUTH -> handler.
 * AUTH y HELP son propios del router; el resto de comandos los registra cada CU con register().
 * process() nunca lanza: siempre devuelve el texto a responder ("OK: ..." / "Error: ...").
 */
public class CommandRouter {

    public static final int SESION_MINUTOS = 30;

    private final UserLookup users;
    private final SessionStore sessions;
    private final AuditoriaService auditoria;
    private final Map<String, CommandSpec> commands = new TreeMap<>();

    public CommandRouter(UserLookup users, SessionStore sessions, AuditoriaService auditoria) {
        this.users = users;
        this.sessions = sessions;
        this.auditoria = auditoria;
    }

    public void register(CommandSpec spec) {
        commands.put(spec.name().toUpperCase(), spec);
    }

    public String process(String subject, String senderEmail) {
        try {
            return doProcess(subject, senderEmail);
        } catch (CommandException e) {
            return "Error: " + e.getMessage();
        } catch (Exception e) {
            System.err.println("[router] error interno procesando '" + sanitize(subject) + "': " + e);
            return "Error: ocurrio un problema interno al procesar el comando.";
        }
    }

    private String doProcess(String subject, String senderEmail) throws Exception {
        if (!SubjectParser.esValido(subject)) return "Comando no reconocido. Use HELP[].";
        String cmd = SubjectParser.extraerComando(subject);
        List<String> params = SubjectParser.extraerParametros(subject);

        Optional<Usuario> found = senderEmail == null ? Optional.empty() : users.findByEmail(senderEmail.trim());
        if (found.isEmpty()) return "Error: correo no registrado en el sistema.";
        Usuario user = found.get();
        if (!user.activo()) return "Error: usuario inactivo.";

        boolean isAuth = cmd.equals("AUTH");
        boolean isHelp = cmd.equals("HELP");
        CommandSpec spec = commands.get(cmd);
        if (!isAuth && !isHelp && spec == null) return "Comando no reconocido. Use HELP[].";

        if (isAuth) return auth(user, params, subject);
        if (isHelp) return help(user);

        if (!spec.roles().contains(user.rol())) {
            return "Error: Permiso denegado para rol: " + user.rol() + ". Use HELP[].";
        }
        if (!sessions.isActive(user.id())) {
            return "Error: Autenticacion requerida. Envie AUTH[\"password\"] para habilitar "
                    + SESION_MINUTOS + " minutos.";
        }
        return spec.handler().handle(user, params);
    }

    private String auth(Usuario user, List<String> params, String subject) {
        if (params.size() != 1 || params.get(0).isEmpty()) {
            throw new CommandException("Uso: AUTH[\"password\"]");
        }
        boolean ok;
        try {
            ok = BCrypt.checkpw(params.get(0), user.passwordHash());
        } catch (IllegalArgumentException e) { // hash corrupto
            ok = false;
        }
        if (!ok) {
            auditoria.registrar(user.id(), "AUTH_FALLIDO", "users", user.id(), null, null, "AUTH[\"***\"]");
            throw new CommandException("Credenciales invalidas.");
        }
        sessions.open(user.id(), SESION_MINUTOS);
        auditoria.registrar(user.id(), "AUTH", "users", user.id(), null, null, "AUTH[\"***\"]");
        return "OK: sesion habilitada por " + SESION_MINUTOS + " minutos. Hola " + user.nombre() + " (" + user.rol() + ").";
    }

    private String help(Usuario user) {
        StringBuilder sb = new StringBuilder("Comandos disponibles para el rol ").append(user.rol()).append(":\n");
        sb.append(String.format("  %-34s %s%n", "AUTH[\"password\"]", "Habilita la sesion por " + SESION_MINUTOS + " min"));
        sb.append(String.format("  %-34s %s%n", "HELP[]", "Muestra esta ayuda"));
        String lista = commands.values().stream()
                .filter(s -> s.roles().contains(user.rol()))
                .map(s -> String.format("  %-34s %s", s.usage(), s.description()))
                .collect(Collectors.joining("\n"));
        if (!lista.isEmpty()) sb.append(lista).append("\n");
        return sb.toString().stripTrailing();
    }

    /** Evita volcar passwords a logs. */
    private static String sanitize(String subject) {
        if (subject == null) return "";
        String s = subject.length() > 80 ? subject.substring(0, 80) + "..." : subject;
        return SubjectParser.extraerComando(subject).equals("AUTH") ? "AUTH[***]" : s;
    }
}
