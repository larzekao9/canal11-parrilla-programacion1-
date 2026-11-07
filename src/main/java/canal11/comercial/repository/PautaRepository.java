package canal11.comercial.repository;

import canal11.db.Jdbc;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** SQL de pautas publicitarias y spots comerciales (pautas, pauta_spots y sus joins con bloques/materiales). */
public class PautaRepository {

    private static final String SELECT_PAUTA = "SELECT pt.id, cl.razon_social AS cliente, pt.nombre, pt.fecha_inicio, pt.fecha_fin, pt.tarifa_spot, pt.max_pases, "
            + "(SELECT count(*) FROM pauta_spots s WHERE s.pauta_id = pt.id) AS usados, pt.estado "
            + "FROM pautas pt JOIN clientes cl ON cl.id = pt.cliente_id";

    // ------------------------------------------------------------------ pautas

    public boolean clienteActivoExiste(Connection c, int clienteId) throws SQLException {
        return Jdbc.count(c, "SELECT 1 FROM clientes WHERE id = ? AND activo", clienteId) > 0;
    }

    public int insert(Connection c, int clienteId, String nombre, LocalDate fi, LocalDate ff, BigDecimal tarifa, int max, int creadoPor) throws SQLException {
        return Jdbc.insert(c, "INSERT INTO pautas (cliente_id, nombre, fecha_inicio, fecha_fin, tarifa_spot, max_pases, creado_por) "
                + "VALUES (?,?,?,?,?,?,?) RETURNING id", clienteId, nombre, fi, ff, tarifa, max, creadoPor);
    }

    public List<Map<String, Object>> list(Connection c) throws SQLException {
        return Jdbc.query(c, SELECT_PAUTA + " ORDER BY pt.id");
    }

    public List<Map<String, Object>> listByCliente(Connection c, int clienteId) throws SQLException {
        return Jdbc.query(c, SELECT_PAUTA + " WHERE pt.cliente_id = ? ORDER BY pt.id", clienteId);
    }

    /** Pauta con nombre del cliente y spots usados. */
    public Map<String, Object> findDetalleById(Connection c, int id) throws SQLException {
        return Jdbc.one(c, SELECT_PAUTA + " WHERE pt.id = ?", id);
    }

    public Map<String, Object> findByIdForUpdate(Connection c, int id) throws SQLException {
        return Jdbc.one(c, "SELECT * FROM pautas WHERE id = ? FOR UPDATE", id);
    }

    public boolean existe(Connection c, int id) throws SQLException {
        return Jdbc.one(c, "SELECT id FROM pautas WHERE id = ?", id) != null;
    }

    public long contarSpots(Connection c, int pautaId) throws SQLException {
        return Jdbc.count(c, "SELECT count(*) FROM pauta_spots WHERE pauta_id = ?", pautaId);
    }

    public boolean haySpotsFueraDeVigencia(Connection c, int pautaId, LocalDate fi, LocalDate ff) throws SQLException {
        return Jdbc.count(c, "SELECT count(*) FROM pauta_spots s JOIN parrilla_bloques b ON b.id = s.parrilla_bloque_id "
                + "JOIN parrillas pa ON pa.id = b.parrilla_id WHERE s.pauta_id = ? AND (pa.fecha < ? OR pa.fecha > ?)", pautaId, fi, ff) > 0;
    }

    /** sets: fragmentos "columna = ?" generados por el service con nombres de una lista cerrada. */
    public void update(Connection c, int id, List<String> sets, List<Object> vals) throws SQLException {
        List<Object> all = new ArrayList<>(vals);
        all.add(id);
        Jdbc.exec(c, "UPDATE pautas SET " + String.join(", ", sets) + " WHERE id = ?", all.toArray());
    }

    public void delete(Connection c, int id) throws SQLException {
        Jdbc.exec(c, "DELETE FROM pautas WHERE id = ?", id);
    }

    public String snapshot(Connection c, int id) throws SQLException {
        return Jdbc.json(c, "pautas", id);
    }

    // ------------------------------------------------------------------ spots

    /** Bloque con fecha y estado de su parrilla, bloqueado para insertar spots. */
    public Map<String, Object> bloqueForUpdate(Connection c, int bloqueId) throws SQLException {
        return Jdbc.one(c, "SELECT b.*, pa.fecha, pa.estado AS parrilla_estado FROM parrilla_bloques b "
                + "JOIN parrillas pa ON pa.id = b.parrilla_id WHERE b.id = ? FOR UPDATE OF b", bloqueId);
    }

    public Map<String, Object> materialActivo(Connection c, int materialId) throws SQLException {
        return Jdbc.one(c, "SELECT * FROM materiales WHERE id = ? AND activo", materialId);
    }

    /** Spots ya colocados en un bloque, con la duracion de su material. */
    public List<Map<String, Object>> spotsDeBloque(Connection c, int bloqueId) throws SQLException {
        return Jdbc.query(c, "SELECT s.id, s.hora_programada, m.duracion_seg FROM pauta_spots s JOIN materiales m ON m.id = s.material_id "
                + "WHERE s.parrilla_bloque_id = ?", bloqueId);
    }

    public int insertSpot(Connection c, int pautaId, int materialId, int bloqueId, LocalTime hora) throws SQLException {
        return Jdbc.insert(c, "INSERT INTO pauta_spots (pauta_id, material_id, parrilla_bloque_id, hora_programada) VALUES (?,?,?,?) RETURNING id",
                pautaId, materialId, bloqueId, hora);
    }

    /** Spot con el estado de su parrilla, bloqueado para eliminarlo. */
    public Map<String, Object> spotForUpdate(Connection c, int id) throws SQLException {
        return Jdbc.one(c, "SELECT s.*, pa.estado AS parrilla_estado FROM pauta_spots s JOIN parrilla_bloques b ON b.id = s.parrilla_bloque_id "
                + "JOIN parrillas pa ON pa.id = b.parrilla_id WHERE s.id = ? FOR UPDATE OF s", id);
    }

    public void deleteSpot(Connection c, int id) throws SQLException {
        Jdbc.exec(c, "DELETE FROM pauta_spots WHERE id = ?", id);
    }

    public String snapshotSpot(Connection c, int id) throws SQLException {
        return Jdbc.json(c, "pauta_spots", id);
    }

    public List<Map<String, Object>> spotsDePauta(Connection c, int pautaId) throws SQLException {
        return Jdbc.query(c, "SELECT s.id, pa.fecha, s.hora_programada AS hora, m.titulo, m.duracion_seg AS seg, s.estado, b.id AS bloque_id "
                + "FROM pauta_spots s JOIN materiales m ON m.id = s.material_id JOIN parrilla_bloques b ON b.id = s.parrilla_bloque_id "
                + "JOIN parrillas pa ON pa.id = b.parrilla_id WHERE s.pauta_id = ? ORDER BY pa.fecha, s.hora_programada", pautaId);
    }

    // ------------------------------------------------------------------ pauta del dia

    public Map<String, Object> parrillaById(Connection c, int id) throws SQLException {
        return Jdbc.one(c, "SELECT * FROM parrillas WHERE id = ?", id);
    }

    public List<Map<String, Object>> pausasComerciales(Connection c, int parrillaId) throws SQLException {
        return Jdbc.query(c, "SELECT id, hora_inicio, hora_fin, EXTRACT(EPOCH FROM (hora_fin - hora_inicio))::int AS cap "
                + "FROM parrilla_bloques WHERE parrilla_id = ? AND tipo = 'pausa_comercial' ORDER BY hora_inicio", parrillaId);
    }

    public List<Map<String, Object>> spotsConClienteDeBloque(Connection c, Object bloqueId) throws SQLException {
        return Jdbc.query(c, "SELECT s.id, s.hora_programada AS hora, cl.razon_social AS cliente, m.titulo, m.duracion_seg AS seg, s.estado "
                + "FROM pauta_spots s JOIN pautas pt ON pt.id = s.pauta_id JOIN clientes cl ON cl.id = pt.cliente_id "
                + "JOIN materiales m ON m.id = s.material_id WHERE s.parrilla_bloque_id = ? ORDER BY s.hora_programada", bloqueId);
    }
}
