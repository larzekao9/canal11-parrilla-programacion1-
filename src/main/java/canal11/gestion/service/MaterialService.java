package canal11.gestion.service;

import canal11.db.Jdbc;
import canal11.emision.service.AuditoriaService;
import canal11.gestion.repository.MaterialRepository;
import canal11.mail.CommandRouter;
import canal11.mail.CommandSpec;
import canal11.model.Usuario;
import canal11.util.Args;
import canal11.util.CommandException;
import canal11.util.FileHash;
import canal11.util.InputValidator;
import canal11.util.TextTable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CU4 - Materiales de video. El archivo ya esta en el servidor local: el comando registra su ruta y el sistema
 * calcula el hash SHA-256 (un hash repetido = material duplicado). Estado inicial PENDIENTE (se valida en CU5).
 */
public class MaterialService {

    public static final List<String> TIPOS = List.of("programa", "spot", "promo", "relleno");
    public static final List<String> FORMATOS = List.of("mp4", "mov", "mxf");
    static final List<String> ESTADOS = List.of("PENDIENTE", "VALIDADO", "RECHAZADO");
    static final Set<String> CAMPOS = Set.of("titulo", "tipo", "duracion_seg", "formato", "observaciones", "programa_id", "ruta", "ruta_archivo");
    private static final String[] COLS = {"id", "titulo", "tipo", "duracion_seg", "formato", "estado", "programa_id", "activo"};
    private static final String[] DET = {"id", "titulo", "tipo", "duracion_seg", "formato", "ruta_archivo", "hash_sha256",
            "estado", "programa_id", "validado_por", "validado_at", "observaciones", "registrado_por", "activo"};

    private final AuditoriaService auditoria;
    private final MaterialRepository repo = new MaterialRepository();

    public MaterialService(AuditoriaService auditoria) {
        this.auditoria = auditoria;
    }

    public void registerAll(CommandRouter r) {
        var W = CommandSpec.ADMIN_ENC;
        var R = CommandSpec.TODOS;
        r.register(new CommandSpec("INSMAT", "INSMAT[programa_id|0,\"titulo\",\"tipo\",duracion_seg,\"formato\",\"ruta\"]",
                "Registra un material de video", W, this::insert));
        r.register(new CommandSpec("LISMAT", "LISMAT[\"*\"] | [\"estado\"] | [\"tipo\"]", "Lista materiales", R, this::list));
        r.register(new CommandSpec("GETMAT", "GETMAT[id]", "Detalle de un material", R, this::get));
        r.register(new CommandSpec("UPDMAT", "UPDMAT[id,\"campo=valor\",...]", "Actualiza un material", W, this::update));
        r.register(new CommandSpec("DELMAT", "DELMAT[id]", "Da de baja un material", W, this::delete));
    }

    String insert(Usuario actor, List<String> p) {
        Args.exact(p, 6, "INSMAT[programa_id|0,\"titulo\",\"tipo\",duracion_seg,\"formato\",\"ruta\"]");
        int programaId = InputValidator.validateNonNegative(p.get(0), "programa_id");
        String titulo = InputValidator.validateText(p.get(1), "titulo", 160);
        String tipo = InputValidator.validateEnum(p.get(2), "tipo", TIPOS);
        int dur = InputValidator.validatePositive(p.get(3), "duracion_seg");
        String formato = InputValidator.validateEnum(p.get(4), "formato", FORMATOS);
        String ruta = InputValidator.validateText(p.get(5), "ruta", 1000);
        String hash = hashDe(ruta, formato);
        return Jdbc.tx(c -> {
            if (programaId > 0) exigirPrograma(c, programaId);
            exigirHashLibre(c, hash, 0);
            int id = repo.insert(c, programaId > 0 ? programaId : null, titulo, tipo, dur, formato, ruta, hash, actor.id());
            auditoria.registrar(c, actor.id(), "MATERIAL_CREAR", "materiales", id, null, repo.snapshot(c, id), Args.cmdText("INSMAT", p));
            return "OK: material registrado con ID " + id + " (estado PENDIENTE)";
        });
    }

    String list(Usuario actor, List<String> p) {
        Args.exact(p, 1, "LISMAT[\"*\"] | LISMAT[\"estado\"] | LISMAT[\"tipo\"]");
        return Jdbc.tx(c -> {
            List<Map<String, Object>> rows;
            if (Args.isAll(p)) {
                rows = repo.listActivos(c);
            } else if (ESTADOS.contains(p.get(0).trim().toUpperCase())) {
                rows = repo.listByEstado(c, p.get(0).trim().toUpperCase());
            } else {
                rows = repo.listByTipo(c, InputValidator.validateEnum(p.get(0), "estado o tipo", TIPOS));
            }
            return "Listando materiales (" + rows.size() + "):\n" + TextTable.render(rows, COLS);
        });
    }

    String get(Usuario actor, List<String> p) {
        Args.exact(p, 1, "GETMAT[id]");
        int id = InputValidator.validateId(p.get(0), "id");
        return Jdbc.tx(c -> {
            Map<String, Object> r = repo.findById(c, id);
            if (r == null) throw new CommandException("Material no encontrado: " + id);
            return TextTable.detail(r, DET);
        });
    }

    String update(Usuario actor, List<String> p) {
        String usage = "UPDMAT[id,\"campo=valor\",...]";
        if (p.isEmpty()) throw new CommandException("Uso: " + usage);
        int id = InputValidator.validateId(p.get(0), "id");
        Map<String, String> f = Args.fields(p, 1, CAMPOS, usage);
        return Jdbc.tx(c -> {
            Map<String, Object> cur = repo.findByIdForUpdate(c, id);
            if (cur == null) throw new CommandException("Material no encontrado: " + id);
            String antes = repo.snapshot(c, id);
            List<String> sets = new ArrayList<>();
            List<Object> vals = new ArrayList<>();
            boolean tecnico = false; // cambios que invalidan una validacion previa
            String formato = (String) cur.get("formato"), ruta = (String) cur.get("ruta_archivo");
            for (Map.Entry<String, String> e : f.entrySet()) {
                String v = e.getValue();
                switch (e.getKey()) {
                    case "titulo" -> add(sets, vals, "titulo", InputValidator.validateText(v, "titulo", 160));
                    case "observaciones" -> add(sets, vals, "observaciones", v.isBlank() ? null : v);
                    case "programa_id" -> {
                        int pid = InputValidator.validateNonNegative(v, "programa_id");
                        if (pid > 0) exigirPrograma(c, pid);
                        add(sets, vals, "programa_id", pid > 0 ? pid : null);
                    }
                    case "tipo" -> {
                        String t = InputValidator.validateEnum(v, "tipo", TIPOS);
                        if (t.equals("spot") && repo.enBloques(c, id))
                            throw new CommandException("El material esta en bloques de parrilla; no puede ser spot.");
                        if (!t.equals("spot") && repo.enSpots(c, id))
                            throw new CommandException("El material esta en pautas como spot; no puede cambiar de tipo.");
                        add(sets, vals, "tipo", t);
                        tecnico = true;
                    }
                    case "duracion_seg" -> { add(sets, vals, "duracion_seg", InputValidator.validatePositive(v, "duracion_seg")); tecnico = true; }
                    case "formato" -> { formato = InputValidator.validateEnum(v, "formato", FORMATOS); add(sets, vals, "formato", formato); tecnico = true; }
                    default -> { ruta = InputValidator.validateText(v, "ruta", 1000); tecnico = true; } // ruta / ruta_archivo
                }
            }
            if (f.containsKey("ruta") || f.containsKey("ruta_archivo") || f.containsKey("formato")) {
                String hash = hashDe(ruta, formato);
                exigirHashLibre(c, hash, id);
                add(sets, vals, "ruta_archivo", ruta);
                add(sets, vals, "hash_sha256", hash);
            }
            if (tecnico && !"PENDIENTE".equals(cur.get("estado"))) {
                sets.add("estado = 'PENDIENTE'");
                sets.add("validado_por = NULL");
                sets.add("validado_at = NULL");
            }
            repo.update(c, id, sets, vals);
            auditoria.registrar(c, actor.id(), "MATERIAL_ACTUALIZAR", "materiales", id, antes, repo.snapshot(c, id), Args.cmdText("UPDMAT", p));
            return "OK: material " + id + " actualizado" + (tecnico && !"PENDIENTE".equals(cur.get("estado")) ? " (vuelve a PENDIENTE de validacion)" : "");
        });
    }

    String delete(Usuario actor, List<String> p) {
        Args.exact(p, 1, "DELMAT[id]");
        int id = InputValidator.validateId(p.get(0), "id");
        return Jdbc.tx(c -> {
            Map<String, Object> cur = repo.findByIdForUpdate(c, id);
            if (cur == null) throw new CommandException("Material no encontrado: " + id);
            if (!(Boolean) cur.get("activo")) return "OK: el material " + id + " ya estaba dado de baja";
            if (repo.enParrillaVigente(c, id))
                throw new CommandException("El material esta en una parrilla borrador/publicada; quitelo primero.");
            if (repo.tieneSpotsProgramados(c, id))
                throw new CommandException("El material tiene spots programados; quitelos primero.");
            String antes = repo.snapshot(c, id);
            repo.darDeBaja(c, id);
            auditoria.registrar(c, actor.id(), "MATERIAL_BAJA", "materiales", id, antes, repo.snapshot(c, id), Args.cmdText("DELMAT", p));
            return "OK: material " + id + " dado de baja";
        });
    }

    // ---------------------------------------------------------------- utilidades compartidas

    /** Verifica que el archivo exista, sea legible y tenga la extension del formato; devuelve su SHA-256. */
    static String hashDe(String ruta, String formato) {
        Path path;
        try {
            path = Path.of(ruta);
        } catch (RuntimeException e) {
            throw new CommandException("ruta invalida");
        }
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) throw new CommandException("El archivo no existe o no es legible: " + ruta);
        String name = path.getFileName().toString().toLowerCase();
        if (!name.endsWith("." + formato.toLowerCase())) throw new CommandException("La extension del archivo no coincide con el formato " + formato);
        try {
            return FileHash.sha256(path);
        } catch (IOException e) {
            throw new CommandException("No se pudo leer el archivo: " + e.getMessage());
        }
    }

    private void exigirHashLibre(Connection c, String hash, int exceptId) throws SQLException {
        Object dupId = repo.idConHash(c, hash, exceptId);
        if (dupId != null) throw new CommandException("material duplicado, ya registrado como ID " + dupId);
    }

    private void exigirPrograma(Connection c, int id) throws SQLException {
        if (!repo.programaActivoExiste(c, id))
            throw new CommandException("programa_id no existe o esta inactivo: " + id);
    }

    private static void add(List<String> sets, List<Object> vals, String col, Object v) {
        int i = sets.indexOf(col + " = ?");
        if (i >= 0) {
            vals.set(i, v);
        } else {
            sets.add(col + " = ?");
            vals.add(v);
        }
    }
}
