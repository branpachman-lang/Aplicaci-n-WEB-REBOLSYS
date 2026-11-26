# Reservas con anticipos y liquidación al check-out

El [flujo de cobro y liquidación](API-FLUJO-PAGOS-RESERVAS.md) describe los permisos, la trazabilidad y el contrato de check-out.

El registro lo realiza un operador autenticado mediante JWT. El titular puede ser
una persona natural adulta o una empresa; los ocupantes son personas naturales.

## Proceso

1. Seleccionar titular, habitaciones, fechas y ocupantes opcionales.
2. Registrar la reserva con saldo pendiente, con un anticipo o con el pago total.
   Si se cobra, el recepcionista debe tener el permiso de cobro y un turno abierto.
3. El servicio valida disponibilidad, identidades y ocupantes y guarda la reserva,
   sus estancias y, cuando se incluye un anticipo, el pago en una transacción.
4. Completar ocupantes antes del check-in. Cada menor necesita un responsable
   adulto en su habitación; se mantienen los límites de personas y camas.
5. Registrar check-in dentro del periodo reservado. La habitación pasa a OCUPADO.
6. Las ventas a huéspedes pueden cargarse a la cuenta principal de la reserva.
   Las ventas que se cobran de inmediato conservan su propia cuenta.
7. El check-out liquida el importe pendiente de la cuenta principal y deja la
   habitación POR_LIMPIAR. Las cuentas históricas separadas se liquidan antes.
8. El comprobante interno de la cuenta principal se emite al registrar la última
   salida o después. La interfaz de recepción ofrece emitirlo tras ese check-out.

El saldo debe ser cero en cada cuenta: un sobrepago en alojamiento no compensa una
deuda de consumos para autorizar el checkout. Al registrar una nueva reserva,
el ingreso previsto debe estar en el futuro y la salida debe ser posterior.

El cierre automático está deshabilitado por defecto. Quita
`CHECKOUT_AUTOMATICO_HABILITADO=true` si la instalación lo activaba expresamente.
Para actualizar una base existente, aplica [V22](src/main/resources/db/iteration/V22__operaciones_salida_reserva.sql).

## Contrato

Ejemplo de POST /api/v1/reservas (sustituir identificadores y fechas):

~~~json
{
  "claveRegistro": "reserva-unica-001",
  "idCliente": 1,
  "detalles": [{
    "idHabitacion": 1,
    "fechaIngreso": "2026-12-01T14:00:00",
    "fechaSalida": "2026-12-02T12:00:00",
    "costoEstadia": 80.00,
    "ocupantes": []
  }],
  "cobro": {"idFormaPago": 1, "monto": 20.00, "numeroOperacion": null}
}
~~~

El servidor incorpora el usuario autenticado y, si hay anticipo, resuelve su turno abierto.
El servidor recalcula el alojamiento con la tarifa efectiva de cada habitación y
noches de hasta 24 horas, igual que el formulario. Rechaza tarifas desactualizadas
e importes manipulados. El anticipo debe ser positivo y estar dentro del saldo. Repetir
claveRegistro y contenido, incluido cobro, recupera la misma reserva sin otro pago.
Una clave usada con contenido diferente produce 409.

## Rutas conservadas

| Ruta bajo /api/v1 | Función |
| --- | --- |
| GET /reservas y /reservas/{id} | Listar y consultar reservas. |
| GET /reservas/{id}/cuenta | Consultar alojamiento, cuentas propias de ventas y pagos; solo lectura. |
| PUT /reservas/detalles/{id}/ocupantes | Completar ocupantes antes del ingreso. |
| POST /reservas/detalles/{id}/ingreso | Check-in con ocupantes y disponibilidad validados. |
| POST /reservas/detalles/{id}/salida | Registrar check-out y, opcionalmente, el cobro del saldo principal y la emisión interna. |
| POST /reservas/{id}/consumos | Registrar una venta asociada y cargarla a la cuenta principal si se omite el cobro. |
| POST /reservas/{id}/pagos | Registrar abonos en la cuenta principal o liquidar una cuenta histórica separada. |
| POST /reservas/{id}/comprobantes | Emitir la cuenta principal tras la última salida o emitir un documento de una venta separada. |

admiteConsumosConsolidados indica si la cuenta admite cargos de una estancia en curso.
Las cuentas históricas y sus pagos se mantienen. Una reserva pagada conserva las
restricciones de cancelación: requiere gestionar devolución o rectificación.
No se elimina ni devuelve dinero de manera automática.

## Pruebas

api/postman/reservas-flujo.postman_collection.json usa JWT y una base de pruebas:
crea alojamiento pagado por S/80, tres consumos pagados por S/14 en cuentas propias,
registra ingreso y salida y comprueba cuatro cuentas con saldo cero. Úsala con
un recepcionista autorizado para el cobro. Las pruebas H2 verifican anticipos,
trazabilidad por operador, rollback y reintentos de check-out. Las pruebas opcionales Oracle requieren revisión
contra el esquema y los catálogos de la instancia antes de habilitarse.

La [revisión del 21 de noviembre de 2026](REVISION-2026-11-21.md) detalla los
casos nuevos, los resultados y el alcance de las comprobaciones.
