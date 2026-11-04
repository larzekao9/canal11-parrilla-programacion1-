package canal11.mail;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SubjectParserTest {

    @Test
    void comandoEnMayusculas() {
        assertEquals("INSUSU", SubjectParser.extraerComando("insusu[\"a\"]"));
        assertEquals("HELP", SubjectParser.extraerComando("  Help [] "));
    }

    @Test
    void subjectInvalido() {
        assertFalse(SubjectParser.esValido("hola"));
        assertFalse(SubjectParser.esValido("HELP"));
        assertFalse(SubjectParser.esValido("[\"x\"]"));
        assertFalse(SubjectParser.esValido(null));
        assertEquals("", SubjectParser.extraerComando("hola que tal"));
        assertTrue(SubjectParser.extraerParametros("hola").isEmpty());
    }

    @Test
    void sinParametros() {
        assertEquals(List.of(), SubjectParser.extraerParametros("HELP[]"));
    }

    @Test
    void asterisco() {
        assertEquals(List.of("*"), SubjectParser.extraerParametros("LISUSU[*]"));
        assertEquals(List.of("*"), SubjectParser.extraerParametros("LISUSU[\"*\"]"));
    }

    @Test
    void textosYNumeros() {
        assertEquals(List.of("Noticiero Central", "60", "informativo"),
                SubjectParser.extraerParametros("INSPRG[\"Noticiero Central\",60,\"informativo\"]"));
    }

    @Test
    void comasDentroDeComillas() {
        assertEquals(List.of("Perez, Juan", "x"),
                SubjectParser.extraerParametros("INSUSU[\"Perez, Juan\",\"x\"]"));
    }

    @Test
    void espaciosAlrededorDeComas() {
        assertEquals(List.of("a", "b", "3"), SubjectParser.extraerParametros("CMD[ \"a\" , \"b\" , 3 ]"));
    }

    @Test
    void comillasEscapadas() {
        assertEquals(List.of("dijo \"hola\"", "z"),
                SubjectParser.extraerParametros("CMD[\"dijo \\\"hola\\\"\",\"z\"]"));
    }

    @Test
    void jsonConComillasInternasSinEscapar() {
        assertEquals(List.of("{\"a\":1}", "ok"),
                SubjectParser.extraerParametros("CMD[\"{\"a\":1}\",\"ok\"]"));
    }

    @Test
    void jsonSinComillasExternas() {
        assertEquals(List.of("{\"a\":1,\"b\":2}", "ok"),
                SubjectParser.extraerParametros("CMD[{\"a\":1,\"b\":2},\"ok\"]"));
    }

    @Test
    void campoValor() {
        assertEquals(List.of("5", "telefono=70000000", "rol=programador"),
                SubjectParser.extraerParametros("UPDUSU[\"5\",\"telefono=70000000\",\"rol=programador\"]"));
    }

    @Test
    void parametroVacio() {
        assertEquals(List.of("a", ""), SubjectParser.extraerParametros("CMD[\"a\",\"\"]"));
    }
}
