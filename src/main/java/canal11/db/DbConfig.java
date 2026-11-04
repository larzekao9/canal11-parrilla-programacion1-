package canal11.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Lee configuracion de variables de entorno o de config.properties local (ignorado por git).
 * Clave "db.password" => variable de entorno DB_PASSWORD. El entorno tiene prioridad.
 */
public final class DbConfig {

    private static final Properties FILE = load();

    private DbConfig() {}

    private static Properties load() {
        Properties p = new Properties();
        Path path = Path.of("config.properties");
        if (Files.exists(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                p.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new IllegalStateException("No se pudo leer config.properties", e);
            }
        }
        return p;
    }

    public static String get(String key, String defaultValue) {
        String env = System.getenv(key.toUpperCase().replace('.', '_'));
        if (env != null && !env.isBlank()) return env;
        String v = FILE.getProperty(key);
        return (v != null && !v.isBlank()) ? v.trim() : defaultValue;
    }

    public static String require(String key) {
        String v = get(key, null);
        if (v == null) {
            throw new IllegalStateException("Falta configuracion: " + key
                    + " (config.properties o variable " + key.toUpperCase().replace('.', '_') + ")");
        }
        return v;
    }

    public static String url()      { return get("db.url", "jdbc:postgresql://localhost:5432/canal_bd"); }
    public static String user()     { return get("db.user", "postgres"); }
    public static String password() { return require("db.password"); }
}
