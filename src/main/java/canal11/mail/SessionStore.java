package canal11.mail;

/** Sesiones AUTH (ventana de 30 min). */
public interface SessionStore {
    boolean isActive(int userId);

    /** Abre o renueva la sesion; devuelve los minutos de validez. */
    void open(int userId, int minutes);
}
