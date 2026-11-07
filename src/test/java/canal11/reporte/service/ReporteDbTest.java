package canal11.reporte.service;

import canal11.testutil.DbTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ReporteDbTest extends DbTestBase {

    @TempDir Path dir;

    /** Rango que incluye hoy (los materiales y la auditoria se crean con la fecha actual). */
    static final String HOY_RANGO = "\"" + LocalDate.now().minusDays(1) + "\",\"" + LocalDate.now().plusDays(1) + "\"";

    int material(String name, String tipo, int seg, boolean validar) throws Exception {
        Path f = dir.resolve(name + ".mp4");
        Files.writeString(f, name);
        String r = enc("INSMAT[0,\"" + name + "\",\"" + tipo + "\"," + seg + ",\"mp4\",\"" + f + "\"]");
        int id = Integer.parseInt(r.replaceAll("\\D*(\\d+) .*", "$1").trim());
        if (validar) enc("VALMAT[" + id + "]");
        return id;
    }

    @BeforeEach
    void escenario() throws Exception {
        int noti = material("noti", "programa", 3600, true);
        int sp = material("spot", "spot", 30, true);
        material("pendiente", "promo", 20, false);
        prog("INSPAR[\"2026-12-01\"]");
        prog("INSBLO[1,1,\"20:00\"," + noti + ",\"programa\"]");        // 1 h noticiero
        prog("INSBLO[1,3,\"21:00\",0,\"pausa_comercial\"]");             // 5 min
        prog("INSCLI[\"Banco Sol\",\"1023456011\",\"\",\"\",\"\"]");
        prog("INSCLI[\"Tigo\",\"2023456011\",\"\",\"\",\"\"]");
        prog("INSPAU[1,\"A\",\"2026-12-01\",\"2026-12-31\",100,5]");
        prog("INSPAU[2,\"B\",\"2026-12-01\",\"2026-12-31\",50,5]");
        prog("INSSPO[1," + sp + ",2,\"21:00:00\"]");
        prog("INSSPO[2," + sp + ",2,\"21:01:00\"]");
        prog("PUBPAR[1]");
        prog("INSEMI[1,\"2026-12-01 20:00:00\",\"2026-12-01 21:00:00\",\"EMITIDO\"]");
        prog("INSEMI[\"spot:1\",\"2026-12-01 21:00:00\",\"-\",\"EMITIDO\"]");
    }

    @Test
    void reporteDeProgramacion() {
        String r = prog("REPPRO[\"2026-12-01\",\"2026-12-31\"]");
        assertTrue(r.contains("noticiero") && r.contains("1.00"), r);
        assertTrue(r.contains("2026-12-01") && r.contains("PUBLICADA"), r);
        assertTrue(prog("REPPRO[\"2027-01-01\",\"2027-01-31\"]").contains("sin parrillas"));
        assertTrue(enc("REPPRO[\"2026-12-01\",\"2026-12-31\"]").contains("Permiso denegado"));
    }

    @Test
    void reporteDeContenidos() {
        String r = enc("REPCON[" + HOY_RANGO + "]");
        assertTrue(r.contains("PENDIENTE") && r.contains("VALIDADO") && r.contains("pendiente"), r);
        assertTrue(prog("REPCON[" + HOY_RANGO + "]").contains("Permiso denegado"));
    }

    @Test
    void reporteDePautaPorClienteYMonto() {
        String r = prog("REPPAU[\"2026-12-01\",\"2026-12-31\"]");
        assertTrue(r.contains("Banco Sol") && r.contains("Tigo"), r);
        assertTrue(r.contains("100.00"), r);                               // un spot emitido de Banco Sol a 100
        String solo = prog("REPPAU[\"2026-12-01\",\"2026-12-31\",2]");
        assertTrue(solo.contains("Tigo") && !solo.contains("Banco Sol"), solo);
        assertTrue(prog("REPPAU[\"2026-12-01\",\"2026-12-31\",99]").contains("Cliente no encontrado"));
    }

    @Test
    void reporteDeCumplimiento() {
        String r = prog("REPCUM[\"2026-12-01\",\"2026-12-31\"]");
        assertTrue(r.contains("66.7%"), r);                                 // 3 elementos (bloque + 2 spots), 2 emitidos
    }

    @Test
    void actividadSoloAdministradorYValidaRangos() {
        assertTrue(admin("REPAUD[" + HOY_RANGO + "]").contains("prog@c.com"));
        assertTrue(prog("REPAUD[" + HOY_RANGO + "]").contains("Permiso denegado"));
        assertTrue(admin("REPPRO[\"2026-12-31\",\"2026-12-01\"]").contains("anterior"));
        assertTrue(admin("REPPRO[\"2020-01-01\",\"2026-12-01\"]").contains("rango maximo"));
        assertTrue(admin("REPPRO[\"hoy\",\"2026-12-01\"]").contains("invalida"));
    }

    @Test
    void resumenDelDiaYAuditoriaDeReportes() {
        String sinParrilla = enc("RESDIA[\"*\"]");
        assertTrue(sinParrilla.contains("NO CREADA") && sinParrilla.contains("pendientes de validacion: 1"), sinParrilla);
        prog("INSPAR[\"" + LocalDate.now() + "\"]");
        assertTrue(enc("RESDIA[\"*\"]").contains("Parrilla de hoy: ID"));
        assertTrue(admin("LISAUD[\"2000-01-01\",\"2099-12-31\"]").contains("REPORTE") || admin("LISAUD[\"*\"]").length() > 0);
        prog("REPPRO[\"2026-12-01\",\"2026-12-31\"]");
        assertTrue(admin("LISAUD[\"2000-01-01\",\"2099-12-31\"]").contains("REPPRO"));
    }
}
