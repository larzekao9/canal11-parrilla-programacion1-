package canal11.programacion.repository;

import canal11.db.Jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/** SQL de parrillas, bloques de parrilla y las consultas auxiliares (materiales, programas, spots) que usa CU3. */
public class ParrillaRepository {

    private static final String LIST_BASE = "SELECT pa.id, pa.fecha, pa.estado, pa.version, "
            + "(SELECT count(*) FROM parrilla_bloques b WHERE b.parrilla_id = pa.id) AS bloques FROM parrillas pa";

    // ------------------------------------------------------------------ parrillas

    public boolean existeFecha(Connection c, LocalDate fecha) throws SQLException {
        return Jdbc.count(c, "SELECT 1 FROM parrillas WHERE fecha = ?", fecha) > 0;
    }

    public boolean existe(Connection c, int id) throws SQLException {
        return Jdbc.one(c, "SELECT id FROM parrillas WHERE id = ?", id) != null;
    }

    public int insert(Connection c, LocalDate fecha, int creadoPor) throws SQLException {
        return Jdbc.insert(c, "INSERT INTO parrillas (fecha, creado_por) VALUES (?, ?) RETURNING id", fecha, creadoPor);
    }

    public List<Map<String, Object>> listEntre(Connection c, LocalDate desde, LocalDate hasta) throws SQLException {
        return Jdbc.query(c, LIST_BASE + " WHERE pa.fecha BETWEEN ? AND ? ORDER BY pa.fecha", desde, hasta);
    }

    public List<Map<String, Object>> listUltimas(Connection c) throws SQLException {
        return Jdbc.query(c, LIST_BASE + " ORDER BY pa.fecha DESC LIMIT 30");
    }

    public Map<String, Object> findById(Connection c, int id) throws SQLException {
        return Jdbc.one(c, "SELECT * FROM parrillas WHERE id = ?", id);
    }

    public Map<String, Object> findByIdForUpdate(Connection c, int id) throws SQLException {
        return Jdbc.one(c, "SELECT * FROM parrillas WHERE id = ? FOR UPDATE", id);
    }

    public void deleteConBloques(Connection c, int id) throws SQLException {
        Jdbc.exec(c, "DELETE FROM parrilla_bloques WHERE parrilla_id = ?", id);
        Jdbc.exec(c, "DELETE FROM parrillas WHERE id = ?", id);
    }

    /** Copia los bloques (sin spots) de una parrilla a otra. Devuelve cuantos copio. */
    public int copiarBloques(Connection c, int destinoId, int origenId) throws SQLException {
        return Jdbc.exec(c, "INSERT INTO parrilla_bloques (parrilla_id, programa_id, material_id, hora_inicio, hora_fin, orden, tipo) "
                + "SELECT ?, programa_id, material_id, hora_inicio, hora_fin, orden, tipo FROM parrilla_bloques WHERE parrilla_id = ?", destinoId, origenId);
    }

    public boolean tieneSpots(Connection c, int parrillaId) throws SQLException {
        return Jdbc.count(c, "SELECT 1 FROM pauta_spots s JOIN parrilla_bloques b ON b.id = s.parrilla_bloque_id WHERE b.parrilla_id = ?", parrillaId) > 0;
    }

    public void publicar(Connection c, int id) throws SQLException {
        Jdbc.exec(c, "UPDATE parrillas SET estado = 'PUBLICADA', publicada_at = now() WHERE id = ?", id);
    }

    public void cerrar(Connection c, int id) throws SQLException {
        Jdbc.exec(c, "UPDATE parrillas SET estado = 'CERRADA' WHERE id = ?", id);
    }

    public void subirVersion(Connection c, int id) throws SQLException {
        Jdbc.exec(c, "UPDATE parrillas SET version = version + 1 WHERE id = ?", id);
    }

    public String snapshot(Connection c, int id) throws SQLException {
        return Jdbc.json(c, "parrillas", id);
    }

    // ------------------------------------------------------------------ bloques

    public List<Map<String, Object>> bloquesConMaterial(Connection c, int parrillaId) throws SQLException {
        return Jdbc.query(c,
                "SELECT b.orden, b.id, b.hora_inicio, b.hora_fin, b.tipo, pr.nombre AS programa, b.material_id, m.estado AS mat_estado "
              + "FROM parrilla_bloques b JOIN programas pr ON pr.id = b.programa_id LEFT JOIN materiales m ON m.id = b.material_id "
              + "WHERE b.parrilla_id = ? ORDER BY b.hora_inicio", parrillaId);
    }

    public Map<String, Object> bloqueById(Connection c, int id) throws SQLException {
        return Jdbc.one(c, "SELECT * FROM parrilla_bloques WHERE id = ?", id);
    }

    public int insertBloque(Connection c, int parrillaId, int programaId, Integer materialId, LocalTime inicio, LocalTime fin, String tipo) throws SQLException {
        return Jdbc.insert(c, "INSERT INTO parrilla_bloques (parrilla_id, programa_id, material_id, hora_inicio, hora_fin, tipo) "
                + "VALUES (?,?,?,?,?,?) RETURNING id", parrillaId, programaId, materialId, inicio, fin, tipo);
    }

    public void updateBloque(Connection c, int id, int programaId, Integer materialId, LocalTime inicio, LocalTime fin, String tipo) throws SQLException {
        Jdbc.exec(c, "UPDATE parrilla_bloques SET programa_id = ?, material_id = ?, hora_inicio = ?, hora_fin = ?, tipo = ? WHERE id = ?",
                programaId, materialId, inicio, fin, tipo, id);
    }

    public void deleteBloque(Connection c, int id) throws SQLException {
        Jdbc.exec(c, "DELETE FROM parrilla_bloques WHERE id = ?", id);
    }

    public boolean bloqueTieneSpots(Connection c, int bloqueId) throws SQLException {
        return Jdbc.count(c, "SELECT 1 FROM pauta_spots WHERE parrilla_bloque_id = ?", bloqueId) > 0;
    }

    /** Spots del bloque que quedarian fuera del horario [inicio, fin]. */
    public long spotsFueraDeHorario(Connection c, int bloqueId, LocalTime inicio, LocalTime fin) throws SQLException {
        return Jdbc.count(c, "SELECT count(*) FROM pauta_spots s JOIN materiales m ON m.id = s.material_id "
                + "WHERE s.parrilla_bloque_id = ? AND (s.hora_programada < ? OR s.hora_programada + (m.duracion_seg * interval '1 second') > ?)",
                bloqueId, inicio, fin);
    }

    /** orden = posicion del bloque segun su hora de inicio. */
    public void renumerar(Connection c, int parrillaId) throws SQLException {
        Jdbc.exec(c, "UPDATE parrilla_bloques b SET orden = s.rn FROM (SELECT id, row_number() OVER (ORDER BY hora_inicio, id) AS rn "
                + "FROM parrilla_bloques WHERE parrilla_id = ?) s WHERE b.id = s.id", parrillaId);
    }

    public List<Map<String, Object>> solapes(Connection c, int parrillaId, int excluirBloque, LocalTime fin, LocalTime inicio) throws SQLException {
        return Jdbc.query(c,
                "SELECT id, hora_inicio, hora_fin FROM parrilla_bloques WHERE parrilla_id = ? AND id <> ? AND hora_inicio < ? AND hora_fin > ?",
                parrillaId, excluirBloque, fin, inicio);
    }

    public String snapshotBloque(Connection c, int id) throws SQLException {
        return Jdbc.json(c, "parrilla_bloques", id);
    }

    /** Bloques con el estado de su material, para validar/publicar. */
    public List<Map<String, Object>> bloquesParaAnalisis(Connection c, int parrillaId) throws SQLException {
        return Jdbc.query(c,
                "SELECT b.id, b.hora_inicio, b.hora_fin, b.tipo, b.material_id, m.estado AS mat_estado, m.activo AS mat_activo "
              + "FROM parrilla_bloques b LEFT JOIN materiales m ON m.id = b.material_id WHERE b.parrilla_id = ? ORDER BY b.hora_inicio, b.id", parrillaId);
    }

    // ------------------------------------------------------------------ referencias

    public Map<String, Object> programaActivo(Connection c, int id) throws SQLException {
        return Jdbc.one(c, "SELECT * FROM programas WHERE id = ? AND activo", id);
    }

    public Map<String, Object> materialActivo(Connection c, int id) throws SQLException {
        return Jdbc.one(c, "SELECT * FROM materiales WHERE id = ? AND activo", id);
    }
}
