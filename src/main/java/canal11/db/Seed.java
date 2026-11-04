package canal11.db;

import java.sql.Connection;
import java.sql.SQLException;

/** Datos iniciales del catalogo (idempotente). "Pausa Comercial" es el programa que usan los bloques de pausa. */
public final class Seed {

    private Seed() {}

    public static int ensureProgramas(Connection c) throws SQLException {
        String[][] base = {
            {"Noticiero Central", "Edicion principal del noticiero", "noticiero", "60", "informativo", "TP"},
            {"Revista Matinal", "Revista de la manana", "revista", "90", "variedades", "TP"},
            {"Pausa Comercial", "Bloque de publicidad", "otro", "5", "publicidad", "TP"},
        };
        int n = 0;
        for (String[] p : base) {
            n += Jdbc.exec(c, "INSERT INTO programas (nombre, descripcion, tipo, duracion_min, genero, clasificacion) "
                    + "SELECT ?,?,?,?,?,? WHERE NOT EXISTS (SELECT 1 FROM programas WHERE lower(nombre) = lower(?) AND tipo = ?)",
                    p[0], p[1], p[2], Integer.parseInt(p[3]), p[4], p[5], p[0], p[2]);
        }
        return n;
    }
}
