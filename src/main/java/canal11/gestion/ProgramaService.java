package canal11.gestion;

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

/** CU2 - Catalogo de programas. DELPRG desactiva (no se borra) y se bloquea si hay parrillas futuras que lo usan. */
public class ProgramaService {

    public static final List<String> TIPOS = List.of("noticiero", "revista", "pelicula", "documental", "serie", "musical", "otro");
    static final Set<String> CAMPOS = Set.of("nombre", "descripcion", "tipo", "duracion_min", "genero", "clasificacion", "activo");
    private static final String[] COLS = {"id", "nombre", "tipo", "duracion_min", "genero", "clasificacion", "activo"};
    private static final String[] DET = {"id", "nombre", "descripcion", "tipo", "duracion_min", "genero", "clasificacion", "activo"};

    private final AuditoriaService auditoria;

    public ProgramaService(AuditoriaService auditoria) {
        this.auditoria = auditoria;
    }

    public void registerAll(CommandRouter r) {
        var W = CommandSpec.ADMIN_PROG;
        var R = CommandSpec.TODOS;
        r.register(new CommandSpec("INSPRG", "INSPRG[\"nombre\",\"descripcion\",\"tipo\",duracion_min,\"genero\",\"clasificacion\"]",
                "Crea un programa", W, this::insert));
        r.register(new CommandSpec("LISPRG", "LISPRG[\"*\"] | LISPRG[\"tipo\"]", "Lista programas", R, this::list));
        r.register(new CommandSpec("GETPRG", "GETPRG[id]", "Detalle de un programa", R, this::get));
        r.register(new CommandSpec("UPDPRG", "UPDPRG[id,\"campo=valor\",...]", "Actualiza un programa", W, this::update));
        r.register(new CommandSpec("DELPRG", "DELPRG[id]", "Desactiva un programa", W, this::delete));
    }

    String insert(Usuario actor, List<String> p) {
        Args.exact(p, 6, "INSPRG[\"nombre\",\"descripcion\",\"tipo\",duracion_min,\"genero\",\"clasificacion\"]");
        String nombre = InputValidator.validateText(p.get(0), "nombre", 120);
        String desc = p.get(1).isBlank() ? null : p.get(1).trim();
        String tipo = InputValidator.validateEnum(p.get(2), "tipo", TIPOS);
        int dur = InputValidator.validatePositive(p.get(3), "duracion_min");
        String genero = opt(p.get(4), 60, "genero");
        String clasif = opt(p.get(5), 10, "clasificacion");
        return Jdbc.tx(c -> {
            if (Jdbc.count(c, "SELECT 1 FROM programas WHERE lower(nombre) = lower(?) AND tipo = ?", nombre, tipo) > 0)
                throw new CommandException("Ya existe un programa '" + nombre + "' de tipo " + tipo + ".");
            int id = Jdbc.insert(c, "INSERT INTO programas (nombre, descripcion, tipo, duracion_min, genero, clasificacion) "
                    + "VALUES (?,?,?,?,?,?) RETURNING id", nombre, desc, tipo, dur, genero, clasif);
            auditoria.registrar(c, actor.id(), "PROGRAMA_CREAR", "programas", id, null, Jdbc.json(c, "programas", id), Args.cmdText("INSPRG", p));
            return "OK: programa creado con ID " + id;
        });
    }

    String list(Usuario actor, List<String> p) {
        Args.exact(p, 1, "LISPRG[\"*\"] | LISPRG[\"tipo\"]");
        return Jdbc.tx(c -> {
            List<Map<String, Object>> rows = Args.isAll(p)
                    ? Jdbc.query(c, "SELECT * FROM programas ORDER BY id")
                    : Jdbc.query(c, "SELECT * FROM programas WHERE tipo = ? ORDER BY id", InputValidator.validateEnum(p.get(0), "tipo", TIPOS));
            return "Listando programas (" + rows.size() + "):\n" + TextTable.render(rows, COLS);
        });
    }

    String get(Usuario actor, List<String> p) {
        Args.exact(p, 1, "GETPRG[id]");
        int id = InputValidator.validateId(p.get(0), "id");
        return Jdbc.tx(c -> {
            Map<String, Object> r = Jdbc.one(c, "SELECT * FROM programas WHERE id = ?", id);
            if (r == null) throw new CommandException("Programa no encontrado: " + id);
            return TextTable.detail(r, DET);
        });
    }

    String update(Usuario actor, List<String> p) {
        String usage = "UPDPRG[id,\"campo=valor\",...]";
        if (p.isEmpty()) throw new CommandException("Uso: " + usage);
        int id = InputValidator.validateId(p.get(0), "id");
        Map<String, String> f = Args.fields(p, 1, CAMPOS, usage);
        return Jdbc.tx(c -> {
            Map<String, Object> cur = Jdbc.one(c, "SELECT * FROM programas WHERE id = ? FOR UPDATE", id);
            if (cur == null) throw new CommandException("Programa no encontrado: " + id);
            String antes = Jdbc.json(c, "programas", id);
            List<String> sets = new ArrayList<>();
            List<Object> vals = new ArrayList<>();
            String nombre = (String) cur.get("nombre"), tipo = (String) cur.get("tipo");
            for (Map.Entry<String, String> e : f.entrySet()) {
                String v = e.getValue();
                Object val;
                switch (e.getKey()) {
                    case "nombre" -> { nombre = InputValidator.validateText(v, "nombre", 120); val = nombre; }
                    case "tipo" -> { tipo = InputValidator.validateEnum(v, "tipo", TIPOS); val = tipo; }
                    case "descripcion" -> val = v.isBlank() ? null : v;
                    case "duracion_min" -> val = InputValidator.validatePositive(v, "duracion_min");
                    case "genero" -> val = opt(v, 60, "genero");
                    case "clasificacion" -> val = opt(v, 10, "clasificacion");
                    case "activo" -> {
                        boolean act = Args.parseBool(v, "activo");
                        if (!act) exigirSinUso(c, id);
                        val = act;
                    }
                    default -> throw new CommandException("Campo no permitido: " + e.getKey());
                }
                sets.add(e.getKey() + " = ?");
                vals.add(val);
            }
            if (Jdbc.count(c, "SELECT 1 FROM programas WHERE lower(nombre) = lower(?) AND tipo = ? AND id <> ?", nombre, tipo, id) > 0)
                throw new CommandException("Ya existe un programa '" + nombre + "' de tipo " + tipo + ".");
            vals.add(id);
            Jdbc.exec(c, "UPDATE programas SET " + String.join(", ", sets) + " WHERE id = ?", vals.toArray());
            auditoria.registrar(c, actor.id(), "PROGRAMA_ACTUALIZAR", "programas", id, antes, Jdbc.json(c, "programas", id), Args.cmdText("UPDPRG", p));
            return "OK: programa " + id + " actualizado";
        });
    }

    String delete(Usuario actor, List<String> p) {
        Args.exact(p, 1, "DELPRG[id]");
        int id = InputValidator.validateId(p.get(0), "id");
        return Jdbc.tx(c -> {
            Map<String, Object> cur = Jdbc.one(c, "SELECT * FROM programas WHERE id = ? FOR UPDATE", id);
            if (cur == null) throw new CommandException("Programa no encontrado: " + id);
            if (!(Boolean) cur.get("activo")) return "OK: el programa " + id + " ya estaba desactivado";
            exigirSinUso(c, id);
            String antes = Jdbc.json(c, "programas", id);
            Jdbc.exec(c, "UPDATE programas SET activo = FALSE WHERE id = ?", id);
            auditoria.registrar(c, actor.id(), "PROGRAMA_DESACTIVAR", "programas", id, antes, Jdbc.json(c, "programas", id), Args.cmdText("DELPRG", p));
            return "OK: programa " + id + " desactivado";
        });
    }

    /** No se desactiva un programa usado en parrillas borrador/publicadas de hoy en adelante. */
    private static void exigirSinUso(java.sql.Connection c, int id) throws java.sql.SQLException {
        long n = Jdbc.count(c, "SELECT count(*) FROM parrilla_bloques b JOIN parrillas pa ON pa.id = b.parrilla_id "
                + "WHERE b.programa_id = ? AND pa.estado IN ('BORRADOR','PUBLICADA') AND pa.fecha >= CURRENT_DATE", id);
        if (n > 0) throw new CommandException("El programa esta en " + n + " bloque(s) de parrillas vigentes; quitelo de las parrillas primero.");
    }

    private static String opt(String v, int max, String field) {
        if (v == null || v.isBlank()) return null;
        return InputValidator.validateText(v, field, max);
    }
}
