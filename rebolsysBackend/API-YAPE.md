# Cobros con Yape vía Mercado Pago

REBOLSYS integra cobros con **Yape** usando **Checkout API vía Orders de Mercado Pago Perú** y un **simulador local** para pruebas sin credenciales externas.

Aplica [V23__pago_yape.sql](src/main/resources/db/iteration/V23__pago_yape.sql) una vez en el esquema Oracle del proyecto para crear la tabla `PAGO_YAPE` y asegurar la forma de pago `YAPE`.

## Requisitos

- Reserva con estancia `CONFIRMADA` o `EN_CURSO` y saldo principal pendiente.
- Usuario activo con rol `RECEPCIONISTA`, permiso `RESERVAS:COBRAR` y turno de caja abierto.
- El comprobante final se emite en el flujo ordinario de check-out.

| Variable | Uso | Predeterminado |
| --- | --- | --- |
| `YAPE_HABILITADO` | Habilita o deshabilita el canal Yape | `true` (`false` en `prod`) |
| `YAPE_MODO` | `SIMULADO` o `MERCADO_PAGO` | `SIMULADO` |
| `YAPE_MP_AMBIENTE` | `PRUEBA` o `PRODUCCION` | `PRUEBA` |
| `MP_YAPE_PUBLIC_KEY` | Public Key de Mercado Pago Perú | vacío |
| `MP_YAPE_ACCESS_TOKEN` | Access Token `APP_USR-...` de Mercado Pago Perú | vacío |
| `MP_YAPE_SELLER_ID` | ID numérico de la cuenta vendedor en Mercado Pago | vacío |
| `MP_YAPE_WEBHOOK_SECRET` | Secreto de firma HMAC-SHA256 del webhook | vacío |
| `MP_YAPE_PAYER_EMAIL` | Correo técnico enviado en la orden cuando no se solicita al pagador | `pagos-yape@rebolsys.local` |

## Flujo en la aplicación

1. En el detalle de una reserva con saldo pendiente, el panel **Cobro con Yape** permite ingresar:
   - **Importe a cobrar (S/)** (total o abono parcial hasta el saldo vigente).
   - **Celular Yape** (9 dígitos).
   - **Código de aprobación (OTP)** (6 dígitos generados desde la app Yape del pagador).
2. En modo `SIMULADO`:
   - Cualquier celular de 9 dígitos y OTP de 6 dígitos aprueba el cobro inmediatamente.
   - OTP `000000` (o teléfono terminado en `2`) simula un rechazo (`RECHAZADO`).
   - OTP `999999` (o teléfono terminado en `3`) deja el intento `PENDIENTE`; luego **Consultar estado** lo concilia a `APLICADO`.
3. En modo `MERCADO_PAGO`:
   - El backend tokeniza el teléfono y OTP en `/platforms/pci/yape/v1/payment` y crea la orden en `/v1/orders` con `X-Idempotency-Key`.
   - En `YAPE_MP_AMBIENTE=PRUEBA`, valida que el vendedor sea cuenta de prueba de Perú (`MPE`) y exige `live_mode=false`.
   - En `YAPE_MP_AMBIENTE=PRODUCCION`, exige cuenta productiva de Perú y `live_mode=true`.
   - El teléfono se enmascara (`*** *** 321`) al persistir en `PAGO_YAPE` y el OTP nunca se guarda.

## Endpoints

| Método y ruta | Autorización | Comportamiento |
| --- | --- | --- |
| `GET /api/v1/pagos/yape/config` | Sesión JWT | Indica `habilitado`, `modo` y `ambiente`. |
| `GET /api/v1/reservas/{id}/yape` | `RECEPCIONISTA` + `RESERVAS:COBRAR` | Devuelve el último intento de la reserva o `204`. |
| `POST /api/v1/reservas/{id}/yape` | `RECEPCIONISTA` + `RESERVAS:COBRAR` | Crea o recupera idempotentemente el cobro con teléfono y OTP. |
| `POST /api/v1/reservas/{id}/yape/{clave}/conciliar` | `RECEPCIONISTA` + `RESERVAS:COBRAR` | Consulta y aplica el estado vigente de la orden pendiente. |
| `POST /api/v1/webhooks/yape` | Pública (firma `x-signature`) | Verifica HMAC-SHA256 y consulta la orden en Mercado Pago antes de aplicarla. |

[Documentación de Yape (Mercado Pago Perú)](https://www.mercadopago.com.pe/developers/es/docs/checkout-api-orders/payment-integration/websites/yape)
· [Credenciales](https://www.mercadopago.com.pe/developers/en/docs/checkout-api-orders/create-application)
· [Notificaciones de órdenes](https://www.mercadopago.com.pe/developers/es/docs/checkout-api-orders/notifications)
