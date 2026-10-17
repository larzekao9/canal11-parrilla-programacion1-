PLAN DE IMPLEMENTACION - CU2 GESTION DE CATALOGO DE PROGRAMAS

OBJETIVO
- Mantener el catalogo maestro de programas que se usan para armar la parrilla.

ALCANCE
- Comandos: INSPRG, LISPRG, GETPRG, UPDPRG, DELPRG.
- Tabla: programas.

DATOS Y CAMPOS
- programas: nombre, descripcion, tipo (noticiero | revista | pelicula | documental | serie | musical | otro),
  duracion_min, genero, clasificacion, activo.

REGLAS DE NEGOCIO
- Escriben: administrador y programador. Lectura: todos los roles.
- nombre unico por tipo; duracion_min > 0.
- DELPRG = desactivar; no se puede desactivar un programa con bloques en parrillas BORRADOR/PUBLICADA de fecha futura.
- Un programa inactivo no se puede agregar a una parrilla (lo valida CU3).

ENTRADAS ESPERADAS
- INSPRG["nombre","descripcion","tipo",duracion_min,"genero","clasificacion"]
- LISPRG["*"]  |  LISPRG["tipo"]
- GETPRG["id"]
- UPDPRG["id","campo=valor",...]
- DELPRG["id"]

SALIDAS ESPERADAS
- "OK: programa creado con ID n", listado (id, nombre, tipo, duracion, activo), detalle por id, errores claros.

CAMBIOS A APLICAR EN CODIGO
1) Migracion programas + seeder de ejemplo (Noticiero, Revista, peliculas).
2) ProgramaRepository / ProgramaService con InputValidator (validateEnum para tipo, validatePositive para duracion).
3) CommandRouter: agregar comandos a ADMIN y PROGRAMADOR (solo LIS/GET para encargado) y actualizar HELP.
4) Auditoria de INS/UPD/DEL.

VALIDACIONES CLAVE
- tipo en el enum; nombre obligatorio; duracion numerica > 0; id existente.

PRUEBAS MINIMAS
- Crear, listar con filtro de tipo, actualizar duracion, desactivar programa sin uso, intentar desactivar uno usado en parrilla futura.

CRITERIO DE ACEPTACION
- Catalogo completo gestionable por correo; integridad con parrillas respetada.
