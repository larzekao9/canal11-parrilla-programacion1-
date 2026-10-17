PLAN DE IMPLEMENTACION - CU6 GESTION DE PAUTA PUBLICITARIA E INSERTAR SPOTS COMERCIALES

OBJETIVO
- Gestionar anunciantes y pautas, e insertar spots comerciales en las pausas de la parrilla.

ALCANCE
- Comandos: INSCLI, LISCLI, GETCLI, UPDCLI, DELCLI; INSPAU, LISPAU, GETPAU, UPDPAU, DELPAU; INSSPO, LISSPO, DELSPO; PAUPAR.
- Tablas: clientes, pautas, pauta_spots.

DATOS Y CAMPOS
- clientes: razon_social, nit, contacto, email, telefono, activo.
- pautas: cliente_id, nombre, fecha_inicio, fecha_fin, tarifa_spot, max_pases, estado (ACTIVA | PAUSADA | FINALIZADA).
- pauta_spots: pauta_id, material_id, parrilla_bloque_id, hora_programada, estado (PROGRAMADO | EMITIDO | NO_EMITIDO).

REGLAS DE NEGOCIO
- Escriben administrador y programador; encargado solo lectura.
- nit unico por cliente; fecha_fin >= fecha_inicio; tarifa > 0.
- El material del spot debe ser tipo=spot y estado VALIDADO.
- La fecha de la parrilla debe estar dentro de la vigencia de la pauta y la pauta ACTIVA.
- El bloque destino debe ser de tipo pausa_comercial; la suma de duracion de spots no puede exceder la duracion del bloque.
- No superar max_pases contratados; no solapar spots en la misma pausa.
- Cliente con pautas activas no se puede desactivar.

ENTRADAS ESPERADAS
- INSCLI["razon_social","nit","contacto","email","telefono"]
- INSPAU["cliente_id","nombre","2026-10-20","2026-11-20",tarifa,max_pases]
- INSSPO["pauta_id","material_id","bloque_id","HH:MM:SS"]
- DELSPO["spot_id"]
- LISSPO["pauta_id"]
- PAUPAR["parrilla_id"]   (pauta del dia agrupada por pausa comercial)
- LISCLI/LISPAU["*"], GETCLI/GETPAU["id"], UPDCLI/UPDPAU["id","campo=valor",...], DELCLI/DELPAU["id"]

SALIDAS ESPERADAS
- "OK: spot insertado en pausa HH:MM"; PAUPAR en texto agrupado por bloque con cliente y segundos usados/disponibles.

CAMBIOS A APLICAR EN CODIGO
1) Tablas clientes, pautas, pauta_spots en SchemaInitializer.
2) ClienteService, PautaService (validar vigencia, capacidad de pausa, pases), repositories.
3) CommandRouter: comandos a ADMIN y PROGRAMADOR; LIS/GET/PAUPAR a ENCARGADO; HELP.
4) Auditoria de altas, cambios y quitas de spots.

PRUEBAS MINIMAS
- Crear cliente y pauta; insertar spot valido; spot no validado (error); fuera de vigencia (error); exceso de capacidad (error); PAUPAR.

CRITERIO DE ACEPTACION
- Spots ligados a pausa y hora; reglas de vigencia, capacidad y pases rechazan inserciones invalidas.
