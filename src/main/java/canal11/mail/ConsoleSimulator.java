package canal11.mail;

import java.util.Scanner;

/**
 * Simulador interactivo: escribe el Subject como si fuera un correo y muestra la respuesta.
 * Usa el mismo router (y la misma BD) que el daemon.
 *
 *   mvn -q compile exec:java -Dexec.mainClass=canal11.mail.ConsoleSimulator
 *
 * Comandos del simulador:  :de correo@x.com  (cambia el remitente)   :salir
 */
public class ConsoleSimulator {

    public static void main(String[] args) {
        CommandRouter router = RouterFactory.create();
        Scanner in = new Scanner(System.in);
        String from = args.length > 0 ? args[0] : null;
        if (from == null) {
            System.out.print("Remitente (email): ");
            from = in.nextLine().trim();
        }
        System.out.println("Remitente: " + from + "   (:de otro@correo.com para cambiar, :salir para terminar)");
        while (true) {
            System.out.print("Subject> ");
            if (!in.hasNextLine()) break;
            String line = in.nextLine().trim();
            if (line.isEmpty()) continue;
            if (line.equalsIgnoreCase(":salir")) break;
            if (line.toLowerCase().startsWith(":de ")) {
                from = line.substring(4).trim();
                System.out.println("Remitente: " + from);
                continue;
            }
            System.out.println(router.process(line, from));
            System.out.println();
        }
    }
}
