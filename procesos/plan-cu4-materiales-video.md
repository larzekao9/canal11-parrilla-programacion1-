PLAN DE IMPLEMENTACION - CU4 GESTION DE GESTIONAR MATERIALES DE VIDEO

OBJETIVO
- Registrar los materiales de video (programas, spots, promos) y asociarlos a programas, evitando duplicados.

ALCANCE
- Comandos: INSMAT, LISMAT, GETMAT, UPDMAT, DELMAT.
- Tabla: materiales.
- Nota: el sistema es por correo y el Body se ignora, por lo que NO se sube el archivo por correo.
  El archivo ya esta en el servidor local del canal; el comando registra su ruta y el sistema calcula el hash leyendo esa ruta.

DATOS Y CAMPOS
- materiales: programa_id (opcional), titulo, tipo (programa | spot | promo | relleno), duracion_seg, formato,
  ruta_archivo, hash_sha256 (unico), estado (PENDIENTE | VALIDADO | RECHAZADO), observaciones, registrado_por.

REGLAS DE NEGOCIO
- Escriben administrador y encargado; programador solo lectura.
- Estado inicial PENDIENTE.
- hash_sha256 unico: si ya existe, se responde con el material existente (resuelve "archivos repetidos" del levantamiento).
- Si el archivo existe en el servidor: hash y tamano se calculan automaticamente; si no, error.
- Formatos permitidos: mp4, mov, mxf. duracion_seg > 0 (opcionalmente leida con ffprobe).
- DELMAT = baja logica; no se permite si el material esta en una parrilla PUBLICADA o spot programado.

ENTRADAS ESPERADAS
- INSMAT["programa_id|0","titulo","tipo",duracion_seg,"formato","ruta_archivo"]
- LISMAT["*"] | LISMAT["estado"] | LISMAT["tipo"]
- GETMAT["id"]
- UPDMAT["id","campo=valor",...]
- DELMAT["id"]

SALIDAS ESPERADAS
- "OK: material registrado con ID n (estado PENDIENTE)"; en duplicado: "Error: material duplicado, ya registrado como ID n".

CAMBIOS A APLICAR EN CODIGO
1) Tabla materiales en SchemaInitializer (unique en hash_sha256).
2) MaterialRepository y MaterialService (hash SHA-256 con MessageDigest sobre el archivo, verificacion de ruta y extension).
3) CommandRouter: comandos a ADMIN y ENCARGADO; LIS/GET a PROGRAMADOR; HELP.
4) Auditoria de INS/UPD/DEL.

VALIDACIONES CLAVE
- tipo en enum; ruta existente y legible; extension permitida; programa_id existente cuando no es 0.

PRUEBAS MINIMAS
- Registrar material nuevo; registrar el mismo archivo otra vez (duplicado); ruta inexistente; filtrar por estado; eliminar material en uso (error).

CRITERIO DE ACEPTACION
- Materiales registrados y consultables; duplicados detectados por hash; vinculo con programa correcto.
