GUIA DE IMPLEMENTACION Y HANDOFF - CANAL 11 (GRUPO #12)

OBJETIVO
- Dejar una ruta clara de implementacion y un resumen de contexto para otro chat.
- Mismo esquema que el proyecto de referencia tecno-web-main (ServiCargo): sistema operado por CORREO.

CONTEXTO CLAVE (RESUMEN)
- Sistema de gestion de parrilla de programacion de Canal 11 (Santa Cruz, Bolivia). Fuente: Canal11_Levantamiento_Informacion.md.
- Sin frontend. Backend en Java 17 + Maven + JDBC + PostgreSQL (igual que servicargo).
- Interfaz = correo electronico: un daemon lee por POP3 el buzon del grupo, interpreta el Subject como comando
  COMANDO["param1","param2",...], ejecuta contra la BD y responde por SMTP al remitente. El Body se ignora.
- Roles (users.rol, texto): administrador, programador, encargado (Encargado de Contenidos).
- El remitente se identifica por su email registrado en la tabla users. Admin/Programador requieren AUTH["password"] (sesion 30 min).
- Problemas del canal que el sistema ataca: archivos duplicados (hash en CU4), doble fuente de verdad del orden (parrilla unica versionada, CU3),
  falta de trazabilidad (auditoria, CU7 / RF-29), reportes (RF-28, CU8).

ARQUITECTURA (IGUAL A SERVICARGO)
- Correo (Subject) -> MailDaemon -> CommandRouter (roles, AUTH) -> Service (validaciones y reglas) -> Repository (JDBC) -> PostgreSQL
  -> respuesta en texto -> SmtpClient.
- Paquetes: canal11.{db, mail, model, gestion, programacion, comercial, emision, reporte, util}. Detalle en plan-cu0-base-correo.md.
- Esquema y seed: SchemaInitializer (se ejecuta con App.java). Configuracion: DbConfig / MailConfig (variables de entorno o config.properties local).

ORDEN RECOMENDADO DE IMPLEMENTACION
0) Base: esquema BD, daemon de correo, parser, router, AUTH, HELP  -> plan-cu0-base-correo.md
1) CU1 Usuarios                                                      -> plan-cu1-gestion-usuarios.md
2) CU2 Catalogo de Programas                                         -> plan-cu2-catalogo-programas.md
3) CU4 Materiales de video                                           -> plan-cu4-materiales-video.md
4) CU3 Parrilla de programacion                                      -> plan-cu3-parrilla-programacion.md
5) CU5 Validar estado de emision                                     -> plan-cu5-validar-estado-emision.md
6) CU6 Pauta publicitaria y spots                                    -> plan-cu6-pauta-publicitaria.md
7) CU7 Conciliar emision real y auditoria                            -> plan-cu7-conciliacion-auditoria.md
8) CU8 Reportes y estadisticas                                       -> plan-cu8-reportes.md
(CU4 va antes de CU3 porque la parrilla necesita materiales. Instalar la auditoria de CU7 desde el inicio como servicio transversal.)

MODELO DE DATOS (PostgreSQL)
- users(id, ci, nombre, apellido, rol, telefono, email unique, password, activo)
- programas(id, nombre, descripcion, tipo, duracion_min, genero, clasificacion, activo)
- materiales(id, programa_id null, titulo, tipo[programa|spot|promo|relleno], duracion_seg, formato, ruta_archivo, hash_sha256 unique,
  estado[PENDIENTE|VALIDADO|RECHAZADO], validado_por, validado_at, observaciones, registrado_por)
- parrillas(id, fecha unique, estado[BORRADOR|PUBLICADA|CERRADA], version, creado_por, publicada_at)
- parrilla_bloques(id, parrilla_id, programa_id, material_id null, hora_inicio, hora_fin, orden, tipo[programa|pausa_comercial|promo])
- clientes(id, razon_social, nit, contacto, email, telefono, activo)
- pautas(id, cliente_id, nombre, fecha_inicio, fecha_fin, tarifa_spot, max_pases, estado[ACTIVA|PAUSADA|FINALIZADA], creado_por)
- pauta_spots(id, pauta_id, material_id, parrilla_bloque_id, hora_programada, estado[PROGRAMADO|EMITIDO|NO_EMITIDO])
- emisiones_reales(id, parrilla_bloque_id null, pauta_spot_id null, inicio_real, fin_real, estado[EMITIDO|PARCIAL|NO_EMITIDO|REEMPLAZADO],
  desviacion_seg, observacion, registrado_por)
- auditoria_logs(id, user_id, accion, entidad, entidad_id, antes jsonb, despues jsonb, comando, created_at)  -- solo INSERT
- Reglas transversales: borrado logico en catalogos; FK con ON DELETE RESTRICT; fechas YYYY-MM-DD, horas HH:MM[:SS].

PERMISOS POR ROL (resumen)
- administrador: todos los comandos.
- programador: CU2, CU3, CU6, lectura de CU4, reportes de programacion/pauta.
- encargado: CU4, CU5, lectura de CU2/CU3, reportes de contenidos.

PARA CONTINUAR EN OTRO CHAT
- Enviar este archivo y los planes por CU.
- Indicar que el sistema es por correo (Subject = comando) y NO una API REST.
- Orden: base -> CU1 -> CU2 -> CU4 -> CU3 -> CU5 -> CU6 -> CU7 -> CU8.
- Las credenciales del buzon/VPS van en variables de entorno o config.properties local (no en el repositorio).
