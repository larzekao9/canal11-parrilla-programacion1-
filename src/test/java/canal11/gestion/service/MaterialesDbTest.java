package canal11.gestion.service;

import canal11.testutil.DbTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** CU4 + CU5. */
class MaterialesDbTest extends DbTestBase {

    @TempDir Path dir;

    String video(String name, String contenido) throws Exception {
        Path f = dir.resolve(name);
        Files.writeString(f, contenido);
        return f.toString();
    }

    @Test
    void registraYDetectaDuplicadosPorHash() throws Exception {
        String a = video("a.mp4", "contenido-A");
        String copia = video("copia.mp4", "contenido-A");
        assertEquals("OK: material registrado con ID 1 (estado PENDIENTE)",
                enc("INSMAT[1,\"Noticiero 20-10\",\"programa\",3600,\"mp4\",\"" + a + "\"]"));
        String dup = enc("INSMAT[1,\"Otra copia\",\"programa\",3600,\"mp4\",\"" + copia + "\"]");
        assertTrue(dup.contains("duplicado, ya registrado como ID 1"), dup);
        assertTrue(enc("INSMAT[0,\"X\",\"spot\",30,\"mp4\",\"" + dir.resolve("noexiste.mp4") + "\"]").contains("no existe"));
        assertTrue(enc("INSMAT[0,\"X\",\"spot\",30,\"mov\",\"" + a + "\"]").contains("extension"));
        assertTrue(enc("INSMAT[99,\"X\",\"spot\",30,\"mp4\",\"" + video("b.mp4", "B") + "\"]").contains("programa_id"));
        assertTrue(prog("LISMAT[\"PENDIENTE\"]").contains("Noticiero 20-10"));   // programador solo lee
        assertTrue(prog("INSMAT[0,\"X\",\"spot\",30,\"mp4\",\"" + a + "\"]").contains("Permiso denegado"));
    }

    @Test
    void flujoDeValidacion() throws Exception {
        enc("INSMAT[0,\"Spot 1\",\"spot\",30,\"mp4\",\"" + video("s.mp4", "spot") + "\"]");
        assertTrue(enc("RECMAT[1,\"\"]").contains("Falta el campo motivo"));
        assertTrue(enc("VALMAT[1]").startsWith("OK: material 1 VALIDADO"));
        assertTrue(enc("VALMAT[1]").contains("ya esta VALIDADO"));
        assertTrue(enc("RECMAT[1,\"mala calidad\"]").startsWith("OK"));
        assertTrue(enc("VALMAT[1]").contains("RECHAZADO"));
        // corregir con UPDMAT lo devuelve a PENDIENTE y se puede validar otra vez
        assertTrue(enc("UPDMAT[1,\"duracion_seg=31\"]").contains("vuelve a PENDIENTE"));
        assertTrue(enc("VALMAT[1,\"ok\"]").startsWith("OK"));
        assertTrue(enc("GETMAT[1]").contains("VALIDADO"));
        assertTrue(prog("VALMAT[1]").contains("Permiso denegado"));
    }

    @Test
    void validarFallaSiElArchivoCambioOSeBorro() throws Exception {
        String f = video("v.mp4", "original");
        enc("INSMAT[0,\"V\",\"promo\",20,\"mp4\",\"" + f + "\"]");
        Files.writeString(Path.of(f), "modificado");
        assertTrue(enc("VALMAT[1]").contains("hash distinto"));
        Files.delete(Path.of(f));
        assertTrue(enc("VALMAT[1]").contains("no existe"));
    }

    @Test
    void bajaYAuditoria() throws Exception {
        enc("INSMAT[0,\"V\",\"promo\",20,\"mp4\",\"" + video("v.mp4", "x") + "\"]");
        assertTrue(enc("DELMAT[1]").startsWith("OK"));
        assertFalse(enc("LISMAT[\"*\"]").contains("| V "));
        assertTrue(admin("GETAUD[\"materiales\",1]").contains("MATERIAL_BAJA"));
    }
}
