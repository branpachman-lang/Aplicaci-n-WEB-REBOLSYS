-- Completa el catálogo de roles oficiales en REBOLSYSDBV2. Confirma con COMMIT.
BEGIN
  IF SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') <> 'REBOLSYSDBV2' THEN
    RAISE_APPLICATION_ERROR(-20076, 'Ejecuta esta carga en REBOLSYSDBV2');
  END IF;

  MERGE INTO rol destino USING (
    SELECT 'GERENTE' nombre FROM dual
    UNION ALL SELECT 'RECEPCIONISTA' FROM dual
    UNION ALL SELECT 'LIMPIEZA' FROM dual
    UNION ALL SELECT 'MANTENIMIENTO' FROM dual
  ) origen ON (destino.nombreRol = origen.nombre)
  WHEN NOT MATCHED THEN INSERT (nombreRol) VALUES (origen.nombre);
END;
/
