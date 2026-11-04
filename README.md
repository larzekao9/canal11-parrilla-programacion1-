# canal11-parrilla-programacion

Sistema de gestión de parrilla de programación de Canal 11, operado por comandos vía correo electrónico (el Subject es el comando).
Java 17 + Maven + PostgreSQL. Tecnología Web (INF513) – Grupo #12.

## Contenido
- `docs/levantamiento/` – levantamiento de información (AS-IS) del canal.
- `procesos/` – guía de handoff, plan base, planes por caso de uso (CU1–CU8), plan por fases y runbook.

Orden de lectura: `procesos/guia-actualizacion-y-handoff.md` → `procesos/plan-por-fases.md`.

## Probar el sistema

1. Copiar `config.properties.example` a `config.properties` y completar BD (`db.*`), admin semilla (`admin.*`) y correo (`mail.*`). Ese archivo NO se sube a git.
2. Crear tablas y admin: `mvn -q compile exec:java -Dexec.mainClass=canal11.App`
3. Verificar conexiones (BD, POP3, SMTP; sin enviar nada): `mvn -q compile exec:java -Dexec.mainClass=canal11.Doctor`
4. Probar comandos sin correo, escribiendo el Subject: `mvn -q compile exec:java -Dexec.mainClass=canal11.mail.ConsoleSimulator`
   (ej. `AUTH["password"]`, `HELP[]`, `LISUSU["*"]`)
5. Daemon: `mvn -q compile exec:java -Dexec.mainClass=canal11.mail.MailDaemon`
   - `mail.modo=simulado` (por defecto): lee `buzon-simulado/entrada/*.txt`.
   - `mail.modo=real`: lee por POP3 y responde por SMTP del servidor del curso. Si el SMTP va por tunel SSH:
     `ssh -L 2525:mail.tecnoweb.org.bo:25 root@<ip_vps> -N` y `mail.smtp.host=localhost`, `mail.smtp.port=2525`.
6. Tests: `mvn test`. Los tests con BD solo corren si `DB_URL` termina en `_test` (borran el esquema `public`).
