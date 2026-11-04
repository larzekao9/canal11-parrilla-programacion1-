package canal11.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Ayudas para leer parametros de comandos. */
public final class Args {

    private Args() {}

    public static void count(List<String> p, int min, int max, String usage) {
        if (p.size() < min || p.size() > max) throw new CommandException("Uso: " + usage);
    }

    public static void exact(List<String> p, int n, String usage) {
        count(p, n, n, usage);
    }

    public static boolean isAll(List<String> p) {
        return p.size() == 1 && p.get(0).trim().equals("*");
    }

    /** Parsea "campo=valor" desde la posicion from; solo campos permitidos (en minusculas). */
    public static Map<String, String> fields(List<String> p, int from, Set<String> allowed, String usage) {
        if (p.size() <= from) throw new CommandException("Uso: " + usage);
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = from; i < p.size(); i++) {
            String s = p.get(i);
            int eq = s.indexOf('=');
            if (eq <= 0) throw new CommandException("Formato invalido '" + s + "', use campo=valor");
            String k = s.substring(0, eq).trim().toLowerCase();
            String v = s.substring(eq + 1).trim();
            if (!allowed.contains(k)) {
                throw new CommandException("Campo no permitido: " + k + ". Permitidos: " + String.join(", ", allowed));
            }
            out.put(k, v);
        }
        return out;
    }

    public static boolean parseBool(String v, String field) {
        switch (v.trim().toLowerCase()) {
            case "true", "1", "si", "s" -> { return true; }
            case "false", "0", "no", "n" -> { return false; }
            default -> throw new CommandException(field + " debe ser true/false");
        }
    }

    /** Texto del comando para auditoria, ocultando passwords. */
    public static String cmdText(String name, List<String> p) {
        StringBuilder sb = new StringBuilder(name).append('[');
        for (int i = 0; i < p.size(); i++) {
            String s = p.get(i);
            boolean secret = (name.equals("INSUSU") && i == 6) || s.toLowerCase().startsWith("password=");
            if (i > 0) sb.append(',');
            sb.append('"').append(secret ? "***" : s).append('"');
        }
        return sb.append(']').toString();
    }
}
