package canal11.emision;

import canal11.testutil.DbTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EmisionDbTest extends DbTestBase {

    @TempDir Path dir;

    int material(String name, String tipo, int seg) throws Exception {
        Path f = dir.resolve(name + ".mp4");
        Files.writeString(f, name);
        String r = enc("INSMAT[0,\"" + name + "\",\"" + tipo + "\"," + seg + ",\"mp4\",\"" + f + "\"]");
        int id = Integer.parseInt(r.replaceAll("\\D*(\\d+) .*", "$1").trim());
        assertTrue(enc("VALMAT[" + id + "]").startsWith("OK"));
        return id;
    }

    /**
     * Parrilla 1 (2026-12-01): bloque 1 noticiero 20:00-21:00 (material), bloque 2 pausa 21:00-21:05 con un spot (spot 1 a las 21:00:00).
     * Pauta/cliente listos. Parrilla PUBLICADA.
     */
    @BeforeEach
    void escenario() throws Exception {
        int noti = material("noti", "programa", 3600);
        int sp = material("spot", "spot", 30);
        prog("INSPAR[\"2026-12-01\"]");
        prog("INSBLO[1,1,\"20:00\"," + noti + ",\"programa\"]");
        prog("INSBLO[1,3,\"21:00\",0,\"pausa_comercial\"]");
        prog("INSCLI[\"Banco Sol\",\"1023456011\",\"\",\"\",\"\"]");
        prog("INSPAU[1,\"Camp\",\"2026-12-01\",\"2026-12-31\",100,5]");
        assertTrue(prog("INSSPO[1," + sp + ",2,\"21:00:00\"]").startsWith("OK"));
        assertTrue(prog("PUBPAR[1]").startsWith("OK"));
    }

    @Test
    void registraEmisionConDesviacionYSincronizaSpot() {
        String r = prog("INSEMI[1,\"2026-12-01 20:00:20\",\"2026-12-01 21:00:00\",\"EMITIDO\"]");
        assertTrue(r.contains("desviacion 20 s") && r.contains("a tiempo"), r);
        String r2 = prog("INSEMI[\"spot:1\",\"2026-12-01 21:02:00\",\"-\",\"EMITIDO\",\"retraso por cortes\"]");
        assertTrue(r2.contains("desviacion 120 s") && !r2.contains("a tiempo"), r2);
        assertTrue(prog("LISSPO[1]").contains("EMITIDO"));
        assertTrue(prog("INSEMI[1,\"2026-12-01 20:00:00\",\"-\",\"EMITIDO\"]").contains("ya tiene una emision"));
        assertTrue(prog("LISEMI[1]").contains("retraso por cortes"));
    }

    @Test
    void conciliacionMarcaLoNoRegistradoComoNoEmitido() {
        String antes = prog("CONPAR[1]");
        assertTrue(antes.contains("NO_EMITIDO") && antes.contains("Cumplimiento: 0.0%"), antes);
        prog("INSEMI[1,\"2026-12-01 20:00:00\",\"2026-12-01 21:00:00\",\"EMITIDO\"]");
        String c = prog("CONPAR[1]");
        assertTrue(c.contains("Cumplimiento: 50.0%"), c);       // 1 de 2 elementos (bloque + spot)
        prog("INSEMI[\"spot:1\",\"2026-12-01 21:00:00\",\"2026-12-01 21:00:30\",\"NO_EMITIDO\",\"fallo de playout\"]");
        assertTrue(prog("LISSPO[1]").contains("NO_EMITIDO"));
        assertTrue(prog("UPDEMI[2,\"estado=EMITIDO\"]").startsWith("OK"));
        assertTrue(prog("CONPAR[1]").contains("Cumplimiento: 100.0%"));
    }

    @Test
    void validacionesYPermisos() {
        assertTrue(prog("INSEMI[1,\"ayer\",\"-\",\"EMITIDO\"]").contains("invalido"));
        assertTrue(prog("INSEMI[1,\"2026-12-01 20:00:00\",\"2026-12-01 19:00:00\",\"EMITIDO\"]").contains("posterior"));
        assertTrue(prog("INSEMI[1,\"2026-12-01 20:00:00\",\"-\",\"QUIZAS\"]").contains("estado invalido"));
        assertTrue(prog("INSEMI[99,\"2026-12-01 20:00:00\",\"-\",\"EMITIDO\"]").contains("no encontrado"));
        assertTrue(enc("INSEMI[1,\"2026-12-01 20:00:00\",\"-\",\"EMITIDO\"]").contains("Permiso denegado"));
        assertTrue(enc("CONPAR[1]").contains("Permiso denegado"));
    }

    @Test
    void parrillaEnBorradorNoAceptaEmisiones() {
        prog("INSPAR[\"2026-12-02\"]");
        prog("INSBLO[2,1,\"08:00\",0,\"programa\"]");
        assertTrue(prog("INSEMI[3,\"2026-12-02 08:00:00\",\"-\",\"EMITIDO\"]").contains("BORRADOR"));
    }

    @Test
    void auditoriaRegistraEmisionYCorreccion() {
        prog("INSEMI[1,\"2026-12-01 20:00:00\",\"-\",\"PARCIAL\"]");
        prog("UPDEMI[1,\"estado=EMITIDO\"]");
        String h = admin("GETAUD[\"emisiones_reales\",1]");
        assertTrue(h.contains("EMISION_REGISTRAR") && h.contains("EMISION_CORREGIR"), h);
        assertTrue(admin("LISAUD[\"2026-01-01\",\"2099-01-01\"]").contains("EMISION_CORREGIR"));
    }
}
