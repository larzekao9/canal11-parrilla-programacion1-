package canal11.mail;

import canal11.model.Usuario;

import java.util.List;
import java.util.Set;

/** Definicion de un comando: quien puede usarlo y que hace. Cada CU registra los suyos en el router. */
public record CommandSpec(String name, String usage, String description, Set<String> roles, Handler handler) {

    @FunctionalInterface
    public interface Handler {
        /** Devuelve el texto de respuesta. Los errores de negocio se lanzan como CommandException. */
        String handle(Usuario user, List<String> params) throws Exception;
    }

    public static final Set<String> TODOS = Set.of(Usuario.ADMIN, Usuario.PROGRAMADOR, Usuario.ENCARGADO);
    public static final Set<String> ADMIN = Set.of(Usuario.ADMIN);
    public static final Set<String> ADMIN_PROG = Set.of(Usuario.ADMIN, Usuario.PROGRAMADOR);
    public static final Set<String> ADMIN_ENC = Set.of(Usuario.ADMIN, Usuario.ENCARGADO);
}
