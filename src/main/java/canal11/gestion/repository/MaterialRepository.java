package canal11.gestion.repository;

import canal11.db.Jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** SQL de la tabla materiales y de sus referencias (programas, bloques de parrilla, spots). */
public class MaterialRepository {

    private static final String LIST_BASE =
            "SELECT id, titulo, tipo, duracion_seg, formato, estado, programa_id, activo FROM materiales WHERE activo";

    public boolean programaActivoExiste(Connection c, int programaId) throws SQLException {
        return Jdbc.count(c, "SELECT 1 FROM programas WHERE id = ? AND activo", programaId) > 0;
    }

    /** Id de otro material con el mismo hash, o null. */
    public Object idConHash(Connection c, String hash, int exceptId) throws SQLException {
        Map<String, Object> dup = Jdbc.one(c, "SELECT id FROM materiales WHERE hash_sha256 = ? AND id <> ?", hash, exceptId);
        return dup == null ? null : dup.get("id");
    }

    public int insert(Connection c, Integer programaId, String titulo, String tipo, int dur, String formato,
                      String ruta, String hash, int registradoPor) throws SQLException {
        return Jdbc.insert(c, "INSERT INTO materiales (programa_id, titulo, tipo, duracion_seg, formato, ruta_archivo, hash_sha256, registrado_por) "
                + "VALUES (?,?,?,?,?,?,?,?) RETURNING id", programaId, titulo, tipo, dur, formato, ruta, hash, registradoPor);
    }

    public List<Map<String, Object>> listActivos(Connection c) throws SQLException {
        return Jdbc.query(c, LIST_BASE + " ORDER BY id");
    }

    public List<Map<String, Object>> listByEstado(Connection c, String estado) throws SQLException {
        return Jdbc.query(c, LIST_BASE + " AND estado = ? ORDER BY id", estado);
    }

    public List<Map<String, Object>> listByTipo(Connection c, String tipo) throws SQLException {
        return Jdbc.query(c, LIST_BASE + " AND tipo = ? ORDER BY id", tipo);
    }

    public Map<String, Object> findById(Connection c, int id) throws SQLException {
        return Jdbc.one(c, "SELECT * FROM materiales WHERE id = ?", id);
    }

    public Map<String, Object> findByIdForUpdate(Connection c, int id) throws SQLException {
        return Jdbc.one(c, "SELECT * FROM materiales WHERE id = ? FOR UPDATE", id);
    }

    public boolean enBloques(Connection c, int id) throws SQLException {
        return Jdbc.count(c, "SELECT 1 FROM parrilla_bloques WHERE material_id = ?", id) > 0;
    }

    public boolean enSpots(Connection c, int id) throws SQLException {
        return Jdbc.count(c, "SELECT 1 FROM pauta_spots WHERE material_id = ?", id) > 0;
    }

    public boolean enParrillaVigente(Connection c, int id) throws SQLException {
        return Jdbc.count(c, "SELECT count(*) FROM parrilla_bloques b JOIN parrillas pa ON pa.id = b.parrilla_id "
                + "WHERE b.material_id = ? AND pa.estado IN ('BORRADOR','PUBLICADA')", id) > 0;
    }

    public boolean tieneSpotsProgramados(Connection c, int id) throws SQLException {
        return Jdbc.count(c, "SELECT 1 FROM pauta_spots WHERE material_id = ? AND estado = 'PROGRAMADO'", id) > 0;
    }

    /** sets: fragmentos "columna = ?" o fijos, generados por el service con nombres de una lista cerrada. */
    public void update(Connection c, int id, List<String> sets, List<Object> vals) throws SQLException {
        List<Object> all = new ArrayList<>(vals);
        all.add(id);
        Jdbc.exec(c, "UPDATE materiales SET " + String.join(", ", sets) + " WHERE id = ?", all.toArray());
    }

    public void darDeBaja(Connection c, int id) throws SQLException {
        Jdbc.exec(c, "UPDATE materiales SET activo = FALSE WHERE id = ?", id);
    }

    public String snapshot(Connection c, int id) throws SQLException {
        return Jdbc.json(c, "materiales", id);
    }
}
