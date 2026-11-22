-- Ejecutar en el esquema de REBOLSYS después de V16 y V17.
DECLARE
  PROCEDURE validar(p_tabla VARCHAR2, p_columna VARCHAR2) IS
    v_cantidad NUMBER;
  BEGIN
    EXECUTE IMMEDIATE 'SELECT COUNT(*) FROM ' || p_tabla || ' WHERE ' || p_columna ||
      q'[ NOT IN ('OPTIMO','DANADO','FALTANTE')]' INTO v_cantidad;
    IF v_cantidad > 0 THEN
      RAISE_APPLICATION_ERROR(-20080, p_tabla || '.' || p_columna ||
        ' contiene ' || v_cantidad || ' valores fuera del dominio; revisar antes de migrar');
    END IF;
  END;

  PROCEDURE restringir(p_tabla VARCHAR2, p_columna VARCHAR2, p_restriccion VARCHAR2) IS
    v_existe NUMBER;
  BEGIN
    SELECT COUNT(*) INTO v_existe FROM user_constraints
    WHERE table_name = p_tabla AND constraint_name = p_restriccion;
    IF v_existe = 0 THEN
      EXECUTE IMMEDIATE 'ALTER TABLE ' || p_tabla || ' ADD CONSTRAINT ' || p_restriccion ||
        ' CHECK (' || p_columna || q'[ IN ('OPTIMO','DANADO','FALTANTE')) ENABLE VALIDATE]';
    END IF;
  END;
BEGIN
  validar('ITEM', 'CONDICIONITEM');
  validar('DETALLE_INFORME', 'ESTADOENCONTRADO');
  validar('DETALLE_INFORME', 'CONDICIONANTERIOR');
  validar('MOVIMIENTO_INVENTARIO', 'CONDICIONITEM');

  restringir('ITEM', 'CONDICIONITEM', 'ITEM_CONDICION_OPERATIVA_CHK');
  restringir('DETALLE_INFORME', 'ESTADOENCONTRADO', 'DI_ESTADO_OPERATIVO_CHK');
  restringir('DETALLE_INFORME', 'CONDICIONANTERIOR', 'DI_CONDICION_ANTERIOR_CHK');
  restringir('MOVIMIENTO_INVENTARIO', 'CONDICIONITEM', 'MI_CONDICION_ITEM_CHK');
END;
/
