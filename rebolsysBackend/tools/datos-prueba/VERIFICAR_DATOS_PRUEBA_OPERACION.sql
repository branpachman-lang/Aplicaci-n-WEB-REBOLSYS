-- Solo consultas. Ejecutar después de la carga para identificar los casos de prueba.
-- El estado de habitaciones y saldos puede cambiar al probar el sistema.
SET DEFINE OFF

SELECT CASE
  WHEN TO_NUMBER(SUBSTR(r.claveRegistro,-3))<=10 THEN 'Histórica finalizada'
  WHEN TO_NUMBER(SUBSTR(r.claveRegistro,-3))<=15 THEN 'Checkout reciente / limpieza'
  WHEN TO_NUMBER(SUBSTR(r.claveRegistro,-3))<=20 THEN 'En curso / checkout disponible'
  WHEN TO_NUMBER(SUBSTR(r.claveRegistro,-3))<=25 THEN 'Check-in pendiente hoy'
  ELSE 'Confirmada futura' END escenario,
  d.estadoDetalle,COUNT(*) cantidad
FROM reserva r JOIN detalle_reserva d ON d.idReserva=r.idReserva
WHERE r.claveRegistro LIKE 'SEMILLA-RB-OPERACION-01-R-%'
GROUP BY CASE
  WHEN TO_NUMBER(SUBSTR(r.claveRegistro,-3))<=10 THEN 'Histórica finalizada'
  WHEN TO_NUMBER(SUBSTR(r.claveRegistro,-3))<=15 THEN 'Checkout reciente / limpieza'
  WHEN TO_NUMBER(SUBSTR(r.claveRegistro,-3))<=20 THEN 'En curso / checkout disponible'
  WHEN TO_NUMBER(SUBSTR(r.claveRegistro,-3))<=25 THEN 'Check-in pendiente hoy'
  ELSE 'Confirmada futura' END,d.estadoDetalle;

SELECT r.idReserva,h.numeroCuarto,d.estadoDetalle,h.estadoHabitacion,
  CASE WHEN n.idPerfil IS NOT NULL THEN n.nombres||' '||n.apellidos ELSE j.razonSocial END titular,
  d.fechaIngreso,d.fechaSalida,d.fechaIngresoReal,d.fechaSalidaReal
FROM reserva r JOIN detalle_reserva d ON d.idReserva=r.idReserva
JOIN habitacion h ON h.idHabitacion=d.idHabitacion
LEFT JOIN persona_natural n ON n.idPerfil=r.idPerfil
LEFT JOIN persona_juridica j ON j.idPerfil=r.idPerfil
WHERE r.claveRegistro LIKE 'SEMILLA-RB-OPERACION-01-R-%'
ORDER BY r.claveRegistro;

-- Debe devolver saldo cero en las 30 reservas al terminar la carga.
SELECT r.idReserva,c.totalPagar,COALESCE(SUM(p.monto),0) pagado,
  c.totalPagar-COALESCE(SUM(p.monto),0) saldo
FROM reserva r JOIN comprobante c ON c.idReserva=r.idReserva
LEFT JOIN pago p ON p.idComprobante=c.idComprobante AND p.verificado=1
WHERE r.claveRegistro LIKE 'SEMILLA-RB-OPERACION-01-R-%'
GROUP BY r.idReserva,c.idComprobante,c.totalPagar ORDER BY r.idReserva;

SELECT p.nombreProducto,c.nombreCategoria,p.precioActual,p.stock,p.stockMinimo,
  CASE WHEN p.stock=0 THEN 'AGOTADO' WHEN p.stock<=p.stockMinimo THEN 'BAJO' ELSE 'DISPONIBLE' END disponibilidad
FROM producto p JOIN categoria c ON c.idCategoria=p.idCategoria
WHERE EXISTS(SELECT 1 FROM movimiento_inventario m WHERE m.idProducto=p.idProducto
  AND m.claveOperacion LIKE 'SEMILLA-RB-OPERACION-01-PROD-%')
ORDER BY c.nombreCategoria,p.nombreProducto;

-- Debe devolver cero filas: el stock final debe coincidir con los movimientos.
SELECT p.idProducto,p.nombreProducto,p.stock,SUM(m.cantidad*t.signo) stockMovimientos
FROM producto p JOIN movimiento_inventario m ON m.idProducto=p.idProducto
JOIN tipo_movimiento_almacen t ON t.idTipoMovimientoAlmacen=m.idTipoMovimientoAlmacen
WHERE EXISTS(SELECT 1 FROM movimiento_inventario inicial WHERE inicial.idProducto=p.idProducto
  AND inicial.claveOperacion LIKE 'SEMILLA-RB-OPERACION-01-PROD-%')
GROUP BY p.idProducto,p.nombreProducto,p.stock HAVING p.stock<>SUM(m.cantidad*t.signo);

SELECT n.nombres||' '||n.apellidos empleado,u.username,u.estadoUsuario,r.nombreRol,
  c.credencialTemporal,c.fechaVencimiento
FROM usuario u JOIN empleado e ON e.idEmpleado=u.idEmpleado
JOIN persona_natural n ON n.idPerfil=e.idPerfil
JOIN usuario_rol ur ON ur.idUsuario=u.idUsuario JOIN rol r ON r.idRol=ur.idRol
JOIN usuario_credencial c ON c.idUsuario=u.idUsuario
JOIN tipo_credencial tc ON tc.idTipoCredencial=c.idTipoCredencial AND tc.nombreCredencial='PASSWORD'
WHERE u.username LIKE '%@personal.example.test' ORDER BY r.nombreRol,u.username;
