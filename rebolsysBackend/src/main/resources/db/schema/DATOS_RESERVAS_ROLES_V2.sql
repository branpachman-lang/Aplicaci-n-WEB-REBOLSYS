-- Diez reservas ficticias para probar recepción y limpieza en REBOLSYSDBV2.
-- Ejecutar con las habitaciones y los catálogos iniciales cargados. Confirmar con COMMIT.
DECLARE
  v_ahora TIMESTAMP := CAST(SYSTIMESTAMP AT TIME ZONE 'America/Lima' AS TIMESTAMP);
  v_usuario NUMBER;
  v_turno NUMBER;
  v_tipo_cuenta NUMBER;
  v_tipo_cobro NUMBER;
  v_existentes NUMBER;
  v_habitacion NUMBER;
  v_tarifa NUMBER;
  v_estado VARCHAR2(30);
  v_perfil NUMBER;
  v_tipo_documento NUMBER;
  v_huesped NUMBER;
  v_reserva NUMBER;
  v_detalle NUMBER;
  v_cuenta NUMBER;
  v_forma_pago NUMBER;
  v_pago NUMBER;
  v_ingreso TIMESTAMP;
  v_salida TIMESTAMP;
  v_ingreso_real TIMESTAMP;
  v_salida_real TIMESTAMP;
  v_fecha_pago TIMESTAMP;
  v_total NUMBER(12,2);
  v_abono NUMBER(12,2);
  v_clave VARCHAR2(64);
BEGIN
  IF SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') <> 'REBOLSYSDBV2' THEN
    RAISE_APPLICATION_ERROR(-20071, 'Ejecuta la carga en REBOLSYSDBV2');
  END IF;

  SELECT idUsuario INTO v_usuario FROM usuario
  WHERE username = 'gerencia_local' AND estadoUsuario = 'ACTIVO' FOR UPDATE;

  SELECT COUNT(*) INTO v_existentes FROM reserva WHERE claveRegistro LIKE 'PRUEBA-ROLES-V2-%';
  IF v_existentes = 10 THEN RETURN; END IF;
  IF v_existentes <> 0 THEN
    RAISE_APPLICATION_ERROR(-20072, 'Ya existe una carga parcial de reservas de prueba');
  END IF;

  SELECT idTipoComprobante INTO v_tipo_cuenta FROM tipo_comprobante WHERE codigo = 'CUENTA';
  SELECT idTipoMovimiento INTO v_tipo_cobro FROM tipo_movimiento_caja WHERE nombreMovimiento = 'COBRO_RESERVA';

  INSERT INTO turno_caja (idUsuario, fechaApertura, fechaCierre, montoBase, estadoSesion)
  VALUES (v_usuario, v_ahora - INTERVAL '8' HOUR, v_ahora - INTERVAL '10' MINUTE, 0, 0)
  RETURNING idTurno INTO v_turno;

  FOR caso IN (
    WITH escenarios (cuarto, estado, noches, minutos, documento, numero, nombres, apellidos, nacimiento,
                     acompanante, apellidos_acompanante, medio, fraccion) AS (
      SELECT '201', 'FINALIZADA', 2, 240, 'DNI', '98053001', 'Lucía Elena', 'Paredes Salazar', DATE '1992-02-17',
             'Diego Martín', 'Vega Campos', 'YAPE', 1 FROM dual
      UNION ALL SELECT '202', 'FINALIZADA', 1, 180, 'DNI', '98053002', 'Marco Antonio', 'Rojas Cárdenas', DATE '1988-08-21',
             'Valeria Sofía', 'Torres León', 'TARJETA', 1 FROM dual
      UNION ALL SELECT '203', 'FINALIZADA', 2, 120, 'PASAPORTE', 'ZX7264103', 'Camila Andrea', 'Muñoz Herrera', DATE '1997-04-09',
             NULL, NULL, 'PLIN', 1 FROM dual
      UNION ALL SELECT '301', 'FINALIZADA', 3, 60, 'DNI', '98053004', 'Jorge Luis', 'Benavides Rivas', DATE '1983-11-25',
             NULL, NULL, 'EFECTIVO', 1 FROM dual
      UNION ALL SELECT '302', 'FINALIZADA', 1, 30, 'CARNET EXTRANJERIA', '009805305', 'Daniela Isabel', 'Mendoza Silva', DATE '1995-06-12',
             NULL, NULL, 'YAPE', 1 FROM dual
      UNION ALL SELECT '303', 'CONFIRMADA', 2, 60, 'DNI', '98053006', 'Andrés Felipe', 'Quispe Navarro', DATE '1990-03-04',
             'Rosa Milagros', 'Flores Peña', NULL, 0 FROM dual
      UNION ALL SELECT '304', 'CONFIRMADA', 1, 45, 'PASAPORTE', 'YX6149058', 'Natalia Belén', 'Araya Morales', DATE '1994-12-19',
             NULL, NULL, NULL, 0 FROM dual
      UNION ALL SELECT '401', 'CONFIRMADA', 2, 30, 'DNI', '98053008', 'Hugo Sebastián', 'Medina Castro', DATE '1986-09-02',
             NULL, NULL, NULL, 0 FROM dual
      UNION ALL SELECT '402', 'EN_CURSO', 1, 360, 'DNI', '98053009', 'Paola Fernanda', 'Gutiérrez Ríos', DATE '1993-05-16',
             NULL, NULL, 'TARJETA', 1 FROM dual
      UNION ALL SELECT '403', 'EN_CURSO', 2, 300, 'CARNET EXTRANJERIA', '009805310', 'Mateo Gabriel', 'Ramírez Duarte', DATE '1991-01-28',
             'Elena Victoria', 'Soto Méndez', 'PLIN', 0.5 FROM dual
    )
    SELECT * FROM escenarios ORDER BY cuarto
  ) LOOP
    SELECT h.idHabitacion, COALESCE(h.precioPorNoche, t.precioBase), h.estadoHabitacion
    INTO v_habitacion, v_tarifa, v_estado
    FROM habitacion h JOIN tipo_habitacion t ON t.idTipoHabitacion = h.idTipo
    WHERE h.numeroCuarto = caso.cuarto FOR UPDATE OF h.estadoHabitacion;

    IF v_estado <> 'DISPONIBLE' OR v_tarifa <= 0 THEN
      RAISE_APPLICATION_ERROR(-20073, 'La habitación ' || caso.cuarto || ' debe estar disponible y tener tarifa');
    END IF;

    v_ingreso_real := NULL;
    v_salida_real := NULL;
    IF caso.estado = 'FINALIZADA' THEN
      v_salida_real := v_ahora - NUMTODSINTERVAL(caso.minutos, 'MINUTE');
      v_salida := v_salida_real + INTERVAL '30' MINUTE;
      v_ingreso := v_salida - NUMTODSINTERVAL(caso.noches, 'DAY') + INTERVAL '2' HOUR;
      v_ingreso_real := v_ingreso + INTERVAL '15' MINUTE;
      v_fecha_pago := v_salida_real - INTERVAL '15' MINUTE;
    ELSE
      v_ingreso := v_ahora - NUMTODSINTERVAL(caso.minutos, 'MINUTE');
      v_salida := v_ingreso + NUMTODSINTERVAL(caso.noches, 'DAY') - INTERVAL '2' HOUR;
      IF caso.estado = 'EN_CURSO' THEN
        v_ingreso_real := v_ingreso + INTERVAL '15' MINUTE;
        v_fecha_pago := v_ahora - INTERVAL '20' MINUTE;
      END IF;
    END IF;

    SELECT COUNT(*) INTO v_existentes FROM detalle_reserva
    WHERE idHabitacion = v_habitacion AND (
      estadoDetalle = 'EN_CURSO'
      OR (estadoDetalle <> 'CANCELADA' AND fechaIngreso < v_salida AND fechaSalida > v_ingreso)
    );
    IF v_existentes > 0 THEN
      RAISE_APPLICATION_ERROR(-20074, 'Ya hay una estancia que coincide en la habitación ' || caso.cuarto);
    END IF;

    v_total := caso.noches * v_tarifa;
    v_clave := 'PRUEBA-ROLES-V2-' || caso.cuarto;

    FOR persona IN (
      SELECT 1 titular, caso.documento tipo, caso.numero numero, caso.nombres nombres,
             caso.apellidos apellidos, caso.nacimiento nacimiento,
             'huesped.' || caso.cuarto || '@example.test' correo,
             '90080' || caso.cuarto || '1' telefono
      FROM dual
      UNION ALL
      SELECT 0, 'DNI', '98054' || caso.cuarto, caso.acompanante, caso.apellidos_acompanante, DATE '1998-07-23',
             'acompanante.' || caso.cuarto || '@example.test', '90080' || caso.cuarto || '2'
      FROM dual WHERE caso.acompanante IS NOT NULL
      ORDER BY titular DESC
    ) LOOP
      SELECT idTipoDocumento INTO v_tipo_documento FROM tipo_documento WHERE nombreDocumento = persona.tipo;
      INSERT INTO perfil (idTipoDocumento, numeroDocumento, correo, telefono, esCliente)
      VALUES (v_tipo_documento, persona.numero, persona.correo, persona.telefono, 1)
      RETURNING idPerfil INTO v_perfil;
      INSERT INTO persona_natural (idPerfil, nombres, apellidos, fechaNacimiento)
      VALUES (v_perfil, persona.nombres, persona.apellidos, persona.nacimiento);
      INSERT INTO huesped (idPerfil) VALUES (v_perfil) RETURNING idHuesped INTO v_huesped;

      IF persona.titular = 1 THEN
        INSERT INTO reserva (idPerfil, idUsuario, fechaRegistro, claveRegistro, firmaSolicitud)
        SELECT v_perfil, v_usuario, v_ingreso - INTERVAL '2' DAY, v_clave,
               LOWER(RAWTOHEX(STANDARD_HASH(v_clave, 'SHA256'))) FROM dual;
        SELECT idReserva INTO v_reserva FROM reserva WHERE claveRegistro = v_clave;

        INSERT INTO detalle_reserva (idReserva, idHabitacion, fechaIngreso, fechaSalida, fechaIngresoReal,
                                     fechaSalidaReal, costoEstadia, estadoDetalle)
        VALUES (v_reserva, v_habitacion, v_ingreso, v_salida, v_ingreso_real, v_salida_real, v_total, caso.estado)
        RETURNING idDetalleReserva INTO v_detalle;
      END IF;

      INSERT INTO reserva_huesped (idDetalleReserva, idHuesped, ocupaPlazaCama)
      VALUES (v_detalle, v_huesped, 1);
    END LOOP;

    IF caso.estado = 'FINALIZADA' THEN
      UPDATE habitacion SET estadoHabitacion = 'POR_LIMPIAR' WHERE idHabitacion = v_habitacion;
    ELSIF caso.estado = 'EN_CURSO' THEN
      UPDATE habitacion SET estadoHabitacion = 'OCUPADO' WHERE idHabitacion = v_habitacion;
    END IF;

    IF caso.fraccion > 0 THEN
      SELECT idFormaPago INTO v_forma_pago FROM forma_pago WHERE nombrePago = caso.medio;
      v_abono := ROUND(v_total * caso.fraccion, 2);
      INSERT INTO comprobante (idReserva, idPerfilReceptor, idTipoComprobante, idTurno, serie,
                               subtotal, igv, totalPagar, estadoComprobante, claveIdempotencia)
      SELECT v_reserva, r.idPerfil, v_tipo_cuenta, v_turno, 'CTA', v_total, 0, v_total, 'BORRADOR',
             'CUENTA-R-' || v_reserva FROM reserva r WHERE r.idReserva = v_reserva;
      SELECT idComprobante INTO v_cuenta FROM comprobante WHERE claveIdempotencia = 'CUENTA-R-' || v_reserva;
      INSERT INTO comprobante_detalle (idComprobante, idDetalleReserva, numeroLinea, descripcion, cantidad, valorUnitario, igv)
      VALUES (v_cuenta, v_detalle, 1, 'Alojamiento habitación ' || caso.cuarto, 1, v_total, 0);

      INSERT INTO pago (idComprobante, idFormaPago, idUsuario, idTurno, monto, numeroOperacion, fechaPago, verificado,
                        claveIdempotencia)
      VALUES (v_cuenta, v_forma_pago, v_usuario, v_turno, v_abono,
              CASE WHEN caso.medio <> 'EFECTIVO' THEN '950018' || caso.cuarto END,
              v_fecha_pago, 1, v_clave || '-PAGO')
      RETURNING idPago INTO v_pago;

      INSERT INTO flujo_caja (idTurno, idTipoMovimiento, idPago, concepto, monto, fechaHora, claveRegistro)
      VALUES (v_turno, v_tipo_cobro, v_pago, 'Cobro de prueba de reserva R' || v_reserva, v_abono, v_fecha_pago,
              v_clave || '-COBRO');
    END IF;
  END LOOP;

  INSERT INTO arqueo_caja (idTurno, idFormaPago, montoCalculado, montoDeclarado)
  SELECT v_turno, f.idFormaPago, COALESCE(SUM(p.monto), 0), COALESCE(SUM(p.monto), 0)
  FROM forma_pago f LEFT JOIN pago p ON p.idFormaPago = f.idFormaPago AND p.idTurno = v_turno AND p.verificado = 1
  GROUP BY f.idFormaPago;
END;
/
