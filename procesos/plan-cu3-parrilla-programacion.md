PLAN DE IMPLEMENTACION - CU3 GESTION DE CONFECCIONAR Y CONTROLAR LA PARRILLA DE PROGRAMACION

OBJETIVO
- Armar la parrilla diaria (24 h), validarla, publicarla y controlar su estado y versiones.

ALCANCE
- Comandos: INSPAR, LISPAR, GETPAR, DELPAR, INSBLO, UPDBLO, DELBLO, DUPPAR, VALPAR, PUBPAR, CERPAR.
- Tablas: parrillas, parrilla_bloques.

DATOS Y CAMPOS
- parrillas: fecha (unica), estado (BORRADOR | PUBLICADA | CERRADA), version, creado_por, publicada_at.
- parrilla_bloques: parrilla_id, programa_id, material_id (opcional), hora_inicio, hora_fin, orden, tipo (programa | pausa_comercial | promo).

REGLAS DE NEGOCIO
- Escriben administrador y programador; encargado solo lectura.
- Una parrilla por fecha.
- Los bloques de una misma parrilla no pueden solaparse; los huecos en 00:00-24:00 se reportan como alerta (no error).
- hora_fin por defecto = hora_inicio + duracion_min del programa.
- PUBPAR exige: sin solapamientos y bloques de tipo programa con material VALIDADO (CU5).
- Parrilla PUBLICADA: se puede modificar, pero cada cambio incrementa version y se audita. CERRADA: solo lectura.
- Modificaciones dentro de una transaccion (JDBC con SELECT ... FOR UPDATE) para evitar ediciones concurrentes.
- Programa inactivo no se puede agregar.

ENTRADAS ESPERADAS
- INSPAR["2026-10-20"]
- DUPPAR["id_origen","2026-10-27"]
- INSBLO["parrilla_id","programa_id","HH:MM","material_id|0","tipo"]   (hora_fin opcional: ...,"HH:MM")
- UPDBLO["bloque_id","campo=valor",...]
- DELBLO["bloque_id"]
- LISPAR["*"] | LISPAR["desde","hasta"]
- GETPAR["id"]  (parrilla con bloques ordenados)
- VALPAR["id"]  (huecos, solapamientos, bloques sin material)
- PUBPAR["id"] | CERPAR["id"] | DELPAR["id"] (solo BORRADOR)

SALIDAS ESPERADAS
- Parrilla en texto (hora - hora | programa | material | estado), reporte de validacion, confirmaciones de publicacion/cierre.

CAMBIOS A APLICAR EN CODIGO
1) Tablas parrillas y parrilla_bloques en SchemaInitializer (indice por parrilla_id, hora_inicio).
2) ParrillaRepository y ParrillaService (crear, agregar bloque con chequeo de solape, validar, publicar, cerrar, duplicar).
3) CommandRouter: comandos a ADMIN y PROGRAMADOR; LIS/GET/VAL a ENCARGADO; actualizar HELP.
4) Auditoria con snapshot antes/despues en jsonb.

VALIDACIONES CLAVE
- fecha valida; hora valida; hora_fin > hora_inicio; sin solape; transiciones de estado validas.

PRUEBAS MINIMAS
- Crear parrilla, agregar bloques, intentar solapar (error), VALPAR con hueco, PUBPAR con material pendiente (error), PUBPAR correcto,
  modificar publicada y ver version+1, CERPAR y rechazo de ediciones.

CRITERIO DE ACEPTACION
- No se publica con solapamientos o material no validado; cada cambio queda versionado y auditado; una sola fuente de verdad del orden.
