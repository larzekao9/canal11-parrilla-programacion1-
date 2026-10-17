PLAN DE IMPLEMENTACION POR FASES - CANAL 11

REGLAS DE TRABAJO
- Cada fase termina en algo que se puede PROBAR enviando un correo real (o simulado) y queda funcionando antes de pasar a la siguiente.
- Al cerrar cada fase: tests en verde, ejemplos agregados al RUNBOOK, commit/tag (fase-1, fase-2...).
- Detalle de cada CU en su plan-cuN-*.md; aqui solo el orden, alcance y verificacion.

RESUMEN
| Fase | Contenido                              | Planes                          | Resultado probable        |
|------|----------------------------------------|---------------------------------|---------------------------|
| 1    | Base: proyecto, BD, correo, AUTH       | cu0                             | El daemon responde HELP   |
| 2    | Usuarios y roles                       | cu1                             | Login y permisos por rol  |
| 3    | Catalogo de programas                  | cu2                             | CRUD de programas         |
| 4    | Materiales de video                    | cu4                             | Registro sin duplicados   |
| 5    | Parrilla (nucleo del sistema)          | cu3                             | Parrilla armada y publicada|
| 6    | Validacion de emision                  | cu5                             | Estado listo/alerta       |
| 7    | Pauta publicitaria y spots             | cu6                             | Spots en pausas           |
| 8    | Conciliacion y auditoria               | cu7                             | Programado vs real        |
| 9    | Reportes                               | cu8                             | Reportes por fecha/cliente|
| 10   | Cierre: pruebas E2E y entrega          | runbook                         | Demo completa             |

NOTA: la auditoria (AuditoriaService de CU7) se crea como clase vacia/simple en la Fase 1 y cada fase solo la llama; en la Fase 8 se completa
con consultas (LISAUD/GETAUD) y conciliacion.

--------------------------------------------------------------------
FASE 1 - BASE DEL SISTEMA (plan-cu0)
Tareas
- [ ] composer create-project laravel/laravel; configurar pgsql en .env; crear BD canal11.
- [ ] Migraciones de TODAS las tablas (modelo en guia-actualizacion-y-handoff.md) + migrate.
- [ ] UserSeeder con administrador semilla.
- [ ] SubjectParser (+ tests), InputValidator.
- [ ] Pop3Client, SmtpClient (config/mailcommands.php + .env).
- [ ] CommandRouter con roles, sesion AUTH 30 min (tabla auth_sessions) y HELP.
- [ ] php artisan mail:daemon (bucle 10 s, manejo de errores por correo).
- [ ] AuditoriaService minimo (insertar log).
Verificacion
- Enviar HELP[] desde el admin => responde lista de comandos. Remitente desconocido => error. Comando sin AUTH => pide AUTH.
Listo cuando: el daemon procesa un correo real extremo a extremo sin caerse ante un Subject malformado.

FASE 2 - USUARIOS (plan-cu1)
Tareas
- [ ] UsuarioRepository/Service; comandos INSUSU, LISUSU, GETUSU, UPDUSU, DELUSU; AUTH con password hash.
- [ ] Reglas: email/ci unicos, no desactivar ultimo admin, inactivo no entra.
- [ ] Crear usuarios de prueba: programador y encargado.
Verificacion
- Crear un usuario de cada rol; programador intentando INSUSU => permiso denegado.
Listo cuando: los 3 roles existen y el router bloquea comandos segun rol.

FASE 3 - CATALOGO DE PROGRAMAS (plan-cu2)
Tareas
- [ ] Programa Repository/Service; INSPRG, LISPRG, GETPRG, UPDPRG, DELPRG.
- [ ] Seeder: Noticiero, Revista, 2 peliculas, 1 documental.
Verificacion
- CRUD completo, filtro por tipo, desactivar programa sin uso.
Listo cuando: hay catalogo consultable por correo.

FASE 4 - MATERIALES DE VIDEO (plan-cu4)
Tareas
- [ ] MaterialService: validar ruta, extension, hash_sha256; INSMAT, LISMAT, GETMAT, UPDMAT, DELMAT.
- [ ] Carpeta de videos de prueba (archivos pequenos) para tests.
Verificacion
- Registrar archivo nuevo OK; mismo archivo otra vez => "duplicado ID n"; ruta inexistente => error.
Listo cuando: no se pueden registrar archivos repetidos.

FASE 5 - PARRILLA DE PROGRAMACION (plan-cu3)  [fase mas grande]
Subfases
- 5a: INSPAR, GETPAR, LISPAR, DELPAR (parrilla vacia por fecha).
- 5b: INSBLO, UPDBLO, DELBLO con chequeo de solapamiento (transaccion + lockForUpdate).
- 5c: VALPAR (huecos/solapes/sin material) y DUPPAR (copiar dia).
- 5d: PUBPAR / CERPAR, version +1 en cambios de parrilla publicada, auditoria antes/despues.
Verificacion
- Solapar bloques => error; VALPAR muestra hueco; modificar publicada => version+1 y log.
Listo cuando: se arma y publica una parrilla del dia (la publicacion exigira material VALIDADO desde la Fase 6; mientras tanto,
dejar la regla activable con un flag en config para poder probar).

FASE 6 - VALIDAR ESTADO DE EMISION (plan-cu5)
Tareas
- [ ] VALMAT, RECMAT con checklist (archivo existe, formato, duracion +-5 s) y matriz de transiciones.
- [ ] ESTEMI por parrilla (LISTO / SIN_MATERIAL / PENDIENTE / RECHAZADO / ALERTA).
- [ ] Activar la regla en PUBPAR: solo material VALIDADO.
Verificacion
- PUBPAR con material PENDIENTE => error; validar => publica; rechazar uno de parrilla publicada => ALERTA.
Listo cuando: el flujo registrar -> validar -> publicar funciona completo.

FASE 7 - PAUTA PUBLICITARIA (plan-cu6)
Subfases
- 7a: clientes (INSCLI, LISCLI, GETCLI, UPDCLI, DELCLI).
- 7b: pautas (INSPAU, LISPAU, GETPAU, UPDPAU, DELPAU).
- 7c: spots (INSSPO, LISSPO, DELSPO, PAUPAR) con reglas de vigencia, capacidad de pausa y max_pases.
Verificacion
- Spot valido OK; spot no validado / fuera de vigencia / exceso de capacidad => error; PAUPAR agrupado por pausa.
Listo cuando: una parrilla publicada tiene spots asignados en sus pausas comerciales.

FASE 8 - CONCILIACION Y AUDITORIA (plan-cu7)
Tareas
- [ ] INSEMI, UPDEMI, LISEMI con desviacion_seg y estados; actualizar pauta_spots.estado.
- [ ] CONPAR (programado vs real, % cumplimiento).
- [ ] LISAUD, GETAUD (solo admin); verificar que todas las fases anteriores ya generan logs; tabla solo INSERT.
Verificacion
- Emision puntual y con retraso; bloque sin registro => NO_EMITIDO; historial de una parrilla editada.
Listo cuando: cualquier cambio de las fases 2-7 aparece en la auditoria.

FASE 9 - REPORTES (plan-cu8)
Tareas
- [ ] REPPRO, REPCON, REPPAU, REPCUM, REPAUD, RESDIA con consultas SQL agregadas y permisos por rol.
Verificacion
- Totales coinciden con CONPAR; rango sin datos y rango invalido dan mensajes claros.
Listo cuando: los reportes por fecha y cliente (RF-28) responden correctamente.

FASE 10 - CIERRE Y ENTREGA
- [ ] Seeders de demo (usuarios, programas, una semana de parrilla, 2-3 anunciantes).
- [ ] Ejecutar el flujo end-to-end del RUNBOOK por correo real.
- [ ] Completar HELP por rol y RUNBOOK con todos los comandos.
- [ ] Revisar permisos por rol, errores consistentes y que el daemon sobreviva a correos malformados.
- [ ] Credenciales solo en .env; README con puesta en marcha.

COMO USAR EL PLAN CON UN CHAT NUEVO
- Pasar guia-actualizacion-y-handoff.md + este archivo + el plan-cuN de la fase en curso, e indicar "estamos en la Fase N".
