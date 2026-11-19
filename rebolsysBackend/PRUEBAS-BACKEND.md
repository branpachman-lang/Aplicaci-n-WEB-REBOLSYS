# Prueba de las siete funcionalidades del hospedaje

Fecha de validación: 1 de noviembre de 2026 (America/Lima).

Esta página conserva los resultados de la primera reconstrucción. El flujo y
las pruebas de la nueva iteración de reservas se describen en API-RESERVAS.md.

La API usa el DDL de cliente unificado; la copia consolidada incorpora ahora
la restricción V8 que permite consumos separados vinculados a una estancia.
Se conservan las 46 tablas y 264 columnas del DDL original.
No se modificaron Pharma ni los proyectos de frontend.

## Probar ahora

Servidor: `http://localhost:8080`. Abrir
[Swagger UI](http://localhost:8080/swagger-ui.html) para ejecutar las operaciones
con JWT Bearer para las rutas protegidas. `GET /api/v1/recepcion/catalogos` entrega los identificadores
del operador, turno, documentos y catálogos disponibles.

En Postman, importar `api/postman/siete-casos.postman_collection.json` y ejecutar
la colección completa en orden, usando `baseUrl=http://localhost:8080`.
Cada ejecución crea registros nuevos de demostración. Los identificadores se
guardan durante la ejecución; no hay que escribirlos a mano. Configura un JWT válido en la variable local `rebolsysJwt`.
La preparación inicial ya está cargada en el esquema local PropuestaHospedajeRB.

| Funcionalidad | Qué probar | Resultado esperado |
|---|---|---|
| Estado de habitaciones | `GET /api/v1/habitaciones?desde=YYYY-MM-DD&hasta=YYYY-MM-DD` | Estado físico, ocupación actual y disponibilidad del periodo. La consulta no escribe datos. |
| Reservas | `POST /api/v1/reservas`, `GET /api/v1/reservas/{id}` | Reserva con varias habitaciones y ocupantes que referencian CLIENTE. Rechaza superposiciones y menores sin responsable. |
| Gastos | `POST /api/v1/gastos`, `GET /api/v1/gastos?idTurno=...` | Salida de caja con monto positivo y turno abierto del mismo operador. |
| Venta de productos | Crear borrador, confirmar y anular en `/api/v1/ventas` | El borrador no mueve stock; confirmar descuenta y anular repone una sola vez. |
| Configuración de habitaciones | Crear tipos en `/api/v1/tipos-habitacion`; crear/editar habitaciones en `/api/v1/habitaciones` | Valida capacidad, camas y límite de menores; protege reservas existentes. |
| Mobiliario por habitación | Crear tipos en `/api/v1/tipos-item`; crear ítems y asignarlos en `/api/v1/items/{id}/asignacion` | `GET /api/v1/habitaciones/{id}/items` muestra solo la asignación actual; movimientos conserva el historial. |
| Configuración de productos | CRUD de `/api/v1/productos` y `/api/v1/categorias`; ajuste en `/api/v1/productos/{id}/ajustes-stock` | Producto con stock inicial cero, carga trazable y baja lógica. |

Los valores JSON del estado del mobiliario son `Activo`, `Inactivo` y
`En habitacion`, conforme al modelo existente. Un ítem representa una unidad de
mobiliario; el stock comercial corresponde a PRODUCTO.

Para ingreso, salida o cancelación de una estancia:
`POST /api/v1/reservas/detalles/{idDetalleReserva}/ingreso`, `/salida` o
`/cancelacion`, con `{"idUsuario": <operador>}`. El ingreso exige estar dentro
del periodo reservado. La colección crea una reserva para el día siguiente;
las transiciones de ingreso/salida se verifican en las pruebas de integración.

## Estructura de implementación

Los CRUD de habitaciones, ítems, productos, categorías y sus tipos implementan
`CrudService<REQ, RES, ID>` con `create`, `update`, `read`, `readAll`, `delete`.
Se conserva la estructura de Pharma de controladores, DTO, interfaces,
implementaciones y repositorios. Se consultó también su frontend como referencia
del consumo REST y la respuesta paginada `contenido`, `pagina`, `tamanio`,
`totalElementos`, `totalPaginas`, `ultima` en productos, reservas y ventas.

Reservas, gastos y ventas tienen acciones de negocio; no ofrecen borrado de
historial para completar artificialmente las cinco operaciones del CRUD.
Habitaciones se eliminan solo sin historial. Ítems y productos se desactivan;
un ítem debe retirarse de la habitación antes de su baja. Tipos y categorías
referenciados devuelven 409 al intentar borrarlos.

## Validación realizada

- Maven con Java 21 y Oracle local real: 38 pruebas aprobadas, 0 fallos.
  Incluye 20 pruebas funcionales, 2 de concurrencia, 8 de arranque/HTTP/CORS,
  6 de errores y 2 del mapeo del DDL (46 tablas y 264 columnas).
- Se omitieron 7 pruebas de la integración opcional con APIs Perú; los siete
  casos del hospedaje no necesitan ese proveedor.
- Postman, colección de verificación: 21 solicitudes y 57 comprobaciones
  aprobadas; consultas y solicitudes inválidas, sin altas válidas.
- Postman, colección completa: 28 solicitudes y 59 comprobaciones aprobadas;
  altas de demostración, edición de configuraciones, reserva con menor y
  responsable, gasto, ajuste de stock, venta/confirmación/anulación y mobiliario.
- Ambas ejecuciones de Postman finalizaron con código 0 y resultados locales.
  `api/openapi.json` se actualizó desde el `/v3/api-docs` del servidor ejecutado.

Las pruebas funcionales revierten sus transacciones. Las de concurrencia limpian
exclusivamente sus registros. La colección completa conserva sus datos de
demostración para inspección; su venta termina ANULADA y el producto vuelve a
tener stock 10. La reserva y el mobiliario quedan registrados.

Para repetir las pruebas desde la carpeta `rebolsysBackend`:

```powershell
$env:RUN_ORACLE_TESTS='true'
.\mvnw.cmd test
postman collection run api/postman/verificacion.postman_collection.json --no-report-events
postman collection run api/postman/siete-casos.postman_collection.json --no-report-events
```

El entorno debe tener Java 21 configurado en `JAVA_HOME`. Para arrancar de nuevo:
`.\mvnw.cmd spring-boot:run`. La conexión se conserva en el archivo privado local;
no se incluyen contraseñas en las colecciones ni en el contrato.

## Seguridad JWT

La migración se verificó con Java 21 y la suite completa: 80 pruebas aprobadas,
sin fallos y 109 pruebas opcionales omitidas. Las pruebas Oracle no se ejecutaron.
Angular aprobó 137 pruebas, con seis omitidas, y compiló con los tres avisos
de presupuesto CSS preexistentes.

La colección [seguridad-jwt](api/postman/seguridad-jwt.postman_collection.json)
también se ejecutó por HTTP contra una instancia temporal con H2 y cuentas
ficticias de Gerente, Recepcionista, Limpieza y Mantenimiento: 52 peticiones y
68 aserciones aprobadas. Verificó cambio obligatorio, turno según rol, permisos,
rechazo de firma inválida y revocación al cerrar. No se modificó Oracle.

Consulta [API-JWT.md](API-JWT.md) para configurar la firma y la vigencia. Las
pruebas de la colección cambian contraseñas: utiliza exclusivamente cuentas de
prueba con clave temporal y valores privados locales.

## Reorganización de seguridad con Pharma como referencia

Validación del 19 de noviembre de 2026: `RUN_ORACLE_TESTS=false` y
`.\mvnw.cmd -o package`, con Java 21. Resultado: 81 pruebas aprobadas,
0 fallos y 109 pruebas opcionales omitidas. Se generó el JAR sin los antiguos
`SesionOperacionController`, `Acceso` ni `AccesoInterceptor`.

La prueba HTTP comprueba que todas las rutas operativas tengan `@PreAuthorize`
y que el catálogo conserve sus 76 permisos. También verifica claves temporales,
bloqueo por intentos fallidos, apertura y recuperación del turno, actualización
de permisos en sesiones abiertas, acceso a reportes, cierre de caja y revocación.
El flujo de limpieza conserva las restricciones por rol para aprobar informes
y atender tickets. Los DTO de autenticación conservan validaciones y ocultan
las contraseñas en `toString()`.

Estas pruebas usan H2 para los flujos integrados. No se ejecutó SQL contra Oracle
ni se modificaron su configuración, entidades, scripts o el frontend. Consulta
[la organización de seguridad](API-SEGURIDAD.md#organización-equivalente-a-pharma)
para recorrer las clases y conocer el orden de validación y autorización.

## Retiro del acceso automático de desarrollo

Validación del 19 de noviembre de 2026: el backend compila y empaqueta con
84 pruebas aprobadas y 109 opcionales omitidas (`RUN_ORACLE_TESTS=false`).
Angular compila y aprueba 141 pruebas, con seis omitidas; conserva los tres
avisos CSS preexistentes. El script de despliegue supera la revisión de sintaxis.

Las rutas operativas exigen JWT en todos los perfiles. Las pruebas comprueban
401 sin autenticación, ausencia de permisos implícitos, rechazo de credenciales
revocadas y restricciones de contraseñas temporales. El flujo de limpieza usa
JWT y conserva la exigencia del rol MANTENIMIENTO para atender y aprobar.

Se retiró la creación del operador automático y su turno en la carga inicial,
y las cargas de demostración ya no dan preferencia a esa cuenta. No se ejecutó
SQL ni se eliminaron usuarios o registros históricos existentes en Oracle.
