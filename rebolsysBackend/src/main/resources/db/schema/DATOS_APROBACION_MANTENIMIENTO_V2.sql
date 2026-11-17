-- Configura los permisos de atención y aprobación de Mantenimiento en REBOLSYSDBV2.
-- Confirmar con COMMIT.
DECLARE
  v_rol NUMBER;
  v_gestion SYS.ODCIVARCHAR2LIST := SYS.ODCIVARCHAR2LIST(
    'APROBAR_INFORME', 'CREAR_TICKET', 'EDITAR_TICKET', 'ELIMINAR_TICKET'
  );
  v_consulta SYS.ODCIVARCHAR2LIST := SYS.ODCIVARCHAR2LIST(
    'VER_INFORMES', 'VER_TICKETS', 'VER_EVIDENCIA', 'CARGAR_EVIDENCIA'
  );
BEGIN
  IF SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') <> 'REBOLSYSDBV2' THEN
    RAISE_APPLICATION_ERROR(-20075, 'Ejecuta el ajuste en REBOLSYSDBV2');
  END IF;

  SELECT idRol INTO v_rol FROM rol WHERE nombreRol = 'GERENTE' FOR UPDATE;

  INSERT INTO rol (nombreRol)
  SELECT 'MANTENIMIENTO' FROM dual
  WHERE NOT EXISTS (SELECT 1 FROM rol WHERE nombreRol = 'MANTENIMIENTO');
  SELECT idRol INTO v_rol FROM rol WHERE nombreRol = 'MANTENIMIENTO';

  INSERT INTO modulo (nombreModulo)
  SELECT 'LIMPIEZA' FROM dual
  WHERE NOT EXISTS (SELECT 1 FROM modulo WHERE nombreModulo = 'LIMPIEZA');

  INSERT INTO permiso (idModulo, accion)
  SELECT m.idModulo, a.accion FROM modulo m CROSS JOIN (
    SELECT column_value accion FROM TABLE(v_gestion)
    UNION ALL SELECT column_value FROM TABLE(v_consulta)
  ) a
  WHERE m.nombreModulo = 'LIMPIEZA'
    AND NOT EXISTS (SELECT 1 FROM permiso p WHERE p.idModulo = m.idModulo AND p.accion = a.accion);

  INSERT INTO rol_permiso (idRol, idPermiso)
  SELECT v_rol, p.idPermiso FROM permiso p JOIN modulo m ON m.idModulo = p.idModulo
  WHERE m.nombreModulo = 'LIMPIEZA'
    AND p.accion IN (SELECT column_value FROM TABLE(v_gestion) UNION ALL SELECT column_value FROM TABLE(v_consulta))
    AND NOT EXISTS (SELECT 1 FROM rol_permiso rp WHERE rp.idRol = v_rol AND rp.idPermiso = p.idPermiso);

  DELETE FROM rol_permiso rp
  WHERE rp.idRol = (SELECT idRol FROM rol WHERE nombreRol = 'LIMPIEZA')
    AND rp.idPermiso IN (
      SELECT p.idPermiso FROM permiso p JOIN modulo m ON m.idModulo = p.idModulo
      WHERE m.nombreModulo = 'LIMPIEZA' AND p.accion IN (SELECT column_value FROM TABLE(v_gestion))
    );
END;
/
