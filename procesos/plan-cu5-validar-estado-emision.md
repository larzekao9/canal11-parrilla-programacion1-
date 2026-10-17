PLAN DE IMPLEMENTACION - CU5 GESTION DE VALIDAR ESTADO DE EMISION

OBJETIVO
- Validar tecnica/editorialmente cada material antes de emitirse y consultar si cada bloque de la parrilla esta listo para salir al aire.

ALCANCE
- Comandos: VALMAT, RECMAT, ESTEMI, LISMAT["PENDIENTE"] (bandeja de pendientes).
- Tablas: materiales (estado, validado_por, validado_at, observaciones), parrilla_bloques (lectura).

REGLAS DE NEGOCIO
- Validan administrador y encargado.
- Transiciones permitidas: PENDIENTE -> VALIDADO, PENDIENTE -> RECHAZADO, RECHAZADO -> PENDIENTE (al corregir). VALIDADO no vuelve a PENDIENTE salvo UPDMAT de ruta/duracion.
- Checklist automatico al validar: archivo existe, formato permitido, duracion del material coincide con la del bloque (tolerancia configurable, ej. +-5 s).
- RECMAT exige motivo (observaciones obligatorias).
- Si se rechaza un material usado en una parrilla PUBLICADA, ESTEMI lo marca como ALERTA.

ENTRADAS ESPERADAS
- VALMAT["material_id"] | VALMAT["material_id","observaciones"]
- RECMAT["material_id","motivo"]
- ESTEMI["parrilla_id"]   (por bloque: LISTO | SIN_MATERIAL | PENDIENTE | RECHAZADO | ALERTA)

SALIDAS ESPERADAS
- Confirmacion de validacion/rechazo con usuario y fecha; tabla de estado de emision por bloque con resumen (n listos / n con problema).

CAMBIOS A APLICAR EN CODIGO
1) ValidacionService con checklist y matriz de transiciones.
2) Consulta ESTEMI en ParrillaService (join bloques-materiales).
3) CommandRouter: VALMAT/RECMAT a ADMIN y ENCARGADO; ESTEMI a todos; HELP.
4) Auditoria de cada cambio de estado.

PRUEBAS MINIMAS
- Validar material correcto; rechazar sin motivo (error); transicion invalida; ESTEMI con mezcla de estados; rechazo sobre parrilla publicada genera ALERTA.

CRITERIO DE ACEPTACION
- Solo materiales VALIDADO permiten publicar parrilla (CU3); ESTEMI refleja el estado real de cada bloque.
