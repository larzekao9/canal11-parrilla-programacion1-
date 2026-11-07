package canal11.emision.service;

import canal11.db.DbConnection;
import canal11.db.Jdbc;
import canal11.emision.repository.AuditoriaRepository;
import canal11.mail.CommandSpec;
import canal11.mail.CommandRouter;
import canal11.model.Usuario;
import canal11.util.Args;
import canal11.util.CommandException;
import canal11.util.InputValidator;
import canal11.util.TextTable;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Servicio transversal de auditoria (CU7 / RF-29). Solo inserta en auditoria_logs (la tabla es de solo insercion).
 * - registrar(Connection, ...) participa de la transaccion del comando (atomico con el cambio auditado).
 * - registrar(...) sin conexion usa la suya; un fallo se informa por stderr sin tumbar el comando (AUTH, etc.).
 * Clase no final para poder sustituirla en tests.
 */
public class AuditoriaService {

    private static final int MAX_FILAS = 100;
    private final AuditoriaRepository repo = new AuditoriaRepository();

    /**
     * @param antes   JSON (texto) del estado previo o null
     * @param despues JSON (texto) del estado posterior o null
     */
    public void registrar(Integer userId, String accion, String entidad, Integer entidadId,
                          String antes, String despues, String comando) {
        try (Connection c = DbConnection.open()) {
            registrar(c, userId, accion, entidad, entidadId, antes, despues, comando);
        } catch (SQLException e) {
            System.err.println("[auditoria] no se pudo registrar " + accion + ": " + e.getMessage());
        }
    }

    public void registrar(Connection c, Integer userId, String accion, String entidad, Integer entidadId,
                          String antes, String despues, String comando) throws SQLException {
        repo.insert(c, userId, accion, entidad, entidadId, antes, despues, comando);
    }

    // ------------------------------------------------------------------ consultas (solo administrador)

    public void registerAll(CommandRouter r) {
        r.register(new CommandSpec("LISAUD", "LISAUD[\"desde\",\"hasta\"] | [usuario_id] | [\"entidad\"] | [\"*\"]",
                "Lista la auditoria", CommandSpec.ADMIN, this::listar));
        r.register(new CommandSpec("GETAUD", "GETAUD[\"entidad\",id]",
                "Historial de un registro", CommandSpec.ADMIN, this::historial));
    }

    String listar(Usuario u, List<String> p) {
        Args.count(p, 1, 2, "LISAUD[\"desde\",\"hasta\"] | LISAUD[usuario_id] | LISAUD[\"entidad\"] | LISAUD[\"*\"]");
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (p.size() == 2) {
            LocalDate d = InputValidator.validateDate(p.get(0), "desde");
            LocalDate h = InputValidator.validateDate(p.get(1), "hasta");
            if (h.isBefore(d)) throw new CommandException("hasta no puede ser anterior a desde");
            where.append(" AND a.created_at >= ? AND a.created_at < ?");
            params.add(d.atStartOfDay());
            params.add(h.plusDays(1).atStartOfDay());
        } else if (!Args.isAll(p)) {
            String v = p.get(0).trim();
            if (v.matches("\\d+")) {
                where.append(" AND a.user_id = ?");
                params.add(Integer.parseInt(v));
            } else {
                where.append(" AND a.entidad = ?");
                params.add(InputValidator.validateText(v, "entidad", 40).toLowerCase());
            }
        }
        return Jdbc.tx(c -> {
            List<Map<String, Object>> rows = repo.listar(c, where.toString(), params, MAX_FILAS);
            registrar(c, u.id(), "CONSULTA_AUDITORIA", "auditoria_logs", null, null, null, "LISAUD");
            return "Auditoria (ultimos " + MAX_FILAS + " como maximo):\n"
                    + TextTable.render(rows, "id", "fecha", "usuario", "accion", "entidad", "entidad_id", "comando");
        });
    }

    String historial(Usuario u, List<String> p) {
        Args.exact(p, 2, "GETAUD[\"entidad\",id]");
        String entidad = InputValidator.validateText(p.get(0), "entidad", 40).toLowerCase();
        int id = InputValidator.validateId(p.get(1), "id");
        return Jdbc.tx(c -> {
            List<Map<String, Object>> rows = repo.historial(c, entidad, id);
            if (rows.isEmpty()) return "(sin historial para " + entidad + " " + id + ")";
            StringBuilder sb = new StringBuilder("Historial de " + entidad + " " + id + ":\n");
            for (Map<String, Object> r : rows) {
                sb.append("#").append(r.get("id")).append(' ').append(TextTable.fmt(r.get("fecha")))
                  .append(" | ").append(r.get("usuario")).append(" | ").append(r.get("accion")).append('\n');
                sb.append("   antes:   ").append(TextTable.fmt(r.get("antes"))).append('\n');
                sb.append("   despues: ").append(TextTable.fmt(r.get("despues"))).append('\n');
            }
            return sb.toString().stripTrailing();
        });
    }
}
