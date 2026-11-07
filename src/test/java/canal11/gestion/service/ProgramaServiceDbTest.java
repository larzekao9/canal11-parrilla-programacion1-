package canal11.gestion.service;

import canal11.testutil.DbTestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProgramaServiceDbTest extends DbTestBase {

    @Test
    void crudYFiltroPorTipo() {
        assertEquals("OK: programa creado con ID 4", prog("INSPRG[\"Cine Noche\",\"Peliculas\",\"pelicula\",120,\"accion\",\"+13\"]"));
        assertTrue(prog("LISPRG[\"pelicula\"]").contains("Cine Noche"));
        assertFalse(prog("LISPRG[\"pelicula\"]").contains("Noticiero Central"));
        assertTrue(prog("GETPRG[4]").contains("accion"));
        assertTrue(prog("UPDPRG[4,\"duracion_min=90\"]").startsWith("OK"));
        assertTrue(enc("LISPRG[\"*\"]").contains("90"));   // encargado puede leer
    }

    @Test
    void validaciones() {
        assertTrue(prog("INSPRG[\"X\",\"\",\"circo\",60,\"\",\"\"]").contains("tipo invalido"));
        assertTrue(prog("INSPRG[\"X\",\"\",\"serie\",0,\"\",\"\"]").contains("mayor a 0"));
        assertTrue(prog("INSPRG[\"Noticiero Central\",\"\",\"noticiero\",60,\"\",\"\"]").contains("Ya existe"));
        assertTrue(prog("GETPRG[999]").contains("no encontrado"));
    }

    @Test
    void encargadoNoPuedeEscribir() {
        assertTrue(enc("INSPRG[\"X\",\"\",\"serie\",30,\"\",\"\"]").contains("Permiso denegado"));
        assertTrue(enc("DELPRG[1]").contains("Permiso denegado"));
    }

    @Test
    void desactivarYAuditar() {
        assertTrue(prog("DELPRG[1]").startsWith("OK"));
        assertTrue(prog("GETPRG[1]").contains("no"));
        assertTrue(admin("GETAUD[\"programas\",1]").contains("PROGRAMA_DESACTIVAR"));
    }
}
