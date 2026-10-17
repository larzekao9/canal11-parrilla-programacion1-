PLAN DE IMPLEMENTACION - CU8 REPORTES Y ESTADISTICAS

OBJETIVO
- Generar reportes de programacion, contenidos y pauta publicitaria filtrados por fecha o cliente (RF-28) y un resumen del dia.

ALCANCE
- Comandos: REPPRO, REPCON, REPPAU, REPCUM, REPAUD, RESDIA.
- Tablas: lectura de parrillas, parrilla_bloques, materiales, pautas, pauta_spots, emisiones_reales, auditoria_logs.

REGLAS DE NEGOCIO
- Administrador: todos. Programador: REPPRO, REPPAU, REPCUM, RESDIA. Encargado: REPCON, RESDIA.
- Fechas YYYY-MM-DD; rango maximo configurable (ej. 366 dias); desde <= hasta.
- Reportes con agregaciones SQL (GROUP BY, date_trunc) para no cargar todo en memoria.
- La respuesta es texto plano por correo; limitar filas y avisar si se trunca.

ENTRADAS ESPERADAS
- REPPRO["desde","hasta"]            horas programadas por tipo de programa y por dia.
- REPCON["desde","hasta"]            materiales por estado/tipo, pendientes de validacion.
- REPPAU["desde","hasta"] | REPPAU["desde","hasta","cliente_id"]   spots programados/emitidos/no emitidos y monto por cliente.
- REPCUM["desde","hasta"]            % de cumplimiento programado vs real (desde CU7).
- REPAUD["desde","hasta"]            actividad por usuario (solo admin).
- RESDIA["*"]                        resumen del dia: programas de hoy, pendientes, alertas.

SALIDAS ESPERADAS
- Tablas de texto con totales y subtotales; encabezado con filtros aplicados.

CAMBIOS A APLICAR EN CODIGO
1) ReporteRepository: una consulta SQL por reporte (vistas SQL opcionales).
2) ReporteService: validar rango de fechas, formatear texto.
3) CommandRouter: asignar comandos por rol; HELP.
4) Auditar la ejecucion de reportes (quien consulto que).

PRUEBAS MINIMAS
- Reporte con datos de seed; rango sin datos (mensaje claro); rango invalido; filtro por cliente; totales coinciden con CONPAR.

CRITERIO DE ACEPTACION
- Reportes correctos por fecha y cliente; cifras consistentes con la conciliacion; permisos por rol respetados.
