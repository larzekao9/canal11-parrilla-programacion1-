package canal11.util;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.regex.Pattern;

/** Validaciones de parametros de comandos. Todas lanzan CommandException con mensaje claro. */
public final class InputValidator {

    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern TIME = Pattern.compile("^\\d{1,2}:\\d{2}(:\\d{2})?$");

    private InputValidator() {}

    public static int validateId(String value, String field) {
        int n = validatePositive(value, field);
        return n;
    }

    public static int validatePositive(String value, String field) {
        if (value == null || value.isBlank()) throw new CommandException("Falta el campo " + field);
        try {
            int n = Integer.parseInt(value.trim());
            if (n <= 0) throw new CommandException(field + " debe ser un numero mayor a 0");
            return n;
        } catch (NumberFormatException e) {
            throw new CommandException(field + " debe ser un numero entero");
        }
    }

    /** Entero >= 0 (por ejemplo "programa_id|0"). */
    public static int validateNonNegative(String value, String field) {
        if (value == null || value.isBlank()) throw new CommandException("Falta el campo " + field);
        try {
            int n = Integer.parseInt(value.trim());
            if (n < 0) throw new CommandException(field + " no puede ser negativo");
            return n;
        } catch (NumberFormatException e) {
            throw new CommandException(field + " debe ser un numero entero");
        }
    }

    public static String validateText(String value, String field, int maxLen) {
        if (value == null || value.isBlank()) throw new CommandException("Falta el campo " + field);
        String v = value.trim();
        if (v.length() > maxLen) throw new CommandException(field + " excede " + maxLen + " caracteres");
        return v;
    }

    public static LocalDate validateDate(String value, String field) {
        if (value == null || value.isBlank()) throw new CommandException("Falta el campo " + field);
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new CommandException(field + " invalida, use formato YYYY-MM-DD");
        }
    }

    public static LocalTime validateTime(String value, String field) {
        if (value == null || value.isBlank()) throw new CommandException("Falta el campo " + field);
        String v = value.trim();
        if (!TIME.matcher(v).matches()) throw new CommandException(field + " invalida, use formato HH:MM[:SS]");
        try {
            // LocalTime.parse exige 2 digitos en la hora
            return LocalTime.parse(v.length() == 4 || v.length() == 7 ? "0" + v : v);
        } catch (DateTimeParseException e) {
            throw new CommandException(field + " invalida, use formato HH:MM[:SS]");
        }
    }

    public static String validateEnum(String value, String field, Collection<String> allowed) {
        if (value == null || value.isBlank()) throw new CommandException("Falta el campo " + field);
        String v = value.trim().toLowerCase();
        for (String a : allowed) {
            if (a.equalsIgnoreCase(v)) return a;
        }
        throw new CommandException(field + " invalido, valores permitidos: " + String.join(", ", allowed));
    }

    public static String validateEmail(String value, String field) {
        String v = validateText(value, field, 120);
        if (!EMAIL.matcher(v).matches()) throw new CommandException(field + " con formato invalido");
        return v.toLowerCase();
    }
}
