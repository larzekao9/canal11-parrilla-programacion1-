package canal11.gestion.repository;

import canal11.db.Jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/** SQL de la tabla programas. Recibe la conexion de la transaccion que abre el service. */
public class ProgramaRepository {

    public boolean existeNombreTipo(Connection c, String nombre, String tipo, Integer excluirId) throws SQLException {
        return excluirId == null
                ? Jdbc.count(c, "SELECT 1 FROM programas WHERE lower(nombre) = lower(?) AND tipo = ?", nombre, tipo) > 0
                : Jdbc.count(c, "SELECT 1 FROM programas WHERE lower(nombre) = lower(?) AND tipo = ? AND id <> ?", nombre, tipo, excluirId) > 0;
    }

    public int insert(Connection c, String nombre, String desc, String tipo, int dur, String genero, String clasif) throws SQLException {
        return Jdbc.insert(c, "INSERT INTO programas (nombre, descripcion, tipo, duracion_min, genero, clasificacion) "
                + "VALUES (?,?,?,?,?,?) RETURNING id", nombre, desc, tipo, dur, genero, clasif);
    }

    public List<Map<String, Object>> list(Connection c) throws SQLException {
        return Jdbc.query(c, "SELECT * FROM programas ORDER BY id");
    }

    public List<Map<String, Object>> listByTipo(Connection c, String tipo) throws SQLException {
        return Jdbc.query(c, "SELECT * FROM programas WHERE tipo = ? ORDER BY id", tipo);
    }

    public Map<String, Object> findById(Connection c, int id) throws SQLException {
        return Jdbc.one(c, "SELECT * FROM programas WHERE id = ?", id);
    }

    public Map<String, Object> findByIdForUpdate(Connection c, int id) throws SQLException {
        return Jdbc.one(c, "SELECT * FROM programas WHERE id = ? FOR UPDATE", id);
    }

    /** sets: fragmentos "columna = ?" ya validados por el service (nombres de una lista cerrada). */
    public void update(Connection c, int id, List<String> sets, List<Object> vals) throws SQLException {
        List<Object> all = new java.util.ArrayList<>(vals);
        all.add(id);
        Jdbc.exec(c, "UPDATE programas SET " + String.join(", ", sets) + " WHERE id = ?", all.toArray());
    }

    public void desactivar(Connection c, int id) throws SQLException {
        Jdbc.exec(c, "UPDATE programas SET activo = FALSE WHERE id = ?", id);
    }

    /** Bloques de parrillas borrador/publicadas de hoy en adelante que usan el programa. */
    public long bloquesVigentes(Connection c, int programaId) throws SQLException {
        return Jdbc.count(c, "SELECT count(*) FROM parrilla_bloques b JOIN parrillas pa ON pa.id = b.parrilla_id "
                + "WHERE b.programa_id = ? AND pa.estado IN ('BORRADOR','PUBLICADA') AND pa.fecha >= CURRENT_DATE", programaId);
    }

    public String snapshot(Connection c, int id) throws SQLException {
        return Jdbc.json(c, "programas", id);
    }
}
