# Reservas con pago completo al registrar

El [flujo objetivo de cobro y liquidación](API-FLUJO-PAGOS-RESERVAS.md) define el cambio a saldo pendiente y facturación consolidada al check-out.

El registro lo realiza un operador autenticado mediante JWT. El titular puede ser
una persona natural adulta o una empresa; los ocupantes son personas naturales.

## Proceso

1. Seleccionar titular, habitaciones, fechas y ocupantes opcionales.
2. Elegir la forma de pago y confirmar el total del alojamiento. POST
   /api/v1/reservas requiere cobro: {idFormaPago, monto, numeroOperacion opcional}.
3. El servicio valida disponibilidad, identidades y ocupantes; guarda reserva,
   estancias, ocupantes, cuenta, pago e ingreso COBRO_RESERVA en una transacción.
   Si falla el cobro o no coincide con el total, se revierte toda la reserva.
4. Completar ocupantes antes del check-in. Cada menor necesita un responsable
   adulto en su habitación; se mantienen los límites de personas y camas.
5. Registrar check-in dentro del periodo reservado. La habitación pasa a OCUPADO.
6. Las ventas a huéspedes se pagan completas al registrarse, en cuentas propias.
   El alojamiento no recibe cargos nuevos a pagar durante el check-out.
7. El check-out conserva la comprobación de saldo cero y deja la habitación
   POR_LIMPIAR. Los saldos históricos deben liquidarse antes de la salida.
8. Al llegar la hora programada en Lima, el detalle de una estancia EN_CURSO
   sin salida registrada devuelve `salidaPendiente: true`. La habitación sigue
   ocupada hasta que recepción registra la salida y se guarda su hora real.

GET es de solo lectura. El filtro **Salidas pendientes** muestra las reservas
con al menos una habitación por revisar. La lista Angular se actualiza cada
30 segundos mientras no hay un diálogo u operación activa.

El saldo debe ser cero en cada cuenta: un sobrepago en alojamiento no compensa una
deuda de consumos para autorizar el checkout. Una nueva reserva debe tener su
salida en el futuro, aunque se admite registrar el ingreso previsto anterior a la
hora actual para una llegada del mismo día.

El cierre automático está desactivado por defecto en `application.yaml`.
Con `CHECKOUT_AUTOMATICO_HABILITADO=true`, el servidor vuelve a finalizar
estancias vencidas con saldo liquidado. Ese modo usa
`CHECKOUT_AUTOMATICO_INTERVALO` (60000 ms) y
`CHECKOUT_AUTOMATICO_ESPERA_INICIAL` (10000 ms).

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
  "cobro": {"idFormaPago": 1, "monto": 80.00, "numeroOperacion": null}
}
~~~

El servidor incorpora el usuario autenticado y resuelve su único turno abierto.
El servidor recalcula el alojamiento con la tarifa efectiva de cada habitación y
noches de hasta 24 horas, igual que el formulario. Rechaza tarifas desactualizadas
e importes manipulados. El monto debe cubrir exactamente ese total. Repetir
claveRegistro y contenido, incluido cobro, recupera la misma reserva sin otro pago.
Una clave usada con contenido diferente produce 409.

## Rutas conservadas

| Ruta bajo /api/v1 | Función |
| --- | --- |
| GET /reservas y /reservas/{id} | Listar y consultar reservas. |
| GET /reservas/{id}/cuenta | Consultar alojamiento, cuentas propias de ventas y pagos; solo lectura. |
| PUT /reservas/detalles/{id}/ocupantes | Completar ocupantes antes del ingreso. |
| POST /reservas/detalles/{id}/ingreso | Check-in con ocupantes y disponibilidad validados. |
| POST /reservas/detalles/{id}/salida | Check-out después de saldar todas las cuentas. |
| POST /reservas/{id}/consumos | Registrar venta asociada con cobro completo en la misma transacción. |
| POST /reservas/{id}/pagos | Regularizar saldos históricos; no se necesita después del nuevo registro. |
| POST /reservas/{id}/comprobantes | Emitir documento interno de alojamiento o de una venta concreta. |

admiteConsumosConsolidados se conserva como campo de respuesta con valor false.
Las cuentas históricas y sus pagos se mantienen. Una reserva pagada conserva las
restricciones de cancelación: requiere gestionar devolución o rectificación.
No se elimina ni devuelve dinero de manera automática.

## Pruebas

api/postman/reservas-flujo.postman_collection.json usa JWT y una base de pruebas:
crea alojamiento pagado por S/80, tres consumos pagados por S/14 en cuentas propias,
registra ingreso y salida y comprueba cuatro cuentas con saldo cero. No ejecutarla
sobre datos de producción. Las pruebas H2 verifican atomicidad, rechazo de abonos,
reintentos y check-in/check-out; las pruebas opcionales Oracle requieren revisión
contra el esquema y los catálogos de la instancia antes de habilitarse.

La [revisión del 21 de noviembre de 2026](REVISION-2026-11-21.md) detalla los
casos nuevos, los resultados y el alcance de las comprobaciones.
