GUIA DE EJECUCION Y PRUEBA - CANAL 11

1) Requisitos
- Java 17, Maven en PATH, PostgreSQL, acceso al servidor de correo del curso.

2) Crear tablas y admin semilla (una vez)
  mvn -q -DskipTests compile exec:java "-Dexec.mainClass=canal11.App"

3) Ejecutar el daemon de correo
  mvn -q -DskipTests compile exec:java "-Dexec.mainClass=canal11.mail.MailDaemon"
- Lee por POP3 el buzon del grupo, procesa el Subject como comando, ejecuta en la BD, responde por SMTP y elimina el mensaje. Repite cada 10 s.

4) Como enviar comandos
- Enviar un correo al buzon del grupo. SOLO se lee el Subject; el Body se ignora.
- Formato: COMANDO["param1","param2",...]. Comillas para textos con espacios; numeros sin comillas.
- Fechas YYYY-MM-DD, horas HH:MM[:SS].
- Todos los roles se autentican antes con AUTH["password"] (30 min).
- Credenciales de buzon y admin semilla: en variables de entorno / config.properties local (no se documentan aqui).

Ejemplos (Subject)
  AUTH["password"]
  INSUSU["12345678","Ana","Rojas","programador","59170000000","ana@canal11.com","secure123"]
  INSPRG["Noticiero Central","Edicion 20:00","noticiero",60,"informativo","TP"]
  INSMAT["1","Noticiero 20-10","programa",3600,"mp4","/srv/canal11/videos/noticiero_1020.mp4"]
  VALMAT["1"]
  INSPAR["2026-10-20"]
  INSBLO["1","1","20:00","1","programa"]
  VALPAR["1"]
  PUBPAR["1"]
  INSCLI["Banco Sol SA","1023456011","Maria Peña","pauta@bancosol.bo","59171111111"]
  INSPAU["1","Campana Octubre","2026-10-20","2026-10-31",150.00,40]
  INSSPO["1","3","2","20:30:00"]
  INSEMI["1","2026-10-20 20:00:05","2026-10-20 21:00:00","EMITIDO"]
  CONPAR["1"]
  REPPAU["2026-10-01","2026-10-31"]

5) Flujo completo de prueba (end-to-end)
  1. AUTH como admin -> crear programador y encargado (CU1).
  2. Crear programas (CU2).
  3. Registrar materiales y validarlos (CU4, CU5).
  4. Crear parrilla del dia, agregar bloques, VALPAR y PUBPAR (CU3).
  5. Crear cliente, pauta e insertar spots (CU6).
  6. Registrar emisiones reales y CONPAR (CU7); revisar LISAUD.
  7. Reportes REPPRO, REPCON, REPPAU, REPCUM (CU8).

6) Notas
- Comandos insensibles a mayusculas/minusculas.
- Si el Subject no es valido se responde "Comando no reconocido".
- Campos faltantes: el sistema indica cual falta.
- Con tunel SSH para SMTP, mantener la terminal con el tunel abierta mientras corre el daemon.
