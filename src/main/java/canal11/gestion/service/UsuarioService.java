package canal11.gestion.service;

import canal11.db.Jdbc;
import canal11.emision.service.AuditoriaService;
import canal11.gestion.repository.UsuarioRepository;
import canal11.mail.CommandRouter;
import canal11.mail.CommandSpec;
import canal11.model.Usuario;
import canal11.util.Args;
import canal11.util.CommandException;
import canal11.util.InputValidator;
import canal11.util.TextTable;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** CU1 - Gestion de usuarios (solo administrador). DELUSU desactiva, nunca borra. */
public class UsuarioService {

    static final List<String> ROLES = List.of(Usuario.ADMIN, Usuario.PROGRAMADOR, Usuario.ENCARGADO);
    static final Set<String> CAMPOS = Set.of("nombre", "apellido", "rol", "telefono", "email", "password", "activo");
    private static final String[] COLS = {"id", "ci", "nombre", "apellido", "rol", "telefono", "email", "activo"};

    private final AuditoriaService auditoria;
    private final UsuarioRepository repo = new UsuarioRepository();

    public UsuarioService(AuditoriaService auditoria) {
        this.auditoria = auditoria;
    }

    public void registerAll(CommandRouter r) {
        var A = CommandSpec.ADMIN;
        r.register(new CommandSpec("INSUSU", "INSUSU[\"ci\",\"nombre\",\"apellido\",\"rol\",\"telefono\",\"email\",\"password\"]",
                "Crea un usuario", A, this::insert));
        r.register(new CommandSpec("LISUSU", "LISUSU[\"*\"] | LISUSU[\"rol\"]", "Lista usuarios", A, this::list));
        r.register(new CommandSpec("GETUSU", "GETUSU[id]", "Detalle de un usuario", A, this::get));
        r.register(new CommandSpec("UPDUSU", "UPDUSU[id,\"campo=valor\",...]",
                "Actualiza (nombre, apellido, rol, telefono, email, password, activo)", A, this::update));
        r.register(new CommandSpec("DELUSU", "DELUSU[id]", "Desactiva un usuario", A, this::delete));
    }

    String insert(Usuario actor, List<String> p) {
        Args.exact(p, 7, "INSUSU[\"ci\",\"nombre\",\"apellido\",\"rol\",\"telefono\",\"email\",\"password\"]");
        String ci = InputValidator.validateText(p.get(0), "ci", 20);
        String nombre = InputValidator.validateText(p.get(1), "nombre", 80);
        String apellido = InputValidator.validateText(p.get(2), "apellido", 80);
        String rol = InputValidator.validateEnum(p.get(3), "rol", ROLES);
        String tel = telefono(p.get(4));
        String email = InputValidator.validateEmail(p.get(5), "email");
        String pass = password(p.get(6));
        return Jdbc.tx(c -> {
            if (repo.existeCi(c, ci)) throw new CommandException("Ya existe un usuario con ese ci.");
            if (repo.existeEmail(c, email, null)) throw new CommandException("Ya existe un usuario con ese email.");
            int id = repo.insert(c, ci, nombre, apellido, rol, tel, email, BCrypt.hashpw(pass, BCrypt.gensalt()));
            auditoria.registrar(c, actor.id(), "USUARIO_CREAR", "users", id, null, repo.snapshot(c, id), Args.cmdText("INSUSU", p));
            return "OK: usuario creado con ID " + id;
        });
    }

    String list(Usuario actor, List<String> p) {
        Args.exact(p, 1, "LISUSU[\"*\"] | LISUSU[\"rol\"]");
        return Jdbc.tx(c -> {
            List<Map<String, Object>> rows;
            if (Args.isAll(p)) {
                rows = repo.list(c);
            } else {
                rows = repo.listByRol(c, InputValidator.validateEnum(p.get(0), "rol", ROLES));
            }
            return "Listando usuarios (" + rows.size() + "):\n" + TextTable.render(rows, COLS);
        });
    }

    String get(Usuario actor, List<String> p) {
        Args.exact(p, 1, "GETUSU[id]");
        int id = InputValidator.validateId(p.get(0), "id");
        return Jdbc.tx(c -> {
            Map<String, Object> r = repo.findPublicById(c, id);
            if (r == null) throw new CommandException("Usuario no encontrado: " + id);
            return TextTable.detail(r, COLS);
        });
    }

    String update(Usuario actor, List<String> p) {
        String usage = "UPDUSU[id,\"campo=valor\",...]";
        if (p.isEmpty()) throw new CommandException("Uso: " + usage);
        int id = InputValidator.validateId(p.get(0), "id");
        Map<String, String> f = Args.fields(p, 1, CAMPOS, usage);
        return Jdbc.tx(c -> {
            Map<String, Object> cur = repo.findByIdForUpdate(c, id);
            if (cur == null) throw new CommandException("Usuario no encontrado: " + id);
            String antes = repo.snapshot(c, id);
            List<String> sets = new ArrayList<>();
            List<Object> vals = new ArrayList<>();
            boolean seraAdminActivo = Usuario.ADMIN.equals(cur.get("rol")) && (Boolean) cur.get("activo");
            for (Map.Entry<String, String> e : f.entrySet()) {
                String v = e.getValue();
                switch (e.getKey()) {
                    case "nombre", "apellido" -> add(sets, vals, e.getKey(), InputValidator.validateText(v, e.getKey(), 80));
                    case "rol" -> {
                        String rol = InputValidator.validateEnum(v, "rol", ROLES);
                        add(sets, vals, "rol", rol);
                        seraAdminActivo = seraAdminActivo && rol.equals(Usuario.ADMIN);
                    }
                    case "telefono" -> add(sets, vals, "telefono", telefono(v));
                    case "email" -> {
                        String em = InputValidator.validateEmail(v, "email");
                        if (repo.existeEmail(c, em, id))
                            throw new CommandException("Ya existe un usuario con ese email.");
                        add(sets, vals, "email", em);
                    }
                    case "password" -> add(sets, vals, "password", BCrypt.hashpw(password(v), BCrypt.gensalt()));
                    case "activo" -> {
                        boolean act = Args.parseBool(v, "activo");
                        if (!act && id == actor.id()) throw new CommandException("No puede desactivarse a si mismo.");
                        add(sets, vals, "activo", act);
                        seraAdminActivo = seraAdminActivo && act;
                    }
                    default -> throw new CommandException("Campo no permitido: " + e.getKey());
                }
            }
            boolean eraAdminActivo = Usuario.ADMIN.equals(cur.get("rol")) && (Boolean) cur.get("activo");
            if (eraAdminActivo && !seraAdminActivo) exigirOtroAdmin(c, id);
            repo.update(c, id, sets, vals);
            auditoria.registrar(c, actor.id(), "USUARIO_ACTUALIZAR", "users", id, antes, repo.snapshot(c, id), Args.cmdText("UPDUSU", p));
            return "OK: usuario " + id + " actualizado";
        });
    }

    String delete(Usuario actor, List<String> p) {
        Args.exact(p, 1, "DELUSU[id]");
        int id = InputValidator.validateId(p.get(0), "id");
        if (id == actor.id()) throw new CommandException("No puede desactivarse a si mismo.");
        return Jdbc.tx(c -> {
            Map<String, Object> cur = repo.findByIdForUpdate(c, id);
            if (cur == null) throw new CommandException("Usuario no encontrado: " + id);
            if (!(Boolean) cur.get("activo")) return "OK: el usuario " + id + " ya estaba desactivado";
            if (Usuario.ADMIN.equals(cur.get("rol"))) exigirOtroAdmin(c, id);
            String antes = repo.snapshot(c, id);
            repo.desactivar(c, id);
            auditoria.registrar(c, actor.id(), "USUARIO_DESACTIVAR", "users", id, antes, repo.snapshot(c, id), Args.cmdText("DELUSU", p));
            return "OK: usuario " + id + " desactivado";
        });
    }

    private void exigirOtroAdmin(Connection c, int excluirId) throws SQLException {
        if (!repo.existeOtroAdminActivo(c, excluirId)) {
            throw new CommandException("Debe existir al menos un administrador activo.");
        }
    }

    private static void add(List<String> sets, List<Object> vals, String col, Object v) {
        sets.add(col + " = ?");
        vals.add(v);
    }

    private static String telefono(String v) {
        String t = v == null ? "" : v.trim();
        if (t.isEmpty() || t.equals("0")) return null;
        if (!t.matches("\\+?\\d{5,15}")) throw new CommandException("telefono invalido (solo digitos, 5 a 15)");
        return t;
    }

    private static String password(String v) {
        if (v == null || v.length() < 8) throw new CommandException("password debe tener minimo 8 caracteres");
        if (v.length() > 72) throw new CommandException("password excede 72 caracteres");
        return v;
    }
}
