-- Actualiza un esquema REBOLSYS existente que ya incluye la tabla PAGO_YAPE.
-- Ejecutar como propietario del esquema, con el backend detenido y en una hoja no compartida.
-- Los cambios DDL se confirman individualmente. Se puede repetir tras resolver un error.
DECLARE
  v_cantidad NUMBER;
  v_nullable USER_TAB_COLUMNS.NULLABLE%TYPE;
  v_default VARCHAR2(4000);

  PROCEDURE validar(p_consulta VARCHAR2, p_mensaje VARCHAR2) IS
  BEGIN
    EXECUTE IMMEDIATE p_consulta INTO v_cantidad;
    IF v_cantidad > 0 THEN
      RAISE_APPLICATION_ERROR(-20090, p_mensaje || ': ' || v_cantidad);
    END IF;
  END;

  PROCEDURE agregar_restriccion(p_tabla VARCHAR2, p_nombre VARCHAR2, p_definicion VARCHAR2) IS
  BEGIN
    SELECT COUNT(*) INTO v_cantidad FROM user_constraints
      WHERE table_name = p_tabla AND constraint_name = p_nombre;
    IF v_cantidad = 0 THEN
      EXECUTE IMMEDIATE 'ALTER TABLE ' || p_tabla || ' ADD CONSTRAINT ' || p_nombre || ' ' || p_definicion;
    END IF;
  END;

  PROCEDURE retirar_restriccion(p_tabla VARCHAR2, p_nombre VARCHAR2) IS
  BEGIN
    SELECT COUNT(*) INTO v_cantidad FROM user_constraints
      WHERE table_name = p_tabla AND constraint_name = p_nombre;
    IF v_cantidad = 1 THEN
      EXECUTE IMMEDIATE 'ALTER TABLE ' || p_tabla || ' DROP CONSTRAINT ' || p_nombre;
    END IF;
  END;

  PROCEDURE crear_indice(p_nombre VARCHAR2, p_ddl VARCHAR2) IS
  BEGIN
    SELECT COUNT(*) INTO v_cantidad FROM user_indexes WHERE index_name = p_nombre;
    IF v_cantidad = 0 THEN
      EXECUTE IMMEDIATE p_ddl;
    END IF;
  END;

  PROCEDURE renombrar_yape(p_nombre VARCHAR2, p_tipo CHAR, p_columna VARCHAR2,
                           p_referencia VARCHAR2 DEFAULT NULL, p_check VARCHAR2 DEFAULT NULL) IS
    v_nombre USER_CONSTRAINTS.CONSTRAINT_NAME%TYPE;
  BEGIN
    SELECT COUNT(*) INTO v_cantidad FROM user_constraints
      WHERE table_name = 'PAGO_YAPE' AND constraint_name = p_nombre;
    IF v_cantidad > 0 THEN
      RETURN;
    END IF;

    SELECT c.constraint_name INTO v_nombre
      FROM user_constraints c
      JOIN user_cons_columns cc ON cc.constraint_name = c.constraint_name AND cc.table_name = c.table_name
      LEFT JOIN user_constraints r ON r.constraint_name = c.r_constraint_name AND c.r_owner = USER
      WHERE c.table_name = 'PAGO_YAPE' AND c.constraint_type = p_tipo
        AND cc.column_name = p_columna AND c.status = 'ENABLED' AND c.validated = 'VALIDATED'
        AND (p_referencia IS NULL OR r.table_name = p_referencia)
        AND (p_check IS NULL OR REGEXP_REPLACE(UPPER(c.search_condition_vc), '[[:space:]"]', '') = p_check);

    EXECUTE IMMEDIATE 'ALTER TABLE pago_yape RENAME CONSTRAINT ' || DBMS_ASSERT.ENQUOTE_NAME(v_nombre)
      || ' TO ' || p_nombre;
  END;
BEGIN
  IF USER NOT IN ('REBOLSYS', 'REBOLSYSDBV2') OR SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') <> USER THEN
    RAISE_APPLICATION_ERROR(-20091, 'Conecta como propietario REBOLSYS o REBOLSYSDBV2');
  END IF;
  SELECT COUNT(*) INTO v_cantidad FROM user_tables WHERE table_name = 'PAGO_YAPE';
  IF v_cantidad <> 1 THEN
    RAISE_APPLICATION_ERROR(-20092, 'Se requiere el esquema previo con PAGO_YAPE');
  END IF;

  validar('SELECT COUNT(*) FROM habitacion WHERE precioPorNoche < 0', 'Habitaciones con precio negativo');
  validar(q'[SELECT COUNT(*) FROM movimiento_inventario WHERE condicionItem NOT IN ('OPTIMO', 'DANADO', 'FALTANTE')]',
    'Movimientos con condicion invalida');
  validar(q'[SELECT COUNT(*) FROM detalle_informe WHERE condicionAnterior NOT IN ('OPTIMO', 'DANADO', 'FALTANTE')]',
    'Detalles de informe con condicion anterior invalida');
  validar(q'[SELECT COUNT(*) FROM (
      SELECT idHabitacion FROM informe_revision WHERE estadoInforme IN ('BORRADOR', 'POR_APROBAR')
      GROUP BY idHabitacion HAVING COUNT(*) > 1)]', 'Habitaciones con informes activos duplicados');
  validar('SELECT COUNT(*) FROM (SELECT claveRegistro FROM flujo_caja WHERE claveRegistro IS NOT NULL
      GROUP BY claveRegistro HAVING COUNT(*) > 1)', 'Claves de flujo de caja duplicadas');
  validar('SELECT COUNT(*) FROM movimiento_inventario m WHERE
      (m.idItem IS NOT NULL AND NOT EXISTS (SELECT 1 FROM item i WHERE i.idItem = m.idItem)) OR
      (m.idHabitacion IS NOT NULL AND NOT EXISTS (SELECT 1 FROM habitacion h WHERE h.idHabitacion = m.idHabitacion))',
    'Movimientos con referencias inexistentes');
  validar('SELECT COUNT(*) FROM detalle_informe d WHERE NOT EXISTS (SELECT 1 FROM item i WHERE i.idItem = d.idItem)',
    'Detalles de informe con item inexistente');
  validar(q'[SELECT COUNT(*) FROM tipo_movimiento_caja WHERE nombreMovimiento = 'DEVOLUCION RESERVA' AND signo <> -1]',
    'El tipo de movimiento DEVOLUCION RESERVA tiene un signo incorrecto');

  SELECT COUNT(*) INTO v_cantidad FROM user_tab_columns
    WHERE table_name = 'DETALLE_RESERVA' AND column_name = 'MONTODEVUELTO';
  IF v_cantidad = 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE detalle_reserva ADD (montoDevuelto NUMBER(12,2))';
  END IF;
  agregar_restriccion('DETALLE_RESERVA', 'DR_MONTO_DEVUELTO_CK', 'CHECK (montoDevuelto > 0)');

  SELECT data_default INTO v_default FROM user_tab_columns WHERE table_name = 'PERFIL' AND column_name = 'ESCLIENTE';
  IF NVL(TRIM(v_default), 'NULL') <> '0' THEN
    EXECUTE IMMEDIATE 'ALTER TABLE perfil MODIFY (esCliente DEFAULT 0)';
  END IF;
  SELECT nullable INTO v_nullable FROM user_tab_columns WHERE table_name = 'PAGO' AND column_name = 'CLAVEIDEMPOTENCIA';
  IF v_nullable = 'N' THEN
    EXECUTE IMMEDIATE 'ALTER TABLE pago MODIFY (claveIdempotencia NULL)';
  END IF;
  SELECT nullable, data_default INTO v_nullable, v_default FROM user_tab_columns
    WHERE table_name = 'TICKET_MANTENIMIENTO' AND column_name = 'FECHARESOLUCION';
  IF v_nullable = 'N' THEN
    EXECUTE IMMEDIATE 'ALTER TABLE ticket_mantenimiento MODIFY (fechaResolucion DEFAULT NULL NULL)';
  ELSIF NVL(UPPER(TRIM(v_default)), 'NULL') <> 'NULL' THEN
    EXECUTE IMMEDIATE 'ALTER TABLE ticket_mantenimiento MODIFY (fechaResolucion DEFAULT NULL)';
  END IF;

  agregar_restriccion('HABITACION', 'HABITACION_PRECIO_NOCHE_CHK', 'CHECK (precioPorNoche IS NULL OR precioPorNoche >= 0)');
  agregar_restriccion('ITEM', 'ITEM_CONDICION_OPERATIVA_CHK', q'[CHECK (condicionItem IN ('OPTIMO', 'DANADO', 'FALTANTE'))]');
  agregar_restriccion('FLUJO_CAJA', 'PN_FLUJO_CLAVE_UQ', 'UNIQUE (claveRegistro)');
  agregar_restriccion('MOVIMIENTO_INVENTARIO', 'PN_MI_ITEM_FK', 'FOREIGN KEY (idItem) REFERENCES item(idItem)');
  agregar_restriccion('MOVIMIENTO_INVENTARIO', 'PN_MI_HABITACION_FK', 'FOREIGN KEY (idHabitacion) REFERENCES habitacion(idHabitacion)');
  agregar_restriccion('MOVIMIENTO_INVENTARIO', 'MI_CONDICION_ITEM_CHK', q'[CHECK (condicionItem IN ('OPTIMO', 'DANADO', 'FALTANTE'))]');
  agregar_restriccion('INFORME_REVISION', 'INFORME_ESTADO_V2_CHK',
    q'[CHECK (estadoInforme IS NULL OR estadoInforme IN ('BORRADOR', 'POR_APROBAR', 'APROBADO', 'INVALIDADO'))]');
  agregar_restriccion('DETALLE_INFORME', 'DI_ITEM_FK', 'FOREIGN KEY (idItem) REFERENCES item(idItem)');
  agregar_restriccion('DETALLE_INFORME', 'DI_ESTADO_OPERATIVO_CHK',
    q'[CHECK (estadoEncontrado IS NULL OR estadoEncontrado IN ('OPTIMO', 'DANADO', 'FALTANTE'))]');
  agregar_restriccion('DETALLE_INFORME', 'DI_CONDICION_ANTERIOR_CHK',
    q'[CHECK (condicionAnterior IS NULL OR condicionAnterior IN ('OPTIMO', 'DANADO', 'FALTANTE'))]');

  retirar_restriccion('ITEM', 'ITEM_CONDICION_CHK');
  retirar_restriccion('INFORME_REVISION', 'INFORME_ESTADO_CHK');
  retirar_restriccion('DETALLE_INFORME', 'DI_ESTADO_ENCONTRADO_CHK');
  retirar_restriccion('DETALLE_INFORME', 'DI_ITEM_HABITACION_FK');
  retirar_restriccion('MOVIMIENTO_INVENTARIO', 'PN_MI_ITEM_HABITACION_FK');

  renombrar_yape('PY_PK', 'P', 'CLAVEOPERACION');
  renombrar_yape('PY_ORDEN_UQ', 'U', 'ORDENPROVEEDOR');
  renombrar_yape('PY_PAGO_UQ', 'U', 'IDPAGO');
  renombrar_yape('PY_RESERVA_FK', 'R', 'IDRESERVA', 'RESERVA');
  renombrar_yape('PY_USUARIO_FK', 'R', 'IDUSUARIO', 'USUARIO');
  renombrar_yape('PY_TURNO_FK', 'R', 'IDTURNO', 'TURNO_CAJA');
  renombrar_yape('PY_PAGO_FK', 'R', 'IDPAGO', 'PAGO');
  renombrar_yape('PY_IMPORTE_CK', 'C', 'IMPORTE', p_check => 'IMPORTE>0');
  renombrar_yape('PY_MODO_CK', 'C', 'MODO', p_check => q'[MODOIN('SIMULADO','MERCADO_PAGO')]');
  renombrar_yape('PY_AMBIENTE_CK', 'C', 'AMBIENTE', p_check => q'[AMBIENTEIN('PRUEBA','PRODUCCION')]');
  renombrar_yape('PY_ESTADO_CK', 'C', 'ESTADO', p_check => q'[ESTADOIN('ENVIANDO','PENDIENTE','APLICADO','RECHAZADO','REVISION')]');

  crear_indice('DETALLE_RESERVA_CABECERA_IX', 'CREATE INDEX detalle_reserva_cabecera_ix ON detalle_reserva (idReserva)');
  crear_indice('DETALLE_RESERVA_INTERVALO_IX',
    'CREATE INDEX detalle_reserva_intervalo_ix ON detalle_reserva (idHabitacion, estadoDetalle, fechaIngreso, fechaSalida)');
  crear_indice('INFORME_ACTIVO_HAB_UNQ', q'[CREATE UNIQUE INDEX informe_activo_hab_unq ON informe_revision
    (CASE WHEN estadoInforme IN ('BORRADOR', 'POR_APROBAR') THEN idHabitacion END)]');

  MERGE INTO tipo_movimiento_caja t
  USING (SELECT 'DEVOLUCION RESERVA' nombre FROM dual) s
  ON (t.nombreMovimiento = s.nombre)
  WHEN NOT MATCHED THEN INSERT (nombreMovimiento, signo) VALUES (s.nombre, -1);
END;
/
COMMIT;
