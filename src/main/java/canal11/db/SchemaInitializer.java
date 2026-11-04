package canal11.db;

import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/** Crea todas las tablas (idempotente) y el administrador semilla. */
public final class SchemaInitializer {

    private SchemaInitializer() {}

    private static final List<String> DDL = List.of(
        """
        CREATE TABLE IF NOT EXISTS users (
            id         SERIAL PRIMARY KEY,
            ci         VARCHAR(20)  NOT NULL UNIQUE,
            nombre     VARCHAR(80)  NOT NULL,
            apellido   VARCHAR(80)  NOT NULL,
            rol        VARCHAR(20)  NOT NULL CHECK (rol IN ('administrador','programador','encargado')),
            telefono   VARCHAR(20),
            email      VARCHAR(120) NOT NULL UNIQUE,
            password   VARCHAR(100) NOT NULL,
            activo     BOOLEAN      NOT NULL DEFAULT TRUE
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS auth_sessions (
            user_id    INTEGER   PRIMARY KEY REFERENCES users(id) ON DELETE RESTRICT,
            expires_at TIMESTAMP NOT NULL
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS programas (
            id            SERIAL PRIMARY KEY,
            nombre        VARCHAR(120) NOT NULL,
            descripcion   TEXT,
            tipo          VARCHAR(20)  NOT NULL
                          CHECK (tipo IN ('noticiero','revista','pelicula','documental','serie','musical','otro')),
            duracion_min  INTEGER      NOT NULL CHECK (duracion_min > 0),
            genero        VARCHAR(60),
            clasificacion VARCHAR(10),
            activo        BOOLEAN      NOT NULL DEFAULT TRUE,
            UNIQUE (nombre, tipo)
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS materiales (
            id             SERIAL PRIMARY KEY,
            programa_id    INTEGER REFERENCES programas(id) ON DELETE RESTRICT,
            titulo         VARCHAR(160) NOT NULL,
            tipo           VARCHAR(20)  NOT NULL CHECK (tipo IN ('programa','spot','promo','relleno')),
            duracion_seg   INTEGER      NOT NULL CHECK (duracion_seg > 0),
            formato        VARCHAR(10)  NOT NULL,
            ruta_archivo   TEXT         NOT NULL,
            hash_sha256    CHAR(64)     NOT NULL UNIQUE,
            estado         VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE'
                           CHECK (estado IN ('PENDIENTE','VALIDADO','RECHAZADO')),
            validado_por   INTEGER REFERENCES users(id) ON DELETE RESTRICT,
            validado_at    TIMESTAMP,
            observaciones  TEXT,
            registrado_por INTEGER NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
            activo         BOOLEAN NOT NULL DEFAULT TRUE
        )
        """,
        "ALTER TABLE materiales ADD COLUMN IF NOT EXISTS registrado_at TIMESTAMP NOT NULL DEFAULT now()",
        """
        CREATE TABLE IF NOT EXISTS parrillas (
            id           SERIAL PRIMARY KEY,
            fecha        DATE        NOT NULL UNIQUE,
            estado       VARCHAR(20) NOT NULL DEFAULT 'BORRADOR'
                         CHECK (estado IN ('BORRADOR','PUBLICADA','CERRADA')),
            version      INTEGER     NOT NULL DEFAULT 1,
            creado_por   INTEGER     NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
            publicada_at TIMESTAMP
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS parrilla_bloques (
            id          SERIAL PRIMARY KEY,
            parrilla_id INTEGER NOT NULL REFERENCES parrillas(id) ON DELETE RESTRICT,
            programa_id INTEGER NOT NULL REFERENCES programas(id) ON DELETE RESTRICT,
            material_id INTEGER REFERENCES materiales(id) ON DELETE RESTRICT,
            hora_inicio TIME    NOT NULL,
            hora_fin    TIME    NOT NULL,
            orden       INTEGER NOT NULL DEFAULT 0,
            tipo        VARCHAR(20) NOT NULL CHECK (tipo IN ('programa','pausa_comercial','promo')),
            CHECK (hora_fin > hora_inicio)
        )
        """,
        "CREATE INDEX IF NOT EXISTS idx_bloques_parrilla_hora ON parrilla_bloques(parrilla_id, hora_inicio)",
        """
        CREATE TABLE IF NOT EXISTS clientes (
            id           SERIAL PRIMARY KEY,
            razon_social VARCHAR(160) NOT NULL,
            nit          VARCHAR(20)  NOT NULL UNIQUE,
            contacto     VARCHAR(120),
            email        VARCHAR(120),
            telefono     VARCHAR(20),
            activo       BOOLEAN NOT NULL DEFAULT TRUE
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS pautas (
            id           SERIAL PRIMARY KEY,
            cliente_id   INTEGER NOT NULL REFERENCES clientes(id) ON DELETE RESTRICT,
            nombre       VARCHAR(160) NOT NULL,
            fecha_inicio DATE NOT NULL,
            fecha_fin    DATE NOT NULL,
            tarifa_spot  NUMERIC(12,2) NOT NULL CHECK (tarifa_spot > 0),
            max_pases    INTEGER NOT NULL CHECK (max_pases > 0),
            estado       VARCHAR(20) NOT NULL DEFAULT 'ACTIVA'
                         CHECK (estado IN ('ACTIVA','PAUSADA','FINALIZADA')),
            creado_por   INTEGER NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
            CHECK (fecha_fin >= fecha_inicio)
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS pauta_spots (
            id                SERIAL PRIMARY KEY,
            pauta_id          INTEGER NOT NULL REFERENCES pautas(id) ON DELETE RESTRICT,
            material_id       INTEGER NOT NULL REFERENCES materiales(id) ON DELETE RESTRICT,
            parrilla_bloque_id INTEGER NOT NULL REFERENCES parrilla_bloques(id) ON DELETE RESTRICT,
            hora_programada   TIME NOT NULL,
            estado            VARCHAR(20) NOT NULL DEFAULT 'PROGRAMADO'
                              CHECK (estado IN ('PROGRAMADO','EMITIDO','NO_EMITIDO'))
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS emisiones_reales (
            id                 SERIAL PRIMARY KEY,
            parrilla_bloque_id INTEGER REFERENCES parrilla_bloques(id) ON DELETE RESTRICT,
            pauta_spot_id      INTEGER REFERENCES pauta_spots(id) ON DELETE RESTRICT,
            inicio_real        TIMESTAMP NOT NULL,
            fin_real           TIMESTAMP,
            estado             VARCHAR(20) NOT NULL
                               CHECK (estado IN ('EMITIDO','PARCIAL','NO_EMITIDO','REEMPLAZADO')),
            desviacion_seg     INTEGER,
            observacion        TEXT,
            registrado_por     INTEGER NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
            CHECK (parrilla_bloque_id IS NOT NULL OR pauta_spot_id IS NOT NULL)
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS auditoria_logs (
            id         BIGSERIAL PRIMARY KEY,
            user_id    INTEGER REFERENCES users(id) ON DELETE RESTRICT,
            accion     VARCHAR(40)  NOT NULL,
            entidad    VARCHAR(40),
            entidad_id INTEGER,
            antes      JSONB,
            despues    JSONB,
            comando    TEXT,
            created_at TIMESTAMP NOT NULL DEFAULT now()
        )
        """,
        "CREATE INDEX IF NOT EXISTS idx_auditoria_entidad ON auditoria_logs(entidad, entidad_id)",
        // auditoria_logs es solo INSERT: se bloquea UPDATE/DELETE a nivel de BD.
        """
        CREATE OR REPLACE FUNCTION auditoria_solo_insert() RETURNS trigger AS $$
        BEGIN
            RAISE EXCEPTION 'auditoria_logs es de solo insercion';
        END;
        $$ LANGUAGE plpgsql
        """,
        "DROP TRIGGER IF EXISTS trg_auditoria_solo_insert ON auditoria_logs",
        """
        CREATE TRIGGER trg_auditoria_solo_insert
            BEFORE UPDATE OR DELETE ON auditoria_logs
            FOR EACH ROW EXECUTE FUNCTION auditoria_solo_insert()
        """
    );

    public static void createSchema(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            for (String sql : DDL) st.execute(sql);
        }
    }

    /** Crea el administrador semilla solo si no existe ningun administrador. Devuelve true si lo creo. */
    public static boolean ensureAdminSeed(Connection c) throws SQLException {
        try (PreparedStatement q = c.prepareStatement("SELECT 1 FROM users WHERE rol = 'administrador' LIMIT 1");
             ResultSet rs = q.executeQuery()) {
            if (rs.next()) return false;
        }
        String password = DbConfig.require("admin.password");
        if (password.length() < 8) throw new IllegalStateException("admin.password debe tener minimo 8 caracteres");
        String sql = "INSERT INTO users (ci, nombre, apellido, rol, telefono, email, password) "
                   + "VALUES (?, ?, ?, 'administrador', NULL, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, DbConfig.get("admin.ci", "00000000"));
            ps.setString(2, DbConfig.get("admin.nombre", "Admin"));
            ps.setString(3, DbConfig.get("admin.apellido", "Canal11"));
            ps.setString(4, DbConfig.require("admin.email").toLowerCase());
            ps.setString(5, BCrypt.hashpw(password, BCrypt.gensalt()));
            ps.executeUpdate();
        }
        return true;
    }
}
