package canal11.programacion;

import canal11.testutil.DbTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ParrillaDbTest extends DbTestBase {

    @TempDir Path dir;

    /** Registra y valida un material de la duracion dada; devuelve su id. */
    int material(String name, String tipo, int seg) throws Exception {
        Path f = dir.resolve(name + ".mp4");
        Files.writeString(f, name);
        String r = enc("INSMAT[0,\"" + name + "\",\"" + tipo + "\"," + seg + ",\"mp4\",\"" + f + "\"]");
        int id = Integer.parseInt(r.replaceAll("\\D*(\\d+) .*", "$1").trim());
        assertTrue(enc("VALMAT[" + id + "]").startsWith("OK"), "validar " + name);
        return id;
    }

    @Test
    void creaParrillaYBloquesSinSolape() throws Exception {
        assertTrue(prog("INSPAR[\"2026-12-01\"]").contains("ID 1"));
        assertTrue(prog("INSPAR[\"2026-12-01\"]").contains("Ya existe"));
        int m = material("noti", "programa", 3600);
        // programa 1 = Noticiero (60 min): 20:00 - 21:00
        assertTrue(prog("INSBLO[1,1,\"20:00\"," + m + ",\"programa\"]").contains("20:00:00 - 21:00:00"));
        assertTrue(prog("INSBLO[1,2,\"20:30\",0,\"programa\"]").contains("se solapa"));
        assertTrue(prog("INSBLO[1,3,\"21:00\",0,\"pausa_comercial\"]").startsWith("OK"));      // 21:00-21:05, sin solape (borde)
        assertTrue(prog("INSBLO[1,3,\"21:05\"," + m + ",\"pausa_comercial\"]").contains("no lleva material"));
        String g = prog("GETPAR[1]");
        assertTrue(g.contains("20:00:00") && g.contains("pausa_comercial"), g);
        assertTrue(enc("GETPAR[1]").contains("BORRADOR"));                                       // encargado lee
        assertTrue(enc("INSBLO[1,1,\"10:00\",0,\"programa\"]").contains("Permiso denegado"));
    }

    @Test
    void horaFinPorDefectoYMedianoche() {
        prog("INSPAR[\"2026-12-01\"]");
        assertTrue(prog("INSBLO[1,2,\"23:30\",0,\"programa\"]").contains("cruza la medianoche"));   // 90 min
        assertTrue(prog("INSBLO[1,2,\"23:30\",0,\"programa\",\"23:59:59\"]").startsWith("OK"));
        assertTrue(prog("INSBLO[1,1,\"10:00\",0,\"programa\",\"09:00\"]").contains("mayor a hora_inicio"));
        assertTrue(prog("INSBLO[1,99,\"10:00\",0,\"programa\"]").contains("programa_id"));
    }

    @Test
    void validarMuestraHuecosYProblemas() throws Exception {
        prog("INSPAR[\"2026-12-01\"]");
        assertTrue(prog("VALPAR[1]").contains("no tiene bloques"));
        prog("INSBLO[1,1,\"08:00\",0,\"programa\"]");
        String v = prog("VALPAR[1]");
        assertTrue(v.contains("no tiene material"), v);
        assertTrue(v.contains("00:00:00 - 08:00:00"), v);
        assertTrue(prog("PUBPAR[1]").contains("No se puede publicar"));
    }

    @Test
    void publicarVersionarYCerrar() throws Exception {
        int m = material("n1", "programa", 3600);
        prog("INSPAR[\"2026-12-01\"]");
        prog("INSBLO[1,1,\"08:00\"," + m + ",\"programa\"]");
        assertTrue(prog("PUBPAR[1]").startsWith("OK: parrilla 1 PUBLICADA"));
        assertTrue(prog("PUBPAR[1]").contains("Solo se publican"));
        assertTrue(prog("DELPAR[1]").contains("BORRADOR"));
        // parrilla publicada: el bloque nuevo exige material VALIDADO
        assertTrue(prog("INSBLO[1,2,\"10:00\",0,\"programa\"]").contains("PUBLICADA"));
        int m2 = material("n2", "programa", 5400);
        assertTrue(prog("INSBLO[1,2,\"10:00\"," + m2 + ",\"programa\"]").startsWith("OK"));
        assertTrue(prog("LISPAR[\"*\"]").contains("2"));    // version 2
        assertTrue(admin("GETAUD[\"parrillas\",1]").contains("PARRILLA_MODIFICADA"));
        assertTrue(prog("CERPAR[1]").startsWith("OK"));
        assertTrue(prog("INSBLO[1,2,\"15:00\"," + m2 + ",\"programa\"]").contains("CERRADA"));
        assertTrue(prog("UPDBLO[1,\"hora_inicio=09:00\"]").contains("CERRADA"));
    }

    @Test
    void editarMoverYEliminarBloque() throws Exception {
        prog("INSPAR[\"2026-12-01\"]");
        prog("INSBLO[1,1,\"08:00\",0,\"programa\"]");           // 08:00-09:00
        prog("INSBLO[1,3,\"09:00\",0,\"pausa_comercial\"]");     // 09:00-09:05
        assertTrue(prog("UPDBLO[1,\"hora_inicio=08:30\"]").contains("solapa"));
        assertTrue(prog("UPDBLO[1,\"hora_inicio=07:00\"]").contains("07:00:00 - 08:00:00"));
        assertTrue(prog("UPDBLO[2,\"tipo=programa\"]").startsWith("OK"));
        assertTrue(prog("DELBLO[2]").startsWith("OK"));
        assertTrue(prog("DELBLO[2]").contains("no encontrado"));
        assertTrue(prog("DELPAR[1]").startsWith("OK"));
    }

    @Test
    void duplicarParrilla() throws Exception {
        prog("INSPAR[\"2026-12-01\"]");
        prog("INSBLO[1,1,\"08:00\",0,\"programa\"]");
        prog("INSBLO[1,3,\"09:00\",0,\"pausa_comercial\"]");
        assertTrue(prog("DUPPAR[1,\"2026-12-08\"]").contains("copiando 2 bloque"));
        assertTrue(prog("GETPAR[2]").contains("08:00:00"));
        assertTrue(prog("DUPPAR[1,\"2026-12-08\"]").contains("Ya existe"));
        assertTrue(prog("LISPAR[\"2026-12-01\",\"2026-12-31\"]").contains("2026-12-08"));
    }

    @Test
    void estadoDeEmisionYRechazoEnParrillaPublicada() throws Exception {
        int m = material("n1", "programa", 3600);
        prog("INSPAR[\"2026-12-01\"]");
        prog("INSBLO[1,1,\"08:00\"," + m + ",\"programa\"]");
        prog("PUBPAR[1]");
        assertTrue(enc("ESTEMI[1]").contains("LISTO"));
        String r = enc("RECMAT[" + m + ",\"corrupto\"]");
        assertTrue(r.contains("ALERTA"), r);
        assertTrue(enc("ESTEMI[1]").contains("ALERTA"));
    }
}
