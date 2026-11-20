# Datos de prueba de operación

Carga manual para el DDL actual de REBOLSYS en Oracle 19c o posterior, incluidas
las columnas de compatibilidad V19. No instala tablas, índices, triggers ni
procedimientos. El generador no conecta con Oracle ni ejecuta la carga.

## Contenido

| Datos | Cantidad / comportamiento |
| --- | --- |
| Reservas históricas finalizadas | 10 |
| Checkout reciente | 5; habitaciones pendientes de limpieza |
| Estancias en curso | 5; habitaciones ocupadas |
| Confirmadas con check-in pendiente hoy | 5 |
| Confirmadas futuras | 5 |
| Clientes | 50; DNI, CE, pasaporte, RUC 10 y RUC 20 |
| Productos | 50; bebidas, snacks, higiene, botiquín y accesorios |
| Personal con cuenta activa | 1 gerente, 3 recepcionistas, 2 de limpieza y 2 de mantenimiento |

Todas las reservas se pagan íntegramente al registrarlas; se alternan efectivo,
Yape, Plin y tarjeta. Se incluyen cuentas internas, pagos verificados, flujos de
caja y arqueos de turnos históricos cerrados. Las fechas se calculan al ejecutar
el SQL, en `America/Lima`.

Completa las 20 habitaciones del inventario de Bolognesi si faltan y equipa
únicamente las habitaciones nuevas con mobiliario básico (92 ítems en una base
vacía). Requiere 15 habitaciones disponibles sin reservas coincidentes. Conserva
las tarifas y los permisos de roles existentes; inicializa permisos solo para
roles nuevos. Limpieza no puede aprobar informes; mantenimiento sí.

## Generar y ejecutar

Con Node.js, Java 21 y las dependencias Maven del backend instaladas, desde la raíz:

```powershell
node rebolsysBackend/tools/datos-prueba/generar-semilla.mjs
```

1. Abre `.local/datos-prueba-operacion/DATOS_PRUEBA_OPERACION.sql`, dentro del backend.
2. Usa una conexión dedicada al esquema de pruebas configurado en la aplicación,
   sin otras transacciones pendientes. Ejecuta el archivo completo con **F5** en
   SQL Developer; no ejecutes `operacion.template.sql`.
3. El bloque comprueba pagos y stock, confirma con `COMMIT` o revierte sus cambios
   si falla. Ejecuta `VERIFICAR_DATOS_PRUEBA_OPERACION.sql` para localizar los casos.
4. Consulta `ACCESOS_PRIVADOS.txt` en la misma carpeta privada. Cada cuenta tiene
   una contraseña aleatoria, temporal y distinta; debe cambiarse al entrar y vence
   24 horas después de cargar los datos.

Los nombres y documentos son ficticios; precios y saldos son de prueba. Los correos
usan `.test` y no reciben mensajes. Para probar envíos, cambia el correo del cliente
desde el sistema por una dirección tuya.

El SQL contiene hashes BCrypt; los accesos y archivos generados quedan excluidos
de Git. Repetir el generador conserva las contraseñas; repetir el lote completo
no lo duplica. Una carga parcial o una identidad coincidente detiene el proceso.
Si solo se corrige la plantilla SQL, `--actualizar-plantilla` conserva los accesos;
rechaza cambios de catálogo para evitar mezclar versiones del mismo lote.
