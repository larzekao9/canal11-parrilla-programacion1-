PLAN DE IMPLEMENTACION - CU7 GESTION DE CONCILIAR EMISION REAL, REGISTRAR AUDITORIA

OBJETIVO
- Comparar lo programado con lo realmente emitido y dejar trazabilidad completa de cambios (RF-29).

ALCANCE
- Comandos: INSEMI, UPDEMI, LISEMI, CONPAR, LISAUD, GETAUD.
- Tablas: emisiones_reales, auditoria_logs.

DATOS Y CAMPOS
- emisiones_reales: parrilla_bloque_id o pauta_spot_id, inicio_real, fin_real, estado (EMITIDO | PARCIAL | NO_EMITIDO | REEMPLAZADO),
  desviacion_seg, observacion, registrado_por.
- auditoria_logs: user_id, accion, entidad, entidad_id, antes (jsonb), despues (jsonb), comando, created_at.

REGLAS DE NEGOCIO
- Registran emisiones administrador y programador; auditoria visible solo para administrador.
- desviacion_seg = inicio_real - hora_inicio programada. Tolerancia configurable (ej. 30 s) para marcar EMITIDO.
- Al registrar una emision de spot se actualiza pauta_spots.estado (EMITIDO / NO_EMITIDO).
- Auditoria automatica (AuditoriaService) invocada por todos los Service en INS/UPD/DEL/publicar/cerrar/validar/AUTH.
  Guarda usuario, comando, entidad, snapshot antes/despues.
- auditoria_logs es solo INSERT: sin UPDATE/DELETE (revocar en BD y no exponer comandos de edicion).
- Una emision corregida (UPDEMI) tambien se audita.

ENTRADAS ESPERADAS
- INSEMI["bloque_id","2026-10-20 08:00:05","2026-10-20 08:30:00","EMITIDO"]
- INSEMI["spot:spot_id","inicio","fin","estado"]
- UPDEMI["id","campo=valor",...]
- LISEMI["parrilla_id"]
- CONPAR["parrilla_id"]   (programado vs real, desviaciones, bloques sin registro)
- LISAUD["desde","hasta"] | LISAUD["usuario_id"] | LISAUD["entidad"]
- GETAUD["entidad","id"]  (historial de un registro)

SALIDAS ESPERADAS
- Conciliacion: por bloque programado / real / desviacion / estado + resumen (n emitidos, n no emitidos, % cumplimiento).
- Auditoria: lista cronologica quien/cuando/que cambio.

CAMBIOS A APLICAR EN CODIGO
1) Tablas emisiones_reales y auditoria_logs en SchemaInitializer.
2) AuditoriaService (usado por todos los Service; implementar primero, antes del resto de CU si es posible).
3) ConciliacionService (calculo de desviacion y cumplimiento).
4) CommandRouter: INSEMI/UPDEMI/CONPAR para ADMIN y PROGRAMADOR; LISAUD/GETAUD solo ADMIN; HELP.

PRUEBAS MINIMAS
- Registrar emision puntual y con retraso; bloque sin emision => NO_EMITIDO en CONPAR; spot actualiza estado; editar parrilla y ver el log.

CRITERIO DE ACEPTACION
- Toda modificacion sobre parrilla o pauta aparece en la auditoria con usuario, fecha y cambio; la conciliacion muestra diferencias por bloque.
