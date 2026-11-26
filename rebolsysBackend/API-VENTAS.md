# Venta de productos y consumos de reserva

El [flujo de cobro y liquidación](API-FLUJO-PAGOS-RESERVAS.md) define cómo cargar consumos a la cuenta de una reserva.

Todas las rutas requieren JWT y el permiso correspondiente. El usuario de la
operación se obtiene de la sesión autenticada; el turno debe estar abierto y ser suyo.

## Registro y cobro

1. Consultar GET /api/v1/productos/catalogo y seleccionar productos activos.
2. Seleccionar comprador externo o una estancia EN_CURSO.
3. POST /api/v1/ventas/confirmadas recibe el carrito y el cobro cuando corresponde.
   El servidor toma los precios del catálogo y comprueba precioEsperado.
4. Una transacción registra venta, líneas, descuento de stock y cargos.
   Un cobro inmediato registra también el pago y el ingreso COBRO_VENTA.
5. Consultar el saldo en la cuenta de venta si se cobró por separado o en la
   cuenta de la reserva si el consumo se cargó al hospedaje.

El cobro contiene idFormaPago, monto y numeroOperacion opcional. Su monto debe
coincidir exactamente con el total: no se permiten abonos ni sobrepagos al confirmar.
El efectivo recibido y el vuelto se calculan en el formulario; solo el total se cobra.
Una venta gratuita no genera un pago de importe cero ni un ingreso ficticio.

Ejemplo de POST /api/v1/ventas/confirmadas, con identificadores de una base de pruebas:

~~~json
{
  "claveRegistro": "venta-unica-001",
  "compradorNombreLibre": "Lucía Rojas Medina",
  "modalidadCobro": "SEPARADA",
  "detalles": [{"idProducto": 1, "cantidad": 2, "precioEsperado": 3.50}],
  "cobro": {"idFormaPago": 1, "monto": 7.00, "numeroOperacion": null}
}
~~~

## Relación con reservas

- SEPARADA exige idDetalleReserva nulo: venta externa.
- RESERVA exige idDetalleReserva de una estancia EN_CURSO. Si se omite cobro,
  se carga a la cuenta principal de la reserva. Si se envía un cobro completo,
  la venta usa CUENTA-V-{idVenta} y puede tener su propio comprobante.
- Los consumos históricos ya consolidados permanecen en la cuenta de alojamiento.
  No se trasladan, perdonan ni cobran dos veces. Sus saldos se regularizan por las
  rutas existentes de pagos de reserva.

## Borradores, reintentos y seguridad

POST /ventas conserva borradores sin entregar productos ni descontar stock.
PUT /ventas/{id} permite modificarlos. Para confirmarlos, POST
/ventas/{id}/confirmacion exige {"cobro":{"idFormaPago":1,"monto":7.00}}.
Sin cobro completo no se confirma ningún borrador.

La claveRegistro y la firma incluyen el cobro. Repetir la misma solicitud devuelve
la misma venta; cambiar el contenido con la misma clave produce 409. El pago tiene
una clave estable por cuenta. Reintentar no duplica stock, pago ni ingreso de caja.
Las ventas pagadas conservan la protección contra anulaciones sin devolución.
No se realiza un cobro externo automático a tarjetas ni billeteras: se registra
el medio y la operación recibida, como en el flujo existente de caja.

## Verificación

La colección api/postman/ventas-productos.postman_collection.json refleja este
contrato y crea datos: usar exclusivamente una base de pruebas, con JWT y catálogos
preparados. CobroInmediatoIntegracionTest comprueba transacciones reales en H2,
reintentos concurrentes, fallos de cobro, pagos parciales y descuento único de stock.
La validación contra Oracle se realiza aparte y no se presume a partir de H2.
