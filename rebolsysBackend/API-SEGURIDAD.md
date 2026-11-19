# Inicio de sesión y permisos

El servidor autentica usuarios mediante JWT Bearer y credenciales guardadas con BCrypt. Las clases de seguridad están agrupadas en `security/`, tomando Pharma como referencia y conservando los permisos configurables de Oracle. El [contrato JWT](API-JWT.md) explica la firma, renovación, revocación y configuración de `JWT_SECRET`, obligatoria en producción. No requiere modificar el DDL.

## Organización equivalente a Pharma

| Responsabilidad | Clase en REBOLSYS |
| --- | --- |
| Recibir las solicitudes HTTP y validar los DTO | `controller/AuthController`, `dto/auth/` |
| Definir y coordinar la autenticación | `service/service/AuthService`, `service/impl/AuthServiceImpl` |
| Cargar el usuario y sus autoridades actuales | `security/UsuarioDetailsService implements UserDetailsService` |
| Verificar credenciales mediante AuthenticationManager | `security/CredencialesAuthenticationProvider` |
| Firmar, validar, renovar y revocar JWT | `security/JwtService` |
| Autenticar las solicitudes Bearer | `security/JwtAuthenticationFilter` |
| Configurar filtros y habilitar autorización por método | `config/SecurityConfig` con `@EnableMethodSecurity` |
| Autorizar las operaciones | `@PreAuthorize` con `hasAuthority`, `hasAnyAuthority` o `hasRole` en los controladores |

El recorrido del acceso es `AuthController → AuthServiceImpl → AuthenticationManager → CredencialesAuthenticationProvider → UsuarioDetailsService → JwtService`. El proveedor conserva la verificación BCrypt y las transacciones de `CredencialesService`: los intentos fallidos deben persistir y la apertura del turno debe permanecer serializada. No se utiliza un proveedor DAO genérico porque no contempla esas reglas del hospedaje. `UserDetails` no transporta el hash de la contraseña.

En una petición protegida, el filtro valida el JWT y carga las autoridades desde Oracle; Spring Security evalúa `@PreAuthorize` antes de ejecutar el método. `PermisosEndpoint` solo extrae los códigos de esas anotaciones para el catálogo asignable y OpenAPI; no autoriza solicitudes. Los permisos siguen siendo editables desde Seguridad y se aplican en la siguiente petición. `ACCESO_OPERATIVO` es una autoridad interna para consultar catálogos; nunca se asigna a cuentas pendientes de cambiar su contraseña temporal.

`SesionWebConfig` prepara el usuario y turno de la petición para las reglas de negocio y la auditoría. No decide permisos. Se retiraron `@Acceso`, `AccesoInterceptor` y `AccesosWebConfig`. Todos los perfiles exigen autenticación JWT; no existe una excepción de acceso automático para desarrollo.

Esta reorganización conserva las rutas `/api/v1/sesion`, los nombres JSON, los cuatro roles, la renovación y revocación existentes. No requiere ejecutar SQL ni cambiar el frontend.

Como en Pharma, MVC valida los argumentos antes de invocar el método protegido. Una petición con JWT válido y un cuerpo incorrecto puede responder 400 antes de comprobar el permiso; una solicitud válida sin permiso responde 403. La operación no se ejecuta en ninguno de los dos casos.

## Preparar el gerente inicial

1. En una instalación existente, ejecutar [V15](src/main/resources/db/iteration/V15__credencial_temporal.sql) como `PropuestaHospedajeRB` después de V14. El DDL para una instalación nueva ya incluye la columna de vencimiento.
2. Elegir una cuenta activa de un empleado existente que deba administrar el sistema. Los scripts iniciales cargan catálogos y no crean cuentas ni turnos automáticos.
3. Arrancar una instancia con `REBOLSYS_GERENTE_USUARIO` y `REBOLSYS_GERENTE_PASSWORD_TEMPORAL` en su entorno privado. La contraseña debe tener al menos 15 caracteres y ocupar como máximo 72 bytes. La preparación inicial asigna el rol protegido `GERENTE`. Los arranques posteriores conservan su contraseña.
4. Retirar las variables de preparación después del primer arranque. Iniciar sesión en `/login` y elegir una contraseña nueva. Después aparece el menú **Seguridad**.

El inicio de sesión con credenciales es obligatorio en todos los perfiles. Producción exige `JWT_SECRET` en Base64 con al menos 32 bytes. Usa HTTPS en producción. Angular conserva el JWT en `sessionStorage` y lo envía únicamente a la API mediante `Authorization: Bearer <token>`.

## Probar en el equipo local

En SQL Developer, conéctate como `PropuestaHospedajeRB` y ejecuta como script (F5) [V14](src/main/resources/db/iteration/V14__flujo_limpieza.sql) y luego [V15](src/main/resources/db/iteration/V15__credencial_temporal.sql). En una instalación actualizada, ambos scripts se pueden volver a ejecutar.

En PowerShell 7, abre una terminal en `rebolsysBackend`. Para el primer arranque con una cuenta existente y activa, ejecuta lo siguiente. Elige una contraseña temporal de al menos 15 caracteres y hasta 72 bytes.

```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\ms-21.0.12.1"
$env:REBOLSYS_GERENTE_USUARIO = Read-Host 'Usuario existente para gerencia'
$env:REBOLSYS_GERENTE_PASSWORD_TEMPORAL = Read-Host 'Contraseña temporal del gerente' -MaskInput
try {
    .\mvnw.cmd spring-boot:run
} finally {
    Remove-Item Env:REBOLSYS_GERENTE_USUARIO, Env:REBOLSYS_GERENTE_PASSWORD_TEMPORAL
}
```

En otra terminal, entra a `rebolsysFrontend` y ejecuta `npm.cmd start`. Abre [el inicio de sesión](http://localhost:4200/login). Una vez creado el gerente, para los siguientes arranques basta con ejecutar `.\mvnw.cmd spring-boot:run` con Java 21 configurado. La API responde en [el endpoint de salud](http://localhost:8080/api/health).

## Sesión

En Seguridad, el alta de empleados muestra los datos obligatorios pendientes y propone la fecha de contratación actual. Al guardar, el empleado aparece entre los disponibles para crear usuario. El botón junto a su nombre abre el formulario con el empleado seleccionado. El identificador de acceso puede ser un nombre de usuario o un correo de hasta 50 caracteres. El gerente administra empleados y usuarios sin necesitar un turno de caja.

| Método y ruta | Uso |
| --- | --- |
| `POST /api/v1/sesion/iniciar` | Recibe `usuario` y `contrasena`. Devuelve `token`, `tipo`, `expiraEnSegundos` y `sesion`; esta última contiene `requiereCambio`, `gerente`, `roles`, `permisos` e `idTurno`. |
| `GET /api/v1/sesion/actual` | Consulta el usuario, permisos vigentes y turno. |
| `POST /api/v1/sesion/contrasena` | Recibe `actual` y `nueva`. Devuelve el mismo formato del inicio y revoca los tokens de la credencial anterior. |
| `POST /api/v1/sesion/cerrar` | Revoca la cadena de JWT actual y responde 204. |

Con `requiereCambio=true` solo se puede consultar o cerrar el acceso y cambiar la contraseña. Las rutas operativas requieren un permiso explícito. Se devuelve HTTP 401 cuando falta un JWT válido, y HTTP 403 cuando la operación está fuera de sus permisos. Las cookies y los tokens enviados en URL no autentican; se retiró `/sesion/csrf`.

La política de inactividad limita cada JWT. La actividad autenticada puede ampliar su vigencia mediante `X-Auth-Token`, conservando el plazo desde la última petición; Angular conserva la renovación de mayor vencimiento. La cadena tiene un límite absoluto de ocho horas, configurable con `JWT_DURACION_MAXIMA` hasta 24 horas. Reiniciar la API requiere volver a ingresar. La revocación actual está diseñada para una sola instancia. Las evidencias se obtienen con HTTP autenticado y se abren o descargan como Blob.

La [colección JWT](api/postman/seguridad-jwt.postman_collection.json) prueba inicio, cambio de clave y revocación. Utiliza cuentas de prueba con clave temporal: la colección cambia su contraseña. En las colecciones operativas, configura la variable local `rebolsysJwt`; el script común adjunta Bearer cuando tiene valor.

Gerencia y recepción recuperan su turno abierto al iniciar sesión. Si el usuario carece de turno abierto, se abre uno con monto base S/ 0.00. En el primer acceso con clave temporal, la apertura ocurre al establecer la contraseña definitiva. Cerrar sesión conserva el turno de caja.

## Empleados, usuarios y roles

Las rutas de esta tabla empiezan con `/api/v1/seguridad`.

| Método y ruta | Uso |
| --- | --- |
| `GET /empleados`, `POST /empleados` | Consultar o registrar un empleado. El registro solicita documento, nombres, apellidos, correo y fecha de contratación. |
| `PUT /empleados/{id}` | Actualiza nombres, apellidos, contacto y fechas del empleado. Conserva su documento y vínculo con la cuenta. |
| `PUT /empleados/{id}/estado` | Recibe `activo`. La baja fija la fecha de cese y desactiva la cuenta vinculada. La reactivación del empleado permite rehabilitar su cuenta por separado. |
| `GET /usuarios`, `POST /usuarios` | Consultar usuarios o crear una cuenta para un empleado. La contraseña temporal aparece una vez y vence en 24 horas. |
| `PUT /usuarios/{id}` | Actualiza el nombre de acceso y la selección de roles y permisos. Conserva el empleado y la credencial de la cuenta. |
| `PUT /usuarios/{id}/accesos` | Sustituye la selección de roles y permisos individuales. Aplica a la siguiente solicitud de una sesión abierta. |
| `PUT /usuarios/{id}/estado` | Cambia el estado de una cuenta. La desactivación revoca la contraseña y las sesiones. Después de reactivar, generar una nueva contraseña temporal. |
| `POST /usuarios/{id}/credencial-temporal` | Emite otra contraseña temporal para una cuenta activa y revoca la anterior. |
| `GET /funcionalidades` | Catálogo de acciones asignables por módulo. |
| `GET /roles` | Consulta los roles oficiales del hospedaje. |
| `PUT /roles/{id}` | Actualiza los `permisos` de Recepcionista, Limpieza o Mantenimiento. |

Los roles oficiales son `RECEPCIONISTA`, `LIMPIEZA`, `MANTENIMIENTO` y `GERENTE`. Gerencia gestiona las cuentas y sus accesos, Configuración del sistema y Reportes. La lectura de usuarios y roles y el alta de empleados se pueden delegar. Los permisos efectivos son la unión de los roles oficiales asignados y los permisos individuales. Oracle guarda estos últimos en un registro interno por usuario. Las solicitudes administrativas exitosas quedan en `AUDITORIA_GENERAL`.

## Recuperación por correo

Mientras se habilita el envío, el gerente puede entrar a **Seguridad → Usuarios → Nueva contraseña temporal** y entregar la clave por un canal privado. El empleado debe cambiarla al iniciar sesión.

Para preparar la recuperación automática, hay que disponer de lo siguiente.

1. Una dirección de envío del hospedaje y el nombre de su proveedor. Un solo remitente puede enviar a destinatarios de Gmail, Outlook y Hotmail.
2. La dirección HTTPS de REBOLSYS que usarán los empleados al abrir el enlace. Verifica también que cada empleado tenga su correo vigente registrado.
3. Autorización de envío en el proveedor elegido. Si se usa un dominio propio, publica los registros de autenticación que indique el proveedor. Guarda las credenciales de envío en el entorno privado del servidor. [Guía de Gmail](https://support.google.com/mail/answer/81126) · [Guía de Outlook](https://support.microsoft.com/en-us/outlook/fix-ndr-error-550-5-7-515-in-outlook-com)

Con esos datos se podrá implementar y probar el flujo de correo. La solicitud mostrará el mismo mensaje exista o no la cuenta. El enlace será de un solo uso y tendrá un vencimiento corto. REBOLSYS guardará su huella en `TOKEN_RECUPERACION` e invalidará las sesiones anteriores al cambiar la contraseña. [Guía de OWASP](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html)
