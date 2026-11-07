package canal11.comercial.repository;

import canal11.db.Jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** SQL de la tabla clientes (anunciantes). */
public class ClienteRepository {

    public boolean existeNit(Connection c, String nit, Integer excluirId) throws SQLException {
        return excluirId == null
                ? Jdbc.count(c, "SELECT 1 FROM clientes WHERE nit = ?", nit) > 0
                : Jdbc.count(c, "SELECT 1 FROM clientes WHERE nit = ? AND id <> ?", nit, excluirId) > 0;
    }

    public int insert(Connection c, String razonSocial, String nit, String contacto, String email, String tel) throws SQLException {
        return Jdbc.insert(c, "INSERT INTO clientes (razon_social, nit, contacto, email, telefono) VALUES (?,?,?,?,?) RETURNING id",
                razonSocial, nit, contacto, email, tel);
    }

    public List<Map<String, Object>> list(Connection c) throws SQLException {
        return Jdbc.query(c, "SELECT * FROM clientes ORDER BY id");
    }

    public Map<String, Object> findById(Connection c, int id) throws SQLException {
        return Jdbc.one(c, "SELECT * FROM clientes WHERE id = ?", id);
    }

    public Map<String, Object> findByIdForUpdate(Connection c, int id) throws SQLException {
        return Jdbc.one(c, "SELECT * FROM clientes WHERE id = ? FOR UPDATE", id);
    }

    /** sets: fragmentos "columna = ?" generados por el service con nombres de una lista cerrada. */
    public void update(Connection c, int id, List<String> sets, List<Object> vals) throws SQLException {
        List<Object> all = new ArrayList<>(vals);
        all.add(id);
        Jdbc.exec(c, "UPDATE clientes SET " + String.join(", ", sets) + " WHERE id = ?", all.toArray());
    }

    public void desactivar(Connection c, int id) throws SQLException {
        Jdbc.exec(c, "UPDATE clientes SET activo = FALSE WHERE id = ?", id);
    }

    public boolean tienePautasActivas(Connection c, int clienteId) throws SQLException {
        return Jdbc.count(c, "SELECT 1 FROM pautas WHERE cliente_id = ? AND estado = 'ACTIVA'", clienteId) > 0;
    }

    public String snapshot(Connection c, int id) throws SQLException {
        return Jdbc.json(c, "clientes", id);
    }
}
