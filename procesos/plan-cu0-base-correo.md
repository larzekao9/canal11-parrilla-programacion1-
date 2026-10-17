PLAN DE IMPLEMENTACION - CU0 BASE DEL SISTEMA DE CORREO (INFRAESTRUCTURA)

OBJETIVO
- Montar el proyecto Laravel + PostgreSQL y el motor que recibe comandos por correo y responde por correo.

ALCANCE
- Comandos: AUTH, HELP.
- Todas las migraciones y el seed.
- Daemon POP3/SMTP, parser de Subject, router con roles.

SETUP
- composer create-project laravel/laravel canal11
- .env: DB_CONNECTION=pgsql, DB_HOST, DB_PORT=5432, DB_DATABASE=canal11, DB_USERNAME, DB_PASSWORD
- .env (buzon): MAIL_POP3_HOST, MAIL_POP3_PORT=110, MAIL_SMTP_HOST, MAIL_SMTP_PORT, MAIL_USER, MAIL_PASS, MAIL_FROM
  (si se usa tunel SSH: SMTP_HOST=localhost, SMTP_PORT=2525 con ssh -L 2525:mail.tecnoweb.org.bo:25 root@<ip_vps> -N)
- config/mailcommands.php lee esas variables.

COMPONENTES
1) App\Mail\Pop3Client: conectar (USER/PASS), STAT, LIST, RETR, DELE, QUIT. Devuelve MailMessage(from, subject, body).
2) App\Mail\SmtpClient: enviar texto plano al remitente original (asunto "RE: <subject>").
3) App\Mail\SubjectParser:
   - extraerComando(subject): texto antes de "[" en mayusculas.
   - extraerParametros(subject): contenido entre "[" y el ultimo "]"; "*" => ["*"]; soporta strings con comas y JSON con comillas internas.
4) App\Mail\CommandRouter:
   - process(command, params, senderEmail): busca usuario por email -> rol -> permiso -> sesion AUTH -> despacha a Service.
   - Sets de comandos por rol (ADMIN/PROGRAMADOR/ENCARGADO). Mensajes: "correo no registrado", "Permiso denegado para rol: X. Use HELP[].",
     "Autenticacion requerida. Envie AUTH[\"password\"] para habilitar 30 minutos."
   - Sesiones AUTH en tabla auth_sessions (expiran a los 30 min) para sobrevivir reinicios del daemon.
5) App\Console\Commands\MailDaemon (php artisan mail:daemon): bucle -> leer correos -> procesar -> responder -> DELE -> sleep(10).
   Un error en un correo no debe detener el bucle; se responde con el error.
6) App\Support\InputValidator: validateId, validateText, validateDate (YYYY-MM-DD), validateTime (HH:MM[:SS]), validatePositive, validateEnum.
7) Respuestas: texto plano con "OK: ..." o "Error: ...". Listados como tabla de texto alineada.

MIGRACIONES (todas, ver modelo en guia-actualizacion-y-handoff.md)
- users, auth_sessions, programas, materiales, parrillas, parrilla_bloques, clientes, pautas, pauta_spots, emisiones_reales, auditoria_logs.
- Indices: materiales.hash_sha256 unique, parrilla_bloques(parrilla_id, hora_inicio), auditoria_logs(entidad, entidad_id).

SEED
- UserSeeder: administrador semilla si no existe ninguno (email y password definidos en .env o seeder; cambiar tras el primer uso).

REGLAS
- Solo se lee el Subject; el Body se ignora.
- Comandos insensibles a mayusculas/minusculas.
- Subject invalido => "Comando no reconocido".

PRUEBAS MINIMAS
- php artisan migrate --seed crea tablas y admin.
- Enviar HELP[] desde el correo del admin y recibir lista de comandos.
- Remitente no registrado => error. Comando sin AUTH => pide AUTH. AUTH correcto => habilita 30 min.
- Tests PHPUnit de SubjectParser y CommandRouter (sin red, con Pop3/Smtp simulados).

CRITERIO DE ACEPTACION
- El daemon procesa un correo real extremo a extremo y responde al remitente.
- Roles y AUTH funcionan; el daemon no se cae ante un correo malformado.
