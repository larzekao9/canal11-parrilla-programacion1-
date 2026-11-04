package canal11.comercial;

import canal11.db.Jdbc;
import canal11.emision.AuditoriaService;
import canal11.mail.CommandRouter;
import canal11.mail.CommandSpec;
import canal11.model.Usuario;
import canal11.util.Args;
import canal11.util.CommandException;
import canal11.util.InputValidator;
import canal11.util.TextTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** CU6 (parte 1) - Clientes / anunciantes. DELCLI desactiva y se bloquea si tiene pautas activas. */
public class ClienteService {

    static final Set<String> CAMPOS = Set.of("razon_social", "nit", "contacto", "email", "telefono", "activo");
    private static final String[] COLS = {"id", "razon_social", "nit", "contacto", "email", "telefono", "activo"};

    private final AuditoriaService auditoria;

    public ClienteService(AuditoriaService auditoria) {
        this.auditoria = auditoria;
    }

    public void registerAll(CommandRouter r) {
        var W = CommandSpec.ADMIN_PROG;
        var R = CommandSpec.TODOS;
        r.register(new CommandSpec("INSCLI", "INSCLI[\"razon_social\",\"nit\",\"contacto\",\"email\",\"telefono\"]", "Registra un anunciante", W, this::insert));
        r.register(new CommandSpec("LISCLI", "LISCLI[\"*\"]", "Lista anunciantes", R, this::list));
        r.register(new CommandSpec("GETCLI", "GETCLI[id]", "Detalle de un anunciante", R, this::get));
        r.register(new CommandSpec("UPDCLI", "UPDCLI[id,\"campo=valor\",...]", "Actualiza un anunciante", W, this::update));
        r.register(new CommandSpec("DELCLI", "DELCLI[id]", "Desactiva un anunciante", W, this::delete));
    }

    String insert(Usuario actor, List<String> p) {
        Args.exact(p, 5, "INSCLI[\"razon_social\",\"nit\",\"contacto\",\"email\",\"telefono\"]");
        String rs = InputValidator.validateText(p.get(0), "razon_social", 160);
        String nit = nit(p.get(1));
        String contacto = opt(p.get(2), 120, "contacto");
        String email = p.get(3).isBlank() ? null : InputValidator.validateEmail(p.get(3), "email");
        String tel = telefono(p.get(4));
        return Jdbc.tx(c -> {
            if (Jdbc.count(c, "SELECT 1 FROM clientes WHERE nit = ?", nit) > 0) throw new CommandException("Ya existe un cliente con ese NIT.");
            int id = Jdbc.insert(c, "INSERT INTO clientes (razon_social, nit, contacto, email, telefono) VALUES (?,?,?,?,?) RETURNING id",
                    rs, nit, contacto, email, tel);
            auditoria.registrar(c, actor.id(), "CLIENTE_CREAR", "clientes", id, null, Jdbc.json(c, "clientes", id), Args.cmdText("INSCLI", p));
            return "OK: cliente creado con ID " + id;
        });
    }

    String list(Usuario actor, List<String> p) {
        Args.exact(p, 1, "LISCLI[\"*\"]");
        return Jdbc.tx(c -> {
            List<Map<String, Object>> rows = Jdbc.query(c, "SELECT * FROM clientes ORDER BY id");
            return "Listando clientes (" + rows.size() + "):\n" + TextTable.render(rows, COLS);
        });
    }

    String get(Usuario actor, List<String> p) {
        Args.exact(p, 1, "GETCLI[id]");
        int id = InputValidator.validateId(p.get(0), "id");
        return Jdbc.tx(c -> {
            Map<String, Object> r = Jdbc.one(c, "SELECT * FROM clientes WHERE id = ?", id);
            if (r == null) throw new CommandException("Cliente no encontrado: " + id);
            return TextTable.detail(r, COLS);
        });
    }

    String update(Usuario actor, List<String> p) {
        String usage = "UPDCLI[id,\"campo=valor\",...]";
        if (p.isEmpty()) throw new CommandException("Uso: " + usage);
        int id = InputValidator.validateId(p.get(0), "id");
        Map<String, String> f = Args.fields(p, 1, CAMPOS, usage);
        return Jdbc.tx(c -> {
            if (Jdbc.one(c, "SELECT id FROM clientes WHERE id = ? FOR UPDATE", id) == null) throw new CommandException("Cliente no encontrado: " + id);
            String antes = Jdbc.json(c, "clientes", id);
            List<String> sets = new ArrayList<>();
            List<Object> vals = new ArrayList<>();
            for (Map.Entry<String, String> e : f.entrySet()) {
                String v = e.getValue();
                Object val;
                switch (e.getKey()) {
                    case "razon_social" -> val = InputValidator.validateText(v, "razon_social", 160);
                    case "nit" -> {
                        String n = nit(v);
                        if (Jdbc.count(c, "SELECT 1 FROM clientes WHERE nit = ? AND id <> ?", n, id) > 0) throw new CommandException("Ya existe un cliente con ese NIT.");
                        val = n;
                    }
                    case "contacto" -> val = opt(v, 120, "contacto");
                    case "email" -> val = v.isBlank() ? null : InputValidator.validateEmail(v, "email");
                    case "telefono" -> val = telefono(v);
                    default -> {
                        boolean act = Args.parseBool(v, "activo");
                        if (!act) exigirSinPautasActivas(c, id);
                        val = act;
                    }
                }
                sets.add(e.getKey() + " = ?");
                vals.add(val);
            }
            vals.add(id);
            Jdbc.exec(c, "UPDATE clientes SET " + String.join(", ", sets) + " WHERE id = ?", vals.toArray());
            auditoria.registrar(c, actor.id(), "CLIENTE_ACTUALIZAR", "clientes", id, antes, Jdbc.json(c, "clientes", id), Args.cmdText("UPDCLI", p));
            return "OK: cliente " + id + " actualizado";
        });
    }

    String delete(Usuario actor, List<String> p) {
        Args.exact(p, 1, "DELCLI[id]");
        int id = InputValidator.validateId(p.get(0), "id");
        return Jdbc.tx(c -> {
            Map<String, Object> cur = Jdbc.one(c, "SELECT * FROM clientes WHERE id = ? FOR UPDATE", id);
            if (cur == null) throw new CommandException("Cliente no encontrado: " + id);
            if (!(Boolean) cur.get("activo")) return "OK: el cliente " + id + " ya estaba desactivado";
            exigirSinPautasActivas(c, id);
            String antes = Jdbc.json(c, "clientes", id);
            Jdbc.exec(c, "UPDATE clientes SET activo = FALSE WHERE id = ?", id);
            auditoria.registrar(c, actor.id(), "CLIENTE_DESACTIVAR", "clientes", id, antes, Jdbc.json(c, "clientes", id), Args.cmdText("DELCLI", p));
            return "OK: cliente " + id + " desactivado";
        });
    }

    private static void exigirSinPautasActivas(java.sql.Connection c, int id) throws java.sql.SQLException {
        if (Jdbc.count(c, "SELECT 1 FROM pautas WHERE cliente_id = ? AND estado = 'ACTIVA'", id) > 0)
            throw new CommandException("El cliente tiene pautas ACTIVAS; finalicelas o pausela primero.");
    }

    private static String nit(String v) {
        String n = InputValidator.validateText(v, "nit", 20);
        if (!n.matches("[0-9A-Za-z-]{5,20}")) throw new CommandException("nit invalido (5 a 20 caracteres alfanumericos)");
        return n;
    }

    private static String telefono(String v) {
        String t = v == null ? "" : v.trim();
        if (t.isEmpty()) return null;
        if (!t.matches("\\+?\\d{5,15}")) throw new CommandException("telefono invalido (solo digitos, 5 a 15)");
        return t;
    }

    private static String opt(String v, int max, String field) {
        return v == null || v.isBlank() ? null : InputValidator.validateText(v, field, max);
    }
}
