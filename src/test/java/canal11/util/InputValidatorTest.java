package canal11.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InputValidatorTest {

    @Test
    void ids() {
        assertEquals(5, InputValidator.validateId("5", "id"));
        assertThrows(CommandException.class, () -> InputValidator.validateId("0", "id"));
        assertThrows(CommandException.class, () -> InputValidator.validateId("abc", "id"));
        assertThrows(CommandException.class, () -> InputValidator.validateId(null, "id"));
    }

    @Test
    void fechas() {
        assertEquals(LocalDate.of(2026, 10, 20), InputValidator.validateDate("2026-10-20", "fecha"));
        assertThrows(CommandException.class, () -> InputValidator.validateDate("2026-13-40", "fecha"));
        assertThrows(CommandException.class, () -> InputValidator.validateDate("20/10/2026", "fecha"));
    }

    @Test
    void horas() {
        assertEquals(LocalTime.of(20, 0), InputValidator.validateTime("20:00", "hora"));
        assertEquals(LocalTime.of(8, 5, 3), InputValidator.validateTime("8:05:03", "hora"));
        assertThrows(CommandException.class, () -> InputValidator.validateTime("25:00", "hora"));
        assertThrows(CommandException.class, () -> InputValidator.validateTime("abc", "hora"));
    }

    @Test
    void enumsYTexto() {
        assertEquals("programador",
                InputValidator.validateEnum("PROGRAMADOR", "rol", List.of("administrador", "programador")));
        assertThrows(CommandException.class,
                () -> InputValidator.validateEnum("x", "rol", List.of("administrador")));
        assertThrows(CommandException.class, () -> InputValidator.validateText("  ", "nombre", 10));
        assertThrows(CommandException.class, () -> InputValidator.validateText("12345678901", "nombre", 10));
    }

    @Test
    void email() {
        assertEquals("a@b.com", InputValidator.validateEmail("A@B.com", "email"));
        assertThrows(CommandException.class, () -> InputValidator.validateEmail("sin-arroba", "email"));
    }
}
