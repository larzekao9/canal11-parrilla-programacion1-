package canal11.model;

public record Usuario(int id, String ci, String nombre, String apellido, String rol,
                      String telefono, String email, String passwordHash, boolean activo) {

    public static final String ADMIN = "administrador";
    public static final String PROGRAMADOR = "programador";
    public static final String ENCARGADO = "encargado";
}
