# Comandos del sistema - Canal 11

El sistema se opera **por correo electronico**: se envia un mail al buzon del grupo y el **Asunto (Subject) es el comando**. El cuerpo se ignora. El sistema responde al remitente con el resultado (`OK: ...` o `Error: ...`).

## Como usarlos

1. Enviar el correo **desde una cuenta registrada** como usuario activo del sistema (si no, no se recibe respuesta).
2. Escribir el comando en el **Asunto**: `COMANDO[parametro1,parametro2,...]`.
3. Autenticarse primero con `AUTH["tu_password"]`. La sesion dura **30 minutos**; pasado ese tiempo hay que repetirlo.
4. Ver la lista de comandos permitidos para tu rol con `HELP[]` (tambien vale `help` solo). `HELP` y `AUTH` no requieren sesion previa.

### Reglas de formato

| Regla | Detalle |
|---|---|
| Mayusculas | El comando no distingue mayusculas/minusculas (`lisusu["*"]` = `LISUSU["*"]`). |
| Textos | Entre comillas dobles: `"Noticiero Central"`. Los que llevan espacios o comas **siempre** con comillas. |
| Numeros e IDs | Sin comillas: `GETPAR[3]` (con comillas tambien se aceptan). |
| Todos los registros | `["*"]` (ej. `LISUSU["*"]`). |
| Fechas | `YYYY-MM-DD` (ej. `2026-10-20`). |
| Horas | `HH:MM` o `HH:MM:SS`. |
| Fecha y hora (emisiones) | `YYYY-MM-DD HH:MM:SS`. |
| Sin material / sin programa | Se indica con `0` donde el comando lo permite. |
| Actualizar | `UPD...[id,"campo=valor","campo2=valor2"]`: solo se cambian los campos indicados. |
| Borrar | Los `DEL...` de usuarios, programas, clientes y materiales **desactivan** (no borran datos). |

Si el asunto no tiene la forma `COMANDO[...]` la respuesta es `Comando no reconocido. Use HELP[].` Si falta un dato o es invalido, el mensaje indica cual.

### Roles

| Rol | Que puede hacer |
|---|---|
| **administrador** | Todo. |
| **programador** | Programas, parrillas, pautas, emisiones y reportes de programacion/pauta. Lectura del resto. |
| **encargado** | Materiales de video y su validacion; reporte de contenidos. Lectura del resto. |

Todos los roles pueden **consultar** (`LIS`/`GET`), `ESTEMI`, `RESDIA`.

---

## CU1 - Usuarios (solo administrador)

| Comando | Para que sirve | Ejemplo |
|---|---|---|
| `INSUSU["ci","nombre","apellido","rol","telefono","email","password"]` | Crea un usuario. `rol`: `administrador`, `programador`, `encargado`. Password 8 a 72 caracteres. Telefono vacio o `0` = sin telefono. | `INSUSU["12345678","Ana","Rojas","programador","59170000000","ana@canal11.com","secure123"]` |
| `LISUSU["*"]` / `LISUSU["rol"]` | Lista todos los usuarios o los de un rol. | `LISUSU["encargado"]` |
| `GETUSU[id]` | Detalle de un usuario. | `GETUSU[2]` |
| `UPDUSU[id,"campo=valor",...]` | Campos: `nombre`, `apellido`, `rol`, `telefono`, `email`, `password`, `activo` (true/false). | `UPDUSU[2,"telefono=59171111111","rol=encargado"]` |
| `DELUSU[id]` | Desactiva al usuario y cierra sus sesiones. | `DELUSU[2]` |

Restricciones: no puedes desactivarte a ti mismo y siempre debe quedar al menos un administrador activo.

## CU2 - Catalogo de programas (administrador, programador)

| Comando | Para que sirve | Ejemplo |
|---|---|---|
| `INSPRG["nombre","descripcion","tipo",duracion_min,"genero","clasificacion"]` | Crea un programa. `tipo`: `noticiero`, `revista`, `pelicula`, `documental`, `serie`, `musical`, `otro`. | `INSPRG["Noticiero Central","Edicion 20:00","noticiero",60,"informativo","TP"]` |
| `LISPRG["*"]` / `LISPRG["tipo"]` | Lista programas, todos o por tipo. | `LISPRG["noticiero"]` |
| `GETPRG[id]` | Detalle. | `GETPRG[1]` |
| `UPDPRG[id,"campo=valor",...]` | Campos: `nombre`, `descripcion`, `tipo`, `duracion_min`, `genero`, `clasificacion`, `activo`. | `UPDPRG[1,"duracion_min=90"]` |
| `DELPRG[id]` | Desactiva. Se bloquea si esta en parrillas vigentes (borrador/publicadas de hoy en adelante). | `DELPRG[1]` |

## CU3 - Parrilla de programacion (administrador, programador)

Una parrilla es la programacion de **un dia**. Estados: `BORRADOR` -> `PUBLICADA` -> `CERRADA` (solo lectura).

| Comando | Para que sirve | Ejemplo |
|---|---|---|
| `INSPAR["YYYY-MM-DD"]` | Crea la parrilla (borrador) de una fecha. Una por dia. | `INSPAR["2026-10-20"]` |
| `LISPAR["*"]` / `LISPAR["desde","hasta"]` | Ultimas 30 parrillas o las de un rango. | `LISPAR["2026-10-01","2026-10-31"]` |
| `GETPAR[id]` | La parrilla con sus bloques. | `GETPAR[1]` |
| `DELPAR[id]` | Elimina una parrilla en borrador (sin spots). | `DELPAR[1]` |
| `DUPPAR[id_origen,"YYYY-MM-DD"]` | Copia una parrilla (sin spots) a otra fecha. | `DUPPAR[1,"2026-10-21"]` |
| `INSBLO[parrilla_id,programa_id,"HH:MM",material_id,"tipo"(,"HH:MM fin")]` | Agrega un bloque. `tipo`: `programa`, `pausa_comercial`, `promo`. Si no das hora fin, se calcula con la duracion del programa. `material_id` es `0` en pausas comerciales. | `INSBLO[1,1,"20:00",1,"programa"]` |
| `UPDBLO[bloque_id,"campo=valor",...]` | Campos: `hora_inicio`, `hora_fin`, `programa_id`, `material_id`, `tipo`. Si solo cambias `hora_inicio`, el bloque conserva su duracion. | `UPDBLO[5,"hora_inicio=20:30"]` |
| `DELBLO[bloque_id]` | Quita un bloque (sin spots). | `DELBLO[5]` |
| `VALPAR[id]` | Revisa solapes, huecos del dia y materiales. | `VALPAR[1]` |
| `PUBPAR[id]` | Publica. Falla si hay errores (solapes, bloques sin material o con material no validado). Los huecos solo generan alerta. | `PUBPAR[1]` |
| `CERPAR[id]` | Cierra una parrilla publicada (queda solo lectura). | `CERPAR[1]` |

En una parrilla publicada, cada cambio sube su `version` y queda auditado; los materiales deben estar `VALIDADO`.

## CU4 - Materiales de video (administrador, encargado)

El archivo debe existir en el servidor; el sistema calcula su hash SHA-256 (un hash repetido = material duplicado). Estado inicial: `PENDIENTE`.

| Comando | Para que sirve | Ejemplo |
|---|---|---|
| `INSMAT[programa_id,"titulo","tipo",duracion_seg,"formato","ruta"]` | Registra un material. `tipo`: `programa`, `spot`, `promo`, `relleno`. `formato`: `mp4`, `mov`, `mxf` (debe coincidir con la extension del archivo). `programa_id` = `0` si no pertenece a un programa. | `INSMAT[1,"Noticiero 20-10","programa",3600,"mp4","/srv/canal11/videos/noticiero_1020.mp4"]` |
| `LISMAT["*"]` / `LISMAT["estado"]` / `LISMAT["tipo"]` | Lista materiales activos, todos o filtrados por estado (`PENDIENTE`, `VALIDADO`, `RECHAZADO`) o tipo. | `LISMAT["PENDIENTE"]` |
| `GETMAT[id]` | Detalle. | `GETMAT[1]` |
| `UPDMAT[id,"campo=valor",...]` | Campos: `titulo`, `tipo`, `duracion_seg`, `formato`, `observaciones`, `programa_id`, `ruta`. Cambios tecnicos (tipo, duracion, formato, ruta) devuelven el material a `PENDIENTE`. | `UPDMAT[1,"observaciones=revisar audio"]` |
| `DELMAT[id]` | Da de baja. Se bloquea si esta en parrillas vigentes o tiene spots programados. | `DELMAT[1]` |

## CU5 - Validacion de materiales y estado de emision (administrador, encargado)

| Comando | Para que sirve | Ejemplo |
|---|---|---|
| `VALMAT[id]` / `VALMAT[id,"observaciones"]` | Valida un material: comprueba que el archivo exista, que su hash no haya cambiado y que la duracion coincida con sus bloques (tolerancia 5 s). | `VALMAT[1]` |
| `RECMAT[id,"motivo"]` | Rechaza un material (motivo obligatorio). Avisa si esta en parrillas publicadas. Para corregirlo: `UPDMAT` (vuelve a `PENDIENTE`) y validar de nuevo. | `RECMAT[1,"audio sin sonido"]` |
| `ESTEMI[parrilla_id]` | Estado de emision por bloque (todos los roles): `LISTO`, `PENDIENTE`, `RECHAZADO`, `SIN_MATERIAL` o `ALERTA`. | `ESTEMI[1]` |

## CU6 - Clientes y pauta publicitaria (administrador, programador)

### Clientes (anunciantes)

| Comando | Para que sirve | Ejemplo |
|---|---|---|
| `INSCLI["razon_social","nit","contacto","email","telefono"]` | Registra un anunciante. NIT de 5 a 20 caracteres, unico. | `INSCLI["Banco Sol SA","1023456011","Maria Pena","pauta@bancosol.bo","59171111111"]` |
| `LISCLI["*"]` | Lista anunciantes. | `LISCLI["*"]` |
| `GETCLI[id]` | Detalle. | `GETCLI[1]` |
| `UPDCLI[id,"campo=valor",...]` | Campos: `razon_social`, `nit`, `contacto`, `email`, `telefono`, `activo`. | `UPDCLI[1,"telefono=59172222222"]` |
| `DELCLI[id]` | Desactiva (se bloquea si tiene pautas `ACTIVA`). | `DELCLI[1]` |

### Pautas y spots

| Comando | Para que sirve | Ejemplo |
|---|---|---|
| `INSPAU[cliente_id,"nombre","fecha_inicio","fecha_fin",tarifa,max_pases]` | Crea una pauta con vigencia, tarifa por spot y maximo de pases contratados. | `INSPAU[1,"Campana Octubre","2026-10-20","2026-10-31",150.00,40]` |
| `LISPAU["*"]` / `LISPAU[cliente_id]` | Lista pautas, todas o de un cliente. | `LISPAU[1]` |
| `GETPAU[id]` | Detalle con pases usados y disponibles. | `GETPAU[1]` |
| `UPDPAU[id,"campo=valor",...]` | Campos: `nombre`, `fecha_inicio`, `fecha_fin`, `tarifa_spot`, `max_pases`, `estado` (`ACTIVA`, `PAUSADA`, `FINALIZADA`). | `UPDPAU[1,"estado=PAUSADA"]` |
| `DELPAU[id]` | Elimina una pauta **sin spots** (si tiene, usar `estado=FINALIZADA`). | `DELPAU[1]` |
| `INSSPO[pauta_id,material_id,bloque_id,"HH:MM:SS"]` | Inserta un spot en una **pausa comercial**. El material debe ser tipo `spot` y estar `VALIDADO`; debe caber en la pausa, no solaparse y no superar los pases. | `INSSPO[1,3,2,"20:30:00"]` |
| `DELSPO[spot_id]` | Quita un spot programado. | `DELSPO[1]` |
| `LISSPO[pauta_id]` | Spots de una pauta. | `LISSPO[1]` |
| `PAUPAR[parrilla_id]` | Pauta del dia por pausa comercial, con tiempo usado y disponible. | `PAUPAR[1]` |

## CU7 - Conciliacion de emision y auditoria

### Emisiones (administrador, programador)

La parrilla debe estar publicada. Tolerancia para considerar "a tiempo": 30 s.

| Comando | Para que sirve | Ejemplo |
|---|---|---|
| `INSEMI[bloque_id,"inicio","fin","estado"(,"observacion")]` | Registra lo que **realmente salio al aire**. Para un spot usa `"spot:ID"` en lugar del bloque. `fin` = `-` si no se conoce. `estado`: `EMITIDO`, `PARCIAL`, `NO_EMITIDO`, `REEMPLAZADO`. | `INSEMI[1,"2026-10-20 20:00:05","2026-10-20 21:00:00","EMITIDO"]` |
| `UPDEMI[id,"campo=valor",...]` | Corrige una emision: `inicio_real`, `fin_real`, `estado`, `observacion`. | `UPDEMI[1,"estado=PARCIAL"]` |
| `LISEMI[parrilla_id]` | Emisiones registradas de una parrilla. | `LISEMI[1]` |
| `CONPAR[parrilla_id]` | Conciliacion: programado vs emitido, con desviacion y porcentaje de cumplimiento. | `CONPAR[1]` |

### Auditoria (solo administrador)

| Comando | Para que sirve | Ejemplo |
|---|---|---|
| `LISAUD["*"]` | Ultimos 100 registros de auditoria. | `LISAUD["*"]` |
| `LISAUD["desde","hasta"]` / `LISAUD[usuario_id]` / `LISAUD["entidad"]` | Filtra por fechas, usuario o entidad (`users`, `programas`, `materiales`, `parrillas`...). | `LISAUD["2026-10-01","2026-10-31"]` |
| `GETAUD["entidad",id]` | Historial de un registro con su estado antes y despues. | `GETAUD["materiales",1]` |

## CU8 - Reportes

Rango maximo: 366 dias. Cada reporte consultado queda auditado.

| Comando | Roles | Que muestra | Ejemplo |
|---|---|---|---|
| `REPPRO["desde","hasta"]` | admin, programador | Horas programadas por tipo de programa y por dia. | `REPPRO["2026-10-01","2026-10-31"]` |
| `REPCON["desde","hasta"]` | admin, encargado | Materiales registrados por estado y tipo; pendientes de validacion. | `REPCON["2026-10-01","2026-10-31"]` |
| `REPPAU["desde","hasta"(,cliente_id)]` | admin, programador | Spots programados/emitidos y monto por cliente (monto = emitidos x tarifa). | `REPPAU["2026-10-01","2026-10-31",1]` |
| `REPCUM["desde","hasta"]` | admin, programador | Cumplimiento programado vs emitido por parrilla. | `REPCUM["2026-10-01","2026-10-31"]` |
| `REPAUD["desde","hasta"]` | admin | Actividad por usuario y acciones mas frecuentes. | `REPAUD["2026-10-01","2026-10-31"]` |
| `RESDIA["*"]` | todos | Resumen del dia: parrilla de hoy, spots, pendientes y alertas. | `RESDIA["*"]` |

---

## Flujo tipico de uso

1. `AUTH["password"]` como administrador y crear a los demas usuarios (`INSUSU`).
2. Crear programas (`INSPRG`).
3. Registrar materiales (`INSMAT`) y validarlos (`VALMAT`).
4. Crear la parrilla del dia (`INSPAR`), agregar bloques (`INSBLO`), revisar (`VALPAR`) y publicar (`PUBPAR`).
5. Registrar clientes (`INSCLI`), pautas (`INSPAU`) y spots (`INSSPO`).
6. Registrar lo emitido (`INSEMI`) y conciliar (`CONPAR`).
7. Sacar reportes (`REPPRO`, `REPCON`, `REPPAU`, `REPCUM`) y revisar la auditoria (`LISAUD`).

## Probar sin correo

`mvn -q compile exec:java -Dexec.mainClass=canal11.mail.ConsoleSimulator` y escribir el asunto directamente (ver `README.md` y `procesos/RUNBOOK.md`).

## Errores frecuentes

| Respuesta | Causa |
|---|---|
| `Comando no reconocido. Use HELP[].` | Asunto sin la forma `COMANDO[...]` o comando inexistente. |
| `Error: correo no registrado en el sistema.` | El remitente no es un usuario. (En modo real el daemon ni responde.) |
| `Error: Autenticacion requerida...` | Falta `AUTH["password"]` o ya expiro la sesion (30 min). |
| `Error: Permiso denegado para rol ...` | Tu rol no puede usar ese comando; revisa `HELP[]`. |
| `Error: ... no encontrado: N` | El ID no existe. |
