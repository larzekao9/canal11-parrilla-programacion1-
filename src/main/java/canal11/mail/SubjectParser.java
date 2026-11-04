package canal11.mail;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Interpreta el Subject como COMANDO["p1","p2",...].
 * - Comandos: texto antes de "[" en mayusculas.
 * - Parametros: contenido entre el primer "[" y el ultimo "]".
 * - "*" => ["*"]. Numeros sin comillas. Comas dentro de comillas son parte del texto.
 * - Comillas internas: se aceptan escapadas (\") o sin escapar si no van seguidas de coma/fin.
 */
public final class SubjectParser {

    private static final Pattern FORMA = Pattern.compile("^\\s*[A-Za-z]{2,12}\\s*\\[.*\\]\\s*$", Pattern.DOTALL);

    private SubjectParser() {}

    public static boolean esValido(String subject) {
        return subject != null && FORMA.matcher(subject).matches();
    }

    /** Devuelve el comando en mayusculas, o "" si el subject no tiene la forma COMANDO[...]. */
    public static String extraerComando(String subject) {
        if (!esValido(subject)) return "";
        return subject.substring(0, subject.indexOf('[')).trim().toUpperCase();
    }

    /** Devuelve la lista de parametros. Subject invalido => lista vacia. */
    public static List<String> extraerParametros(String subject) {
        List<String> out = new ArrayList<>();
        if (!esValido(subject)) return out;
        String inner = subject.substring(subject.indexOf('[') + 1, subject.lastIndexOf(']')).trim();
        if (inner.isEmpty()) return out;
        if (inner.equals("*")) {
            out.add("*");
            return out;
        }
        int i = 0, n = inner.length();
        while (i < n) {
            while (i < n && Character.isWhitespace(inner.charAt(i))) i++;
            if (i >= n) break;
            StringBuilder sb = new StringBuilder();
            if (inner.charAt(i) == '"') {
                i = leerEntrecomillado(inner, i + 1, sb);
            } else {
                i = leerSinComillas(inner, i, sb);
            }
            out.add(sb.toString());
            while (i < n && Character.isWhitespace(inner.charAt(i))) i++;
            if (i < n && inner.charAt(i) == ',') i++; // salta la coma separadora
        }
        // coma final => parametro vacio final
        if (inner.endsWith(",")) out.add("");
        return out;
    }

    /** Lee hasta la comilla de cierre (la que va seguida de coma o fin). Devuelve la posicion posterior. */
    private static int leerEntrecomillado(String s, int i, StringBuilder sb) {
        int n = s.length();
        while (i < n) {
            char ch = s.charAt(i);
            if (ch == '\\' && i + 1 < n && (s.charAt(i + 1) == '"' || s.charAt(i + 1) == '\\')) {
                sb.append(s.charAt(i + 1));
                i += 2;
                continue;
            }
            if (ch == '"') {
                int j = i + 1;
                while (j < n && Character.isWhitespace(s.charAt(j))) j++;
                if (j >= n || s.charAt(j) == ',') return i + 1;
            }
            sb.append(ch);
            i++;
        }
        return i; // comilla sin cerrar: se toma hasta el final
    }

    /** Lee un valor sin comillas hasta la coma de nivel 0 (respeta {} [] y comillas internas). */
    private static int leerSinComillas(String s, int i, StringBuilder sb) {
        int n = s.length(), depth = 0;
        boolean inStr = false;
        while (i < n) {
            char ch = s.charAt(i);
            if (inStr) {
                if (ch == '\\' && i + 1 < n) { sb.append(ch).append(s.charAt(i + 1)); i += 2; continue; }
                if (ch == '"') inStr = false;
            } else {
                if (ch == '"') inStr = true;
                else if (ch == '{' || ch == '[') depth++;
                else if (ch == '}' || ch == ']') depth--;
                else if (ch == ',' && depth <= 0) break;
            }
            sb.append(ch);
            i++;
        }
        String v = sb.toString().trim();
        sb.setLength(0);
        sb.append(v);
        return i;
    }
}
