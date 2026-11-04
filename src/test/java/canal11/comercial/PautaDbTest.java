package canal11.comercial;

import canal11.testutil.DbTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PautaDbTest extends DbTestBase {

    @TempDir Path dir;

    int spot(String name, String tipo, int seg, boolean validar) throws Exception {
        Path f = dir.resolve(name + ".mp4");
        Files.writeString(f, name);
        String r = enc("INSMAT[0,\"" + name + "\",\"" + tipo + "\"," + seg + ",\"mp4\",\"" + f + "\"]");
        int id = Integer.parseInt(r.replaceAll("\\D*(\\d+) .*", "$1").trim());
        if (validar) enc("VALMAT[" + id + "]");
        return id;
    }

    /** Parrilla 1 (2026-12-01) con una pausa comercial 21:00 - 21:05 (300 s) = bloque 1; cliente 1; pauta 1 vigente en diciembre. */
    @BeforeEach
    void escenario() {
        prog("INSPAR[\"2026-12-01\"]");
        prog("INSBLO[1,3,\"21:00\",0,\"pausa_comercial\"]");
        assertTrue(prog("INSCLI[\"Banco Sol SA\",\"1023456011\",\"Maria\",\"pauta@bancosol.bo\",\"71111111\"]").startsWith("OK"));
        assertTrue(prog("INSPAU[1,\"Campana Diciembre\",\"2026-12-01\",\"2026-12-31\",150.50,3]").startsWith("OK"));
    }

    @Test
    void clientesCrud() {
        assertTrue(prog("INSCLI[\"Otro\",\"1023456011\",\"\",\"\",\"\"]").contains("NIT"));
        assertTrue(prog("INSCLI[\"X\",\"123\",\"\",\"\",\"\"]").contains("nit invalido"));
        assertTrue(prog("UPDCLI[1,\"contacto=Juan\"]").startsWith("OK"));
        assertTrue(enc("GETCLI[1]").contains("Juan"));
        assertTrue(prog("DELCLI[1]").contains("pautas ACTIVAS"));
        assertTrue(prog("UPDPAU[1,\"estado=FINALIZADA\"]").startsWith("OK"));
        assertTrue(prog("DELCLI[1]").startsWith("OK"));
        assertTrue(prog("INSPAU[1,\"X\",\"2026-12-01\",\"2026-12-31\",10,1]").contains("inactivo"));
    }

    @Test
    void insertaSpotValidoYReglas() throws Exception {
        int ok = spot("spot_ok", "spot", 30, true);
        int pend = spot("spot_pend", "spot", 30, false);
        int prg = spot("no_es_spot", "programa", 30, true);
        assertTrue(prog("INSSPO[1," + ok + ",1,\"21:00:00\"]").startsWith("OK: spot 1"));
        assertTrue(prog("INSSPO[1," + ok + ",1,\"21:00:15\"]").contains("se solapa"));
        assertTrue(prog("INSSPO[1," + pend + ",1,\"21:01:00\"]").contains("no esta VALIDADO"));
        assertTrue(prog("INSSPO[1," + prg + ",1,\"21:01:00\"]").contains("no es de tipo spot"));
        assertTrue(prog("INSSPO[1," + ok + ",1,\"21:04:45\"]").contains("no cabe"));        // 21:04:45 + 30s > 21:05
        assertTrue(prog("INSSPO[1," + ok + ",1,\"20:59:30\"]").contains("no cabe"));
        assertTrue(prog("INSSPO[1," + ok + ",1,\"21:00:30\"]").startsWith("OK"));
        assertTrue(enc("INSSPO[1," + ok + ",1,\"21:01:00\"]").contains("Permiso denegado"));
    }

    @Test
    void vigenciaPasesYTipoDeBloque() throws Exception {
        int s = spot("s", "spot", 10, true);
        prog("INSPAR[\"2027-01-05\"]");
        prog("INSBLO[2,3,\"21:00\",0,\"pausa_comercial\"]");
        prog("INSBLO[2,1,\"22:00\",0,\"programa\"]");
        assertTrue(prog("INSSPO[1," + s + ",2,\"21:00:00\"]").contains("fuera de la vigencia"));
        assertTrue(prog("INSSPO[1," + s + ",3,\"22:00:00\"]").contains("no es una pausa"));
        assertTrue(prog("INSSPO[1," + s + ",1,\"21:00:00\"]").startsWith("OK"));
        assertTrue(prog("INSSPO[1," + s + ",1,\"21:01:00\"]").startsWith("OK"));
        assertTrue(prog("INSSPO[1," + s + ",1,\"21:02:00\"]").startsWith("OK"));
        assertTrue(prog("INSSPO[1," + s + ",1,\"21:03:00\"]").contains("pases contratados"));
        assertTrue(prog("UPDPAU[1,\"max_pases=2\"]").contains("menor que los spots"));
        assertTrue(prog("UPDPAU[1,\"fecha_inicio=2026-12-02\"]").contains("fuera de la nueva vigencia"));
    }

    @Test
    void pausaPuedeQuitarSpotsYConsultarPauta() throws Exception {
        int s = spot("s", "spot", 60, true);
        prog("INSSPO[1," + s + ",1,\"21:00:00\"]");
        assertTrue(prog("LISSPO[1]").contains("PROGRAMADO"));
        String pz = enc("PAUPAR[1]");
        assertTrue(pz.contains("usados 60 s de 300 s") && pz.contains("Banco Sol SA"), pz);
        assertTrue(prog("GETPAU[1]").contains("Pases disponibles: 2"));
        assertTrue(prog("DELBLO[1]").contains("tiene spots"));
    }

    @Test
    void movimientoDePausaConSpotsFueraDeHorarioSeRechaza() throws Exception {
        int s = spot("s", "spot", 60, true);
        prog("INSSPO[1," + s + ",1,\"21:00:00\"]");
        assertTrue(prog("UPDBLO[1,\"hora_inicio=21:30\"]").contains("fuera del nuevo horario"));
        assertTrue(prog("UPDBLO[1,\"tipo=programa\"]").contains("pausa_comercial"));
        assertTrue(prog("DELSPO[1]").startsWith("OK"));
        assertTrue(prog("DELPAU[1]").startsWith("OK"));
    }
}
