PLAN DE IMPLEMENTACION - CU0 BASE DEL SISTEMA DE CORREO (INFRAESTRUCTURA)

OBJETIVO
- Montar el proyecto Java + PostgreSQL y el motor que recibe comandos por correo y responde por correo (mismo esquema que servicargo).

ALCANCE
- Comandos: AUTH, HELP.
- Creacion de todas las tablas (SchemaInitializer) y admin semilla.
- Daemon POP3/SMTP, parser de Subject, router con roles.

SETUP
- Java 17 + Maven. pom.xml con driver PostgreSQL (org.postgresql:postgresql) y jbcrypt (hash de passwords); JUnit 5 para tests.
- Paquete base: canal11.
- Estructura:
  canal11/App.java                      (crea tablas + seed)
  canal11/db/        DbConfig, DbConnection, SchemaInitializer
  canal11/mail/      MailConfig, Pop3Client, SmtpClient, MailDaemon, MailReader, MailSender, SubjectParser, CommandRouter
  canal11/model/     Usuario, Programa, Material, Parrilla, Bloque, Cliente, Pauta, Spot, Emision
  canal11/gestion/   repository + service (usuarios, programas, materiales)
  canal11/programacion/ repository + service (parrilla, bloques, validacion)
  canal11/comercial/ repository + service (clientes, pautas, spots)
  canal11/emision/   repository + service (emisiones reales, conciliacion, auditoria)
  canal11/reporte/   repository + service
  canal11/util/      InputValidator, BodyParser
- Configuracion de BD (DbConfig) y de buzon (MailConfig) leen variables de entorno o un archivo local config.properties
  (ignorado por git). Credenciales NUNCA en el repositorio.
- Si se usa tunel SSH para SMTP: SMTP_SERVER=localhost, SMTP_PORT=2525 con ssh -L 2525:mail.tecnoweb.org.bo:25 root@<ip_vps> -N

COMPONENTES
1) Pop3Client: conectar por socket (USER/PASS), STAT, LIST, RETR, DELE, QUIT. Devuelve MailMessage(from, subject, body).
2) SmtpClient: enviar texto plano al remitente original (asunto "RE: <subject>").
3) SubjectParser:
   - extraerComando(subject): texto antes de "[" en mayusculas.
   - extraerParametros(subject): contenido entre "[" y el ultimo "]"; "*" => ["*"]; soporta strings con comas y JSON con comillas internas.
4) CommandRouter:
   - process(command, params, senderEmail): busca usuario por email -> rol -> permiso -> sesion AUTH -> despacha a Service (switch por comando).
   - Sets de comandos por rol (ADMIN / PROGRAMADOR / ENCARGADO). Mensajes: "correo no registrado", "Permiso denegado para rol: X. Use HELP[].",
     "Autenticacion requerida. Envie AUTH[\"password\"] para habilitar 30 minutos."
   - Sesiones AUTH en memoria (ConcurrentHashMap) con ventana de 30 min, o tabla auth_sessions si se quiere que sobrevivan reinicios.
5) MailDaemon (main): bucle -> leer correos -> procesar -> responder -> DELE -> sleep 10 s.
   Un error en un correo no debe detener el bucle; se responde con el error.
6) InputValidator: validateId, validateText, validateDate (YYYY-MM-DD), validateTime (HH:MM[:SS]), validatePositive, validateEnum.
7) Respuestas: texto plano con "OK: ..." o "Error: ...". Listados como tabla de texto alineada.

ESQUEMA (SchemaInitializer, CREATE TABLE IF NOT EXISTS)
- users, programas, materiales, parrillas, parrilla_bloques, clientes, pautas, pauta_spots, emisiones_reales, auditoria_logs
  (ver modelo en guia-actualizacion-y-handoff.md).
- Indices: materiales.hash_sha256 unique, parrilla_bloques(parrilla_id, hora_inicio), auditoria_logs(entidad, entidad_id).
- ensureAdminSeed(): crea un administrador semilla si no existe ninguno.

REGLAS
- Solo se lee el Subject; el Body se ignora.
- Comandos insensibles a mayusculas/minusculas.
- Subject invalido => "Comando no reconocido".
- Acceso a BD con PreparedStatement (nunca concatenar SQL).

PRUEBAS MINIMAS
- mvn -q -DskipTests compile exec:java -Dexec.mainClass=canal11.App crea tablas y admin.
- Enviar HELP[] desde el correo del admin y recibir la lista de comandos.
- Remitente no registrado => error. Comando sin AUTH => pide AUTH. AUTH correcto => habilita 30 min.
- Tests JUnit de SubjectParser y CommandRouter (sin red, con Pop3/Smtp simulados).

CRITERIO DE ACEPTACION
- El daemon procesa un correo real extremo a extremo y responde al remitente.
- Roles y AUTH funcionan; el daemon no se cae ante un correo malformado.
