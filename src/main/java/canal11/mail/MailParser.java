package canal11.mail;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extrae From y Subject de un correo crudo (RFC 822), incluyendo encabezados doblados y palabras codificadas (RFC 2047). */
public final class MailParser {

    private static final Pattern ENCODED = Pattern.compile("=\\?([^?]+)\\?([BbQq])\\?([^?]*)\\?=");
    private static final Pattern ADDR = Pattern.compile("<([^>]+)>");

    private MailParser() {}

    public static MailMessage parse(String id, String raw) {
        String norm = raw.replace("\r\n", "\n");
        int sep = norm.indexOf("\n\n");
        String headers = sep < 0 ? norm : norm.substring(0, sep);
        String body = sep < 0 ? "" : norm.substring(sep + 2);
        String from = extractAddress(header(headers, "From"));
        String subject = decode(header(headers, "Subject")).trim();
        return new MailMessage(id, from, subject, body);
    }

    static String header(String headers, String name) {
        StringBuilder value = null;
        for (String line : headers.split("\n")) {
            if (value != null) {
                if (line.startsWith(" ") || line.startsWith("\t")) { value.append(' ').append(line.trim()); continue; }
                break;
            }
            if (line.regionMatches(true, 0, name + ":", 0, name.length() + 1)) {
                value = new StringBuilder(line.substring(name.length() + 1).trim());
            }
        }
        return value == null ? "" : value.toString();
    }

    static String extractAddress(String from) {
        Matcher m = ADDR.matcher(from);
        return (m.find() ? m.group(1) : from).trim().toLowerCase();
    }

    static String decode(String s) {
        Matcher m = ENCODED.matcher(s);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (m.find()) {
            String between = s.substring(last, m.start());
            if (!between.isBlank() || last == 0) out.append(between); // espacios entre palabras codificadas se ignoran
            out.append(decodeWord(m.group(1), m.group(2), m.group(3)));
            last = m.end();
        }
        out.append(s.substring(last));
        return out.toString();
    }

    private static String decodeWord(String charset, String enc, String text) {
        Charset cs;
        try { cs = Charset.forName(charset); } catch (Exception e) { cs = StandardCharsets.UTF_8; }
        byte[] bytes;
        if (enc.equalsIgnoreCase("B")) {
            bytes = Base64.getMimeDecoder().decode(text);
        } else {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (ch == '_') bos.write(' ');
                else if (ch == '=' && i + 2 < text.length()) {
                    bos.write(Integer.parseInt(text.substring(i + 1, i + 3), 16));
                    i += 2;
                } else bos.write(ch);
            }
            bytes = bos.toByteArray();
        }
        return new String(bytes, cs);
    }
}
