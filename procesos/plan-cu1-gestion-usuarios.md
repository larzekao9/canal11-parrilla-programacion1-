PLAN DE IMPLEMENTACION - CU1 GESTION DE USUARIOS (Administrador, Programador, Encargado de Contenidos)

OBJETIVO
- Registrar y administrar los usuarios que operan el sistema, con rol y control de acceso.

ALCANCE
- Comandos: AUTH, INSUSU, LISUSU, GETUSU, UPDUSU, DELUSU, HELP.
- Tabla: users.

DATOS Y CAMPOS
- users: ci, nombre, apellido, rol (administrador | programador | encargado), telefono, email (unico), password (hash con jbcrypt), activo.

REGLAS DE NEGOCIO
- Solo administrador gestiona usuarios.
- email unico y valido; ci unico; password minimo 8 caracteres, se guarda con hash.
- DELUSU = desactivar (activo=false), no borrado fisico; no se puede desactivar a si mismo ni al ultimo administrador.
- Usuario inactivo no puede ejecutar comandos ni AUTH.
- Admin y programador requieren AUTH["password"] (30 min); encargado tambien (todos los roles del canal son personal interno).

ENTRADAS ESPERADAS
- AUTH["password"]
- INSUSU["ci","nombre","apellido","rol","telefono","email","password"]
- LISUSU["*"]  |  LISUSU["rol"]
- GETUSU["id"]
- UPDUSU["id","campo=valor",...]   (campos: nombre, apellido, rol, telefono, email, password, activo)
- DELUSU["id"]

SALIDAS ESPERADAS
- "OK: usuario creado con ID n", listados tabulados, confirmaciones de actualizacion/desactivacion, errores claros por validacion.

CAMBIOS A APLICAR EN CODIGO
1) Tabla users en SchemaInitializer + ensureAdminSeed() (admin semilla).
2) UsuarioRepository (JDBC, PreparedStatement): crear, buscar por id/email, listar con filtro de rol, actualizar campos permitidos.
3) UsuarioService: validar con InputValidator, hash de password, reglas de ultimo admin.
4) CommandRouter: registrar comandos CU1 en el set ADMIN; AUTH para todos los roles; HELP por rol.
5) Auditoria: registrar INSUSU/UPDUSU/DELUSU (servicio de CU7).

VALIDACIONES CLAVE
- rol dentro del enum; email con formato valido y no repetido; ci y nombre obligatorios; telefono numerico.

PRUEBAS MINIMAS
- Crear un usuario de cada rol; listar por rol; actualizar telefono y rol; desactivar; intentar login con inactivo.
- Un programador intentando INSUSU => permiso denegado.

CRITERIO DE ACEPTACION
- CRUD completo por correo, permisos por rol respetados, password nunca en texto plano, errores consistentes.
