package canal11.util;

/** Error de negocio o de validacion cuyo mensaje se devuelve tal cual al usuario ("Error: ..."). */
public class CommandException extends RuntimeException {
    public CommandException(String message) {
        super(message);
    }
}
