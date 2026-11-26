# Especificación del cobro y la liquidación de reservas

La recepción puede registrar una reserva, cargar consumos durante la estancia y cobrar el saldo al hacer el check-out. Cada acción se atribuye al operador que la ejecuta. El titular de la reserva, el comprador y el receptor del comprobante se registran como datos del cliente.

El flujo se implementa en `develop-gamarra`. Consulta los contratos de [reservas](API-RESERVAS.md) y [ventas](API-VENTAS.md) para los detalles de cada ruta.

## Usuarios y turnos

Las referencias de esta tabla corresponden al [esquema Oracle de la aplicación](src/main/resources/db/schema/DDL_REBOLSYS_ACTUAL.sql). La identidad del operador se resuelve con la [sesión de operación](API-SESION-OPERACION.md).

| Registro | Dato en Oracle | Regla |
| --- | --- | --- |
| Reserva | `RESERVA.idUsuario` | Usuario autenticado que registra la reserva. El backend lo asigna al crearla. Los cobros, consumos y cambios de estado posteriores conservan esa atribución. |
| Venta | `VENTA.idUsuario` | Usuario autenticado que registra la venta. Una venta a un huésped queda vinculada a su estancia mediante `VENTA.idDetalleReserva`. |
| Pago | `PAGO.idUsuario`, `PAGO.idTurno` | Recepcionista que efectúa ese cobro y su turno de caja vigente. El ingreso asociado se registra en el mismo turno. |
| Comprobante emitido | `COMPROBANTE.idTurno` → `TURNO_CAJA.idUsuario` | Turno vigente del operador que emite. Si una cuenta en borrador se emite en otro turno, el comprobante toma el turno de emisión. Cada pago conserva el suyo. |

- El backend obtiene el actor de la sesión JWT y comprueba que su cuenta esté activa. El cliente puede omitir `idUsuario` e `idTurno`. Si los envía, ambos deben coincidir con los que resuelve el servidor para la acción.
- **Cobrar una reserva exige el rol `RECEPCIONISTA` y el permiso `RESERVAS:COBRAR`.** Se aplica a anticipos, cobros posteriores y check-out con cobro, aunque la reserva o la venta las hubiera registrado otra persona. El permiso por sí solo y el rol `GERENTE` por sí solo no autorizan el cobro.
- Registrar una reserva, cargar un consumo, hacer check-in, registrar el check-out, cancelar y emitir conservan sus permisos de operación. Cargar un consumo requiere `RESERVAS:REGISTRAR_CONSUMO` y la autorización aplicable a la confirmación de la venta. Si una sola solicitud de check-out incluye cobro y emisión, el actor debe tener los permisos de las tres acciones. Recepción puede cobrar y emitir en operaciones separadas con operadores autorizados distintos.
- Cada cobro y cada emisión requieren exactamente un turno abierto del actor, con `estadoSesion = 1` y `fechaCierre` nula. El servicio vuelve a validar su vigencia dentro de la transacción. Una reserva sin cobro se puede registrar sin abrir un turno para esa operación. Si el registro incluye un anticipo, se aplican las reglas del cobro.
- En el registro ordinario, la asignación de `RESERVA.idUsuario` y `VENTA.idUsuario` se hace desde la sesión. Si el negocio necesita asignar administrativamente un responsable diferente, esa asignación requerirá un campo o una acción específica que preserve la autoría original.

## Estados de la estancia

Oracle guarda el estado por habitación en `DETALLE_RESERVA`. Una reserva de varias habitaciones puede tener detalles en estados distintos.

| Estado | Cuándo se registra | Efecto |
| --- | --- | --- |
| `CONFIRMADA` | Al registrar una reserva válida y apartar la habitación para el periodo. | La reserva puede quedar con saldo pendiente. |
| `EN_CURSO` | Al hacer check-in de una estancia confirmada. | Guarda la hora real de ingreso, ocupa la habitación y permite cargar consumos. |
| `FINALIZADA` | Al registrar el check-out de una estancia en curso, con todo el saldo exigible de la reserva liquidado en ese momento. | Guarda la hora real de salida y deja la habitación `POR_LIMPIAR`. El pago por sí solo conserva la estancia en curso. |
| `CANCELADA` | Cuando el cliente solicita la baja antes del check-out. | Antes del check-in, libera la reserva de la habitación. Si ya hubo pagos, comprobantes o ingreso, la baja debe pasar por la regularización que corresponda y conservar el historial. Si la habitación se ocupó, queda `POR_LIMPIAR` al liberarla. |

La transición normal es `CONFIRMADA → EN_CURSO → FINALIZADA`. La cancelación de una estancia en curso se considera una baja excepcional. Las reglas para valorar el alojamiento ya prestado y decidir devoluciones o rectificaciones requieren definición antes de habilitar esa transición. Mientras haya importes o documentos por regularizar, la operación devuelve un conflicto y conserva el estado. Una estancia finalizada se rectifica mediante el flujo que corresponda a sus documentos y pagos.

La finalización se registra al hacer el check-out. El cierre automático queda desactivado por defecto. Si una instalación ya tenía `CHECKOUT_AUTOMATICO_HABILITADO=true`, debe quitar ese ajuste para que recepción registre la salida manualmente.

## Flujo de cobro y emisión

1. **Registrar reserva.** `POST /api/v1/reservas` calcula el alojamiento con las tarifas del servidor y registra las habitaciones como confirmadas. El campo `cobro` pasa a ser opcional. Si se incluye, puede cubrir un anticipo o el total vigente. El registro y su anticipo, si existe, se guardan en una transacción.
2. **Registrar check-in.** `POST /api/v1/reservas/detalles/{id}/ingreso` valida ocupantes, fechas y disponibilidad. Registra el ingreso aunque quede un saldo pendiente.
3. **Cargar un consumo.** `POST /api/v1/reservas/{idReserva}/consumos` registra y confirma la venta asociada a una estancia `EN_CURSO`. Aplica el movimiento de stock una vez y agrega sus líneas a la cuenta principal de la reserva, con sus referencias a `DETALLE_VENTA`. La cuenta principal se crea cuando se necesita registrar el primer pago o consumo. Cuando se crea, el borrador toma un turno vigente del operador. La venta a un comprador externo conserva su cuenta y flujo de cobro independiente.
4. **Consultar y abonar.** `GET /api/v1/reservas/{idReserva}/cuenta` muestra alojamiento, consumos, cobros verificados y saldo. `POST /api/v1/reservas/{idReserva}/pagos` permite aplicar abonos a la cuenta principal o regularizar una cuenta histórica individual. El backend valida importe positivo y evita sobrepagos. El pago y su ingreso de caja se guardan juntos.
5. **Registrar check-out.** `POST /api/v1/reservas/detalles/{id}/salida` acepta un cobro opcional por el saldo pendiente de la cuenta principal. El backend calcula el saldo vigente dentro de la transacción. Con deuda en esa cuenta, el importe enviado debe cubrirla por completo. Las cuentas históricas separadas deben quedar liquidadas antes de la salida. Con saldo cero, basta registrar la salida. Si falla el cobro o una validación local, se revierte toda esa solicitud.
6. **Emitir el comprobante.** En el último check-out se puede solicitar una boleta o factura interna con el alojamiento y las ventas pendientes de emisión. También puede emitirse después mediante `POST /api/v1/reservas/{idReserva}/comprobantes`. En ambos casos se toma el turno vigente de quien emite. La emisión usa todos los cargos incluidos y descuenta los abonos solo al calcular el saldo por cobrar. El envío electrónico sigue la operación de facturación existente.

### Cuenta principal y datos históricos

- La cuenta principal `CUENTA-R-{idReserva}` representa el alojamiento y los consumos cargados al hospedaje. `COMPROBANTE.idReserva` identifica la reserva y cada línea de `COMPROBANTE_DETALLE` referencia su `idDetalleReserva` o su `idDetalleVenta`. Una misma venta puede tener varias líneas y cada línea se incorpora una vez. Una estancia cancelada sin cargos exigibles queda fuera del total.
- El comprobante final del nuevo flujo incluye todas las líneas de alojamiento y consumo de la cuenta principal. Una venta con un comprobante ya emitido se muestra en la liquidación con su propio documento. Su importe y su pago se cuentan una vez. Para cuentas separadas históricas, se conserva el vínculo con sus pagos y se regularizan por separado antes de finalizar. Su consolidación requeriría un procedimiento de traslado de cargos y pagos que conserve la trazabilidad.
- Para una reserva con varias habitaciones se puede registrar la salida de cada estancia. Se valida el saldo de la reserva en cada salida. El comprobante consolidado se habilita al cerrar la última estancia, cuando todos los demás detalles están finalizados o cancelados. Así, los consumos que se agreguen después de una salida anterior pueden quedar incluidos.

## Contrato del check-out

Ejemplo para un saldo pendiente de S/ 74.00 y emisión interna en la misma operación.

```http
POST /api/v1/reservas/detalles/501/salida
Authorization: Bearer {{jwtRecepcionista}}
Content-Type: application/json
```

```json
{
  "claveOperacion": "salida-501-01",
  "cobro": {
    "idFormaPago": 1,
    "monto": 74.00,
    "numeroOperacion": null
  },
  "comprobante": {
    "tipo": "FACTURA",
    "idClienteReceptor": 900
  }
}
```

- `claveOperacion` identifica el check-out. Es obligatoria si incluye cobro o emisión. Cuando se envía, se guarda junto con el actor, la estancia y el resultado, incluso si el check-out se hace con saldo cero. Las solicitudes anteriores sin clave funcionan para salidas con saldo cero. `cobro` se omite cuando el saldo es cero. `comprobante` se omite si la emisión se hará después. Si la solicitud incluye la emisión, esta participa en la misma transacción local y el backend valida tipo y receptor antes de confirmar la operación.
- La respuesta `200` incluye el detalle de estancia con `estadoDetalle` y `fechaSalidaReal`, el `saldoPendiente` y, cuando se generan, `idPago` e `idComprobante`. Repetir una operación confirmada con la misma clave, actor y datos recupera los resultados guardados, aunque el turno de aquella operación haya cerrado. Una clave ya usada con otro actor, alcance o contenido devuelve `409`.
- Una sesión ausente o inválida devuelve `401`. La falta de rol o permiso devuelve `403`. Un importe inválido devuelve `400`. La falta de turno vigente para cobrar o emitir, la modificación del saldo o un estado incompatible devuelven `409`. Si quedan saldos en cuentas históricas separadas, la respuesta indica las cuentas que deben regularizarse antes de registrar la salida.
- El backend serializa consumos, cobros y check-out sobre la misma reserva y vuelve a leer sus saldos antes de finalizar. Dos intentos simultáneos no producen dos pagos, salidas ni números de comprobante.

## Caso de aceptación con tres operadores

| Momento | Acción | Resultado esperado |
| --- | --- | --- |
| Día 1 | Ana registra una reserva de S/ 80.00 y cobra S/ 20.00 de anticipo en el turno 101. | `RESERVA.idUsuario` identifica a Ana. El anticipo registra a Ana y el turno 101. Quedan S/ 60.00 por pagar. |
| Día 2 | Bruno registra el check-in y una venta de S/ 14.00 a la habitación. | La estancia queda `EN_CURSO`. `VENTA.idUsuario` identifica a Bruno. El consumo queda cargado a la reserva y el saldo sube a S/ 74.00. |
| Día 3 | Carla cobra S/ 74.00, registra el check-out y emite la factura interna en el turno 303. | `PAGO` guarda a Carla y el turno 303. El comprobante se emite en el turno 303 por S/ 94.00, con el alojamiento y el producto. La estancia queda `FINALIZADA`, el saldo queda en cero y el anticipo conserva el turno 101. |

Verificar también el flujo con una sola persona en distintos turnos, el cobro por un recepcionista diferente al creador, el rechazo a un usuario sin rol o permiso, la emisión posterior por otro operador, el cambio de saldo durante el check-out, los reintentos simultáneos y la cancelación sin pagos frente a una baja que requiere regularización.
