package canal11.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** Formato de texto plano para las respuestas por correo. */
public final class TextTable {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private TextTable() {}

    public static String fmt(Object v) {
        if (v == null) return "-";
        if (v instanceof LocalTime t) return t.format(TIME);
        if (v instanceof LocalDateTime t) return t.format(DATETIME);
        if (v instanceof LocalDate d) return d.toString();
        if (v instanceof Boolean b) return b ? "si" : "no";
        return String.valueOf(v);
    }

    /** Tabla alineada con las columnas indicadas (nombres de clave del mapa). */
    public static String render(List<Map<String, Object>> rows, String... cols) {
        if (rows.isEmpty()) return "(sin resultados)";
        int[] w = new int[cols.length];
        for (int i = 0; i < cols.length; i++) w[i] = cols[i].length();
        for (Map<String, Object> r : rows) {
            for (int i = 0; i < cols.length; i++) w[i] = Math.max(w[i], fmt(r.get(cols[i])).length());
        }
        StringBuilder sb = new StringBuilder();
        line(sb, w, cols, true);
        StringBuilder sep = new StringBuilder();
        for (int i = 0; i < cols.length; i++) sep.append("-".repeat(w[i])).append(i < cols.length - 1 ? "-+-" : "");
        sb.append(sep).append('\n');
        for (Map<String, Object> r : rows) {
            String[] cells = new String[cols.length];
            for (int i = 0; i < cols.length; i++) cells[i] = fmt(r.get(cols[i]));
            line(sb, w, cells, false);
        }
        return sb.toString().stripTrailing();
    }

    private static void line(StringBuilder sb, int[] w, String[] cells, boolean upper) {
        for (int i = 0; i < cells.length; i++) {
            String c = upper ? cells[i].toUpperCase() : cells[i];
            sb.append(String.format("%-" + w[i] + "s", c));
            if (i < cells.length - 1) sb.append(" | ");
        }
        sb.append('\n');
    }

    /** Detalle vertical: "campo: valor" por linea. */
    public static String detail(Map<String, Object> row, String... cols) {
        StringBuilder sb = new StringBuilder();
        for (String c : cols) sb.append(String.format("%-16s %s%n", c + ":", fmt(row.get(c))));
        return sb.toString().stripTrailing();
    }
}
