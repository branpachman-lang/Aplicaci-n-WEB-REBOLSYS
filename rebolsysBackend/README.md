# REBOLSYS Backend — propuesta de siete casos de uso

## Redis para catálogos

Redis se ejecuta separado de Pharma; Oracle y Angular mantienen sus contratos.
Desde `rebolsysBackend/`, con Docker Desktop activo en modo Linux:

```powershell
docker compose up -d --wait redis
docker compose exec -T redis redis-cli ping
.\mvnw.cmd spring-boot:run
```

`ping` debe responder `PONG`. Si usas IntelliJ, recarga Maven y vuelve a ejecutar
`RebolsysBackendApplication`. Redis queda en `127.0.0.1:6380`; el contenedor usa
internamente `6379`. No necesita cambiar el puerto de Pharma ni la base Oracle.
Para detener solo este Redis: `docker compose stop redis`.

Se cachean listas de categorías, tipos de habitación y tipos de mobiliario durante
10 minutos; documentos, formas de pago y tipos de gasto durante 30 minutos.
Los cambios de categorías/tipos, incluyendo tarifas y capacidad, invalidan su
catálogo después del commit. Las escrituras e invalidaciones Redis se completan
de forma síncrona para evitar leer antes de que termine la invalidación.
Los catálogos de recepción reutilizan estas listas, pero usuarios y turnos
abiertos se consultan en vivo. Productos, reservas, pagos,
disponibilidad, credenciales y permisos quedan fuera de esta primera etapa.

La configuración admite `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `REDIS_SSL`,
`CACHE_ENABLED` y `CACHE_PREFIX`. Cambia el prefijo por entorno, por ejemplo
`rebolsys:prod:v1:`. Para desactivar la caché: `CACHE_ENABLED=false` o
`app.cache.habilitada=false` en la configuración privada.

La caché guarda DTO como JSON con tipos explícitos, sin entidades ni sesiones.
Ante un fallo Redis, las consultas continúan en Oracle y se esperan cinco segundos
antes de reintentar por catálogo. Cada proceso descarta el catálogo afectado antes
de reutilizarlo al recuperarse, incluso si falló una invalidación tras el commit.
La caché no garantiza lecturas estrictamente simultáneas entre varios procesos:
si se despliegan múltiples backends, se requiere una estrategia compartida de
recuperación/invalidez. Los cambios SQL externos se reflejan al vencer el TTL;
reiniciar únicamente el backend no borra Redis. No usar `FLUSHALL` para refrescarlo.

Las pruebas normales no necesitan Redis ni Oracle. Para comprobar Redis local real:

```powershell
$env:RUN_REDIS_TESTS = 'true'
.\mvnw.cmd test '-Dtest=CacheCatalogosTest,CacheToleranteTest,RedisCatalogosIntegracionTest'
Remove-Item Env:RUN_REDIS_TESTS
```

Las pruebas de Redis usan un prefijo aleatorio y solo eliminan sus propias claves.
También se verifican rollback, invalidación al confirmar y continuidad ante fallos.

## Comprobantes A4, reportes y correo

Las reservas y las ventas permiten generar una boleta o factura interna desde su
cuenta pagada. El mismo diálogo ofrece **Simular factura o boleta localmente**:
guarda los importes y el desglose del impuesto en `EMISION_ELECTRONICA`, con estado
`PREPARADO`, ambiente `BETA` y código `SIMULACION_LOCAL`. Requiere los datos del
hospedaje, pero no credenciales de APIsPerú. Esa simulación no se puede enviar al
proveedor, no crea XML firmado ni CDR y no acredita aceptación de SUNAT.

**Imprimir comprobante** abre el diálogo del navegador con el comprobante en
formato A4. La descarga y el adjunto por correo también usan A4. La pantalla de
reportes ofrece impresión A4 horizontal o ticket de 80 mm; el usuario elige la
impresora y el papel en el diálogo del navegador. La página no selecciona una
impresora ni imprime silenciosamente. Todos los documentos de prueba conservan
el aviso de que no tienen validez tributaria de producción.

**Enviar al correo** sugiere el correo del receptor y permite corregir el destino
antes de confirmar. Adjunta el PDF y conserva los pagos y la numeración. Utiliza
los permisos existentes de emisión de comprobantes de reservas o ventas, verificando
también el origen del documento. No modifica el correo del perfil del cliente.

Para habilitar SMTP, completar la configuración privada importada desde
`.local/application-local-private.properties`, sin subirla al repositorio:

```properties
app.correo.habilitado=true
app.correo.remitente=cuenta-del-hospedaje@ejemplo.com
spring.mail.host=servidor-smtp-del-proveedor
spring.mail.port=587
spring.mail.username=cuenta-del-hospedaje@ejemplo.com
spring.mail.password=CLAVE_PRIVADA_DEL_PROVEEDOR
```

STARTTLS y autenticación están activados por defecto. Para otro tipo de conexión,
ajustar las propiedades `spring.mail.properties.mail.smtp.*` según el proveedor.
También se admiten `MAIL_ENABLED`, `MAIL_FROM`, `MAIL_HOST`, `MAIL_PORT`,
`MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_AUTH`, `MAIL_STARTTLS` y `MAIL_SSL` del entorno.
El envío queda deshabilitado hasta configurarlo. La respuesta confirma aceptación
por el servidor SMTP; ante un resultado incierto no se reintenta automáticamente.

La estructura adicional se prepara manualmente con
[V18](src/main/resources/db/iteration/V18__emision_electronica.sql) y
[V19](src/main/resources/db/iteration/V19__columnas_compatibilidad_ddl_actual.sql),
aplicando solo los objetos ausentes. Flyway sigue deshabilitado y Hibernate valida
el esquema. La colección
[comprobantes-entrega](api/postman/comprobantes-entrega.postman_collection.json)
incluye consultas, PDF y simulación; omite la petición de correo salvo habilitación
explícita del entorno de pruebas.

`ComprobanteEntregaTest` verifica el PDF y sus dimensiones, la simulación local,
los permisos, el contenido MIME y los fallos SMTP sobre H2, sin enviar correos reales.

## Organización general

Backend con Java 21, Spring Boot, JPA y Oracle, adaptado al DDL propuesto con cliente
unificado. Mantiene controladores, servicios, implementaciones, repositorios,
entidades y DTO separados. El frontend Angular consume estos contratos.

El backend incluye el [flujo de reservas asistidas](API-RESERVAS.md):
ocupantes opcionales al reservar, pago completo al registrar, ingreso, consumos
cobrados al instante en cuentas propias, comprobantes internos y salida con
saldo cero. Presencial, llamada y WhatsApp se registran mediante un operador.
Para probar el recorrido completo se incluye
[reservas-flujo.postman_collection.json](api/postman/reservas-flujo.postman_collection.json).

La iteración de [gastos del turno](API-GASTOS.md) añade historial paginado,
filtros por fechas, turno, recepcionista, categoría, texto e importe, resumen
del total filtrado y registro sin duplicados mediante claveRegistro. Se prueba con
[gastos-historial.postman_collection.json](api/postman/gastos-historial.postman_collection.json).
La V9 y las categorías de desarrollo ya se prepararon en la base local.

El módulo de [ventas de productos](API-VENTAS.md) permite un carrito con varios
productos, editar borradores, confirmar todos sus stocks en una transacción y
cobrar el total dentro de la misma transacción. Los consumos de huéspedes
conservan la referencia a la reserva y se pagan en su propia cuenta. Los cobros y documentos usan un servicio común.
Se incluye [ventas-productos.postman_collection.json](api/postman/ventas-productos.postman_collection.json).
DATOS_VENTAS.sql prepara COBRO_VENTA; ya está aplicado en la base local.

[Caja del turno](API-CAJA.md) describe los ingresos manuales, los gastos y el cierre con arqueo por forma de pago.

Los mantenimientos siguen el patrón de Pharma:
`service/generic/CrudService<REQ, RES, ID>` → interfaz por módulo en
`service/service` → implementación en `service/impl` → controlador REST.
Habitaciones, mobiliario, productos, categorías, tipos de habitación y tipos de
mobiliario comparten las cinco operaciones de esa interfaz. Reservas, ventas y
gastos conservan acciones propias de negocio y sus transacciones.

## Casos implementados

1. Registrar reservas con una o varias habitaciones; completar ocupantes asociados a CLIENTE al reservar o antes del ingreso.
2. Mostrar estado y disponibilidad de habitaciones, sin alterar registros al consultar.
3. Registrar gastos del turno abierto del operador y consultar su historial filtrable y totales.
4. Registrar ventas de varios productos, editar borradores, confirmar stock, cobrar y emitir comprobantes internos; anular sin cobros con reposición.
5. Crear y editar habitaciones, su tipo, estado y límites de ocupación.
6. Crear y editar mobiliario, asignarlo, trasladarlo, retirarlo y consultar movimientos.
7. Crear y editar productos, desactivarlos y ajustar existencias con historial.

Los bloqueos y transacciones impiden reservas superpuestas y ventas por encima del
stock. El registro de reservas y ventas y los ajustes de inventario admiten claves
de operación para evitar duplicados. Los menores requieren un adulto de la misma
habitación; solo menores de siete años pueden alojarse sin cama dentro del límite.

## Base de datos y arranque

La referencia vigente es [DDL_REBOLSYS_ACTUAL.sql](src/main/resources/db/schema/DDL_REBOLSYS_ACTUAL.sql),
actualizada con el DDL proporcionado por el usuario: 46 tablas y auditoría
con `CLAVE_REGISTRO CLOB`. El código no modifica automáticamente Oracle.
No ejecutar un DDL de creación sobre una base poblada. La instancia debe incluir
todas las tablas y columnas antes de arrancar con `ddl-auto=validate`.

Los siguientes pasos corresponden al esquema **anterior REBOLSYSDBV2** y se conservan
como historial; no son una migración para el DDL vigente:

1. [DDL_REBOLSYS_V2.sql](src/main/resources/db/schema/DDL_REBOLSYS_V2.sql) crea las tablas base.
2. [V16](src/main/resources/db/iteration/V16__mobiliario_sin_detalle_habitacion.sql) adapta las claves históricas y agrega las columnas y la tabla de emisión que requiere la aplicación.
3. [DATOS_INICIALES_REBOLSYS_V2.sql](src/main/resources/db/schema/DATOS_INICIALES_REBOLSYS_V2.sql) carga catálogos y la cuenta `gerencia_local`.
4. [DATOS_ROLES_OFICIALES_V2.sql](src/main/resources/db/schema/DATOS_ROLES_OFICIALES_V2.sql) carga los cuatro roles oficiales. Confirma las cargas con `COMMIT` y configura los permisos operativos en **Seguridad**.
5. [DATOS_APROBACION_MANTENIMIENTO_V2.sql](src/main/resources/db/schema/DATOS_APROBACION_MANTENIMIENTO_V2.sql) habilita la atención de tickets, la carga de evidencias y la aprobación de informes para Mantenimiento. Confirma con `COMMIT`.

6. [V17](src/main/resources/db/iteration/V17__estados_limpieza_explicitos.sql) sincroniza los estados explícitos de limpieza y garantiza un solo informe activo por habitación. Ejecutar con la aplicación detenida, revisar el resultado y confirmar con `COMMIT`.

7. [V18](src/main/resources/db/iteration/V18__condiciones_de_conservacion.sql) restringe la conservación del mobiliario a los tres valores operativos. Si encuentra filas fuera del dominio, informa la tabla y la columna para que se revisen antes de migrar.

Hibernate valida el esquema al arrancar y Flyway está deshabilitado. El DDL anterior
y sus scripts de demostración se conservan como historial.

La conexión local está en `.local/application-local-private.properties`, excluido
de Git. También se admiten `DB_URL`, `DB_USERNAME` y `DB_PASSWORD` del entorno.
Para habilitar el primer acceso de gerencia, configura `REBOLSYS_GERENTE_USUARIO`
con `gerencia_local` y asigna una clave temporal privada en
`REBOLSYS_GERENTE_PASSWORD_TEMPORAL` antes de iniciar la API.

```powershell
.\mvnw.cmd spring-boot:run
```

API: http://localhost:8080/api/v1 · Swagger: http://localhost:8080/swagger-ui.html

Habitaciones, clientes, productos y mobiliario se registran desde la aplicación.
Las tarifas por habitación y el inventario documentado están en
[los datos de referencia](api/fixtures/README.md).

## Contrato y ejemplos

- [Organización de DTOs, records y compatibilidad JSON](DTO-GUIA.md).
- [Rutas y reglas de los siete casos](API-PROPUESTA.md).
- [Configuraciones de habitaciones, mobiliario y productos](API-CONFIGURACIONES.md).
- [Flujo de informes de limpieza y tickets de mantenimiento](API-LIMPIEZA.md).
- [Inicio de sesión y gestión de permisos](API-SEGURIDAD.md).
- [Facturación electrónica beta con APIsPerú](API-FACTURACION.md).
- [Prueba Postman de las configuraciones](api/postman/configuraciones.postman_collection.json).
- [Guía de prueba y resultados de esta reconstrucción](PRUEBAS-BACKEND.md).
- OpenAPI en `/v3/api-docs` y copia en [api/openapi.json](api/openapi.json).
- [Verificación Postman sin modificar datos](api/postman/verificacion.postman_collection.json).
- [Ejemplo Postman completo de los siete casos](api/postman/siete-casos.postman_collection.json).

La colección completa crea registros de demostración y requiere la preparación
inicial y COMMIT. Descubre el operador, turno y documentos desde catálogos;
crea tipos y categoría propios y conserva los registros que crea.
La colección de verificación usa consultas y peticiones
inválidas; funciona incluso con el esquema vacío. Variable `baseUrl` para cambiar
el puerto. Las colecciones permanecen locales; no contienen credenciales.

Los productos permiten registrar existencias iniciales en el alta, con responsable
y una entrada de inventario automática. También admiten descripción opcional de
hasta 500 caracteres (migración V13). Para reponer o corregir existencias se usa
`POST /productos/{id}/ajustes-stock`, indicando operador, saldo observado, saldo
nuevo, motivo y clave. Editar los datos del producto conserva las existencias.

Todos los perfiles requieren credenciales y JWT Bearer. El usuario autenticado se
consulta en `/api/v1/sesion/actual`; no existe un operador automático de desarrollo.
Completa el actor/turno de los DTO de operación y rechaza valores de otra sesión.
Las operaciones HTTP exitosas se registran en AUDITORIA_GENERAL. Véase
[API-SESION-OPERACION.md](API-SESION-OPERACION.md), incluido el flujo de confirmación
y pago de ventas. La autenticación real utiliza JWT Bearer; consulta [la seguridad](API-SEGURIDAD.md).
Los precios guardados en las líneas de una venta se mantienen al confirmarla y
representan el importe final con impuesto incluido. Las cuentas internas conservan
sus importes anteriores; la copia fiscal nueva desglosa base e impuesto por línea
sin aumentar el total cobrado. Los registros internos de boleta/factura se envían
electrónicamente mediante el módulo beta separado descrito en API-FACTURACION.md.

Para una base ya creada con el DDL original, aplicar
`src/main/resources/db/iteration/V8__consumos_separados_con_reserva.sql` y preparar
`src/main/resources/db/schema/DATOS_RESERVAS.sql` con COMMIT. La V8 permite que un
consumo separado conserve su estancia de origen; no agrega ni elimina columnas.
Ya se aplicó en la base local de desarrollo. Flyway sigue deshabilitado.

Las rutas operativas requieren inicio de sesión y permisos por funcionalidad.
`idUsuario` identifica al responsable que exige el DDL. APIs Perú conserva su consulta opcional
con `APISPERU_TOKEN`; los siete casos funcionan sin ese servicio.

## Datos de prueba de operación

La [carga manual de prueba](tools/datos-prueba/README.md) genera 30 reservas pagadas
en distintos estados, 50 clientes, 50 productos y 8 empleados con acceso mediante
los cuatro roles oficiales. Completa las habitaciones de referencia si faltan.
Desde la raíz del repositorio, ejecuta
`node rebolsysBackend/tools/datos-prueba/generar-semilla.mjs` y abre el SQL generado
en `rebolsysBackend/.local/datos-prueba-operacion/` con SQL Developer. La generación
no ejecuta inserts; los accesos privados permanecen fuera de Git.

## Pruebas

La [revisión de reservas y validaciones del 21 de noviembre de 2026](REVISION-2026-11-21.md)
documenta el checkout automático con saldo cero, la depuración de código y las
pruebas de regresión ejecutadas en backend y frontend.

```powershell
# Pruebas de modelo y errores sin conexión a Oracle
.\mvnw.cmd test

# Integración y concurrencia contra el esquema propuesto
$env:RUN_ORACLE_TESTS='true'
.\mvnw.cmd test

# Verificación HTTP local con Postman
postman collection run api/postman/verificacion.postman_collection.json --no-report-events
```

Las pruebas funcionales se revierten al terminar cada transacción. Las seis pruebas
de concurrencia necesitan transacciones independientes: crean registros propios y
los eliminan por sus claves en `finally`. No usan usuarios ni datos operativos
preexistentes. La prueba de modelo comprueba exactamente 47 tablas y 289 columnas con V11,
y que Java no intente escribir columnas calculadas.

Las pruebas del modelo anterior se conservaron localmente en
`docs/historico/pruebas-modelo-anterior`. Se sustituyeron por pruebas del contrato
propuesto. Las pruebas nuevas y los contratos de esta versión están incluidos en
el repositorio; las credenciales, informes de ejecución y archivos privados siguen
excluidos.

La validación original de siete funcionalidades se registra en PRUEBAS-BACKEND.md.
La nueva validación de reservas y sus casos límite se registra en API-RESERVAS.md.
La validación de gastos e historial se registra en API-GASTOS.md.
La validación de ventas (80 pruebas y 202 comprobaciones HTTP)
se registra en API-VENTAS.md. La iteración de configuraciones pasa 91 pruebas
automatizadas y 271 comprobaciones HTTP, documentadas en API-CONFIGURACIONES.md.
La primera iteración de facturación pasó 108 pruebas y 265 comprobaciones HTTP.
La configuración autenticada posterior pasó 113 pruebas automatizadas y dos
ejecuciones de Postman contra el beta real (26 comprobaciones). API-FACTURACION.md
registra las boletas/facturas aceptadas y distingue pruebas reales y simuladas.
Las 7 pruebas opcionales de consulta DNI/RUC de APIs Perú permanecen omitidas.

La facturación electrónica viene deshabilitada por defecto. En la configuración
privada local ya está habilitada con la empresa beta, verificada el 3 de noviembre.
La V10 ya se aplicó en Oracle local y agrega
EMISION_ELECTRONICA sin alterar Perfil, Persona Natural ni Persona Jurídica.
El token de consulta DNI/RUC no se reutiliza para emitir comprobantes.
