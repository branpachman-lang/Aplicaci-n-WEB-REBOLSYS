-- Ejecutar con la aplicación detenida, después de V16, en REBOLSYSDBV2.
-- El CHECK del DDL V2 ya admite POR_LIMPIAR y EN_LIMPIEZA.
-- Primero se validan las inconsistencias: no se corrigen habitaciones ocupadas
-- o en mantenimiento automáticamente. Revisarlas antes de repetir el script.
DECLARE
  v_cantidad NUMBER;
BEGIN
  IF UPPER(SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA')) <> 'REBOLSYSDBV2' THEN
    RAISE_APPLICATION_ERROR(-20076, 'Ejecutar V17 en REBOLSYSDBV2 después de V16');
  END IF;

  SELECT COUNT(*) INTO v_cantidad FROM informe_revision WHERE estadoInforme IS NULL;
  IF v_cantidad > 0 THEN
    RAISE_APPLICATION_ERROR(-20077, 'Hay informes sin estado; revisar antes de migrar');
  END IF;
  SELECT COUNT(*) INTO v_cantidad FROM (
    SELECT idHabitacion FROM informe_revision
    WHERE estadoInforme IN ('BORRADOR', 'POR_APROBAR')
    GROUP BY idHabitacion HAVING COUNT(*) > 1
  );
  IF v_cantidad > 0 THEN
    RAISE_APPLICATION_ERROR(-20078, 'Hay habitaciones con más de un informe activo');
  END IF;
  SELECT COUNT(*) INTO v_cantidad FROM habitacion h
  WHERE h.estadoHabitacion NOT IN ('POR_LIMPIAR', 'EN_LIMPIEZA')
    AND EXISTS (SELECT 1 FROM informe_revision i WHERE i.idHabitacion = h.idHabitacion
      AND i.estadoInforme IN ('BORRADOR', 'POR_APROBAR'));
  IF v_cantidad > 0 THEN
    RAISE_APPLICATION_ERROR(-20079, 'Hay informes activos en habitaciones fuera de limpieza');
  END IF;

  SELECT COUNT(*) INTO v_cantidad FROM user_indexes WHERE index_name = 'INFORME_ACTIVO_HAB_UNQ';
  IF v_cantidad = 0 THEN
    EXECUTE IMMEDIATE q'[CREATE UNIQUE INDEX informe_activo_hab_unq ON informe_revision
      (CASE WHEN estadoInforme IN ('BORRADOR', 'POR_APROBAR') THEN idHabitacion END)]';
  END IF;

  UPDATE habitacion h SET estadoHabitacion = 'EN_LIMPIEZA'
  WHERE h.estadoHabitacion = 'POR_LIMPIAR'
    AND EXISTS (SELECT 1 FROM informe_revision i WHERE i.idHabitacion = h.idHabitacion
      AND i.estadoInforme IN ('BORRADOR', 'POR_APROBAR'));

  UPDATE habitacion h SET estadoHabitacion = 'POR_LIMPIAR'
  WHERE h.estadoHabitacion = 'EN_LIMPIEZA'
    AND NOT EXISTS (SELECT 1 FROM informe_revision i WHERE i.idHabitacion = h.idHabitacion
      AND i.estadoInforme IN ('BORRADOR', 'POR_APROBAR'));
END;
/
-- Revisar el resultado y confirmar las actualizaciones con COMMIT.
-- CREATE INDEX es DDL y Oracle lo confirma implícitamente.
SELECT h.idHabitacion, h.numeroCuarto, h.estadoHabitacion, i.idInformeRevision, i.estadoInforme
FROM habitacion h LEFT JOIN informe_revision i ON i.idHabitacion = h.idHabitacion
  AND i.estadoInforme IN ('BORRADOR', 'POR_APROBAR')
WHERE h.estadoHabitacion IN ('POR_LIMPIAR', 'EN_LIMPIEZA')
ORDER BY h.numeroCuarto;
