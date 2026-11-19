# Contrato de autenticación JWT

REBOLSYS utiliza JWT firmado y `Authorization: Bearer <token>`, siguiendo la
organización de seguridad de Pharma. Conserva los cuatro roles oficiales y los
permisos configurables en Oracle. No requiere cambios de esquema.

## Rutas

- `POST /api/v1/sesion/iniciar`: recibe `usuario` y `contrasena`; devuelve
  `{token, tipo: "Bearer", expiraEnSegundos, sesion}`.
- `GET /api/v1/sesion/actual`: conserva la respuesta de usuario, permisos y turno.
- `POST /api/v1/sesion/contrasena`: recibe `actual` y `nueva`; devuelve el mismo
  formato del inicio, con un token nuevo. Invalida los tokens de la clave anterior.
- `POST /api/v1/sesion/cerrar`: revoca el acceso actual y responde 204. Conserva
  el turno de caja abierto.

Las rutas protegidas requieren Bearer. Las cookies de sesión y los tokens enviados
en URL no autentican peticiones. Se retiró `/sesion/csrf`, ya que el navegador no
adjunta automáticamente la cabecera Bearer. Los errores 401 y 403 usan el formato
común de errores de la API, sin credenciales ni detalles internos.

## Vigencia y almacenamiento

El plazo de inactividad configurado limita la vigencia de cada token. Las
peticiones autenticadas pueden recibir una renovación en `X-Auth-Token` y
`X-Auth-Expires-In`. La cadena de renovaciones tiene un máximo de ocho horas.
Angular guarda el token en `sessionStorage`, como Pharma, y lo elimina al cerrar
sesión o recibir un 401. No guarda contraseñas ni coloca tokens en enlaces.

La firma usa `JWT_SECRET` en Base64 de al menos 32 bytes. Producción exige esa
variable. Desarrollo puede generar una clave efímera. Los tokens se vinculan a
la instancia de la API: reiniciarla requiere volver a iniciar sesión. La revocación
local está diseñada para la instancia única actual; varias instancias requieren
coordinar la revocación y el identificador de instancia antes de desplegarlas.

El JWT contiene identificadores técnicos y una prueba opaca de vigencia de la
credencial. No contiene contraseñas, hashes almacenados, DNI, correo ni permisos.
Los permisos y el estado de la cuenta se consultan nuevamente al autenticar cada
petición. Las claves temporales solo habilitan consultar, cambiar y cerrar acceso.

## Archivos protegidos

Angular obtiene evidencias y documentos mediante HTTP con Bearer y abre o descarga
el contenido como Blob. Los recursos protegidos conservan sus comprobaciones de
permisos; no se hacen públicos para facilitar su visualización.
