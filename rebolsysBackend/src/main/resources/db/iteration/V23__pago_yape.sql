-- Tabla de seguimiento de intentos y conciliación para cobros con Yape (Mercado Pago Orders / Simulador).
-- Ejecutar una vez en el esquema Oracle de la aplicación (requiere DDL base y tabla RESERVA/USUARIO/TURNO_CAJA/PAGO).
DECLARE
  v_tabla NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_tabla FROM user_tables WHERE table_name = 'PAGO_YAPE';
  IF v_tabla = 0 THEN
    EXECUTE IMMEDIATE 'CREATE TABLE pago_yape (
      claveOperacion VARCHAR2(36) PRIMARY KEY,
      idReserva NUMBER NOT NULL REFERENCES reserva(idReserva),
      idUsuario NUMBER NOT NULL REFERENCES usuario(idUsuario),
      idTurno NUMBER NOT NULL REFERENCES turno_caja(idTurno),
      importe NUMBER(12,2) NOT NULL CHECK (importe > 0),
      telefonoEnmascarado VARCHAR2(15) NOT NULL,
      modo VARCHAR2(15) NOT NULL CHECK (modo IN (''SIMULADO'',''MERCADO_PAGO'')),
      ambiente VARCHAR2(12) NOT NULL CHECK (ambiente IN (''PRUEBA'',''PRODUCCION'')),
      estado VARCHAR2(12) NOT NULL CHECK (estado IN (''ENVIANDO'',''PENDIENTE'',''APLICADO'',''RECHAZADO'',''REVISION'')),
      ordenProveedor VARCHAR2(64) UNIQUE,
      idPago NUMBER UNIQUE REFERENCES pago(idPago),
      mensajeProveedor VARCHAR2(250),
      creado TIMESTAMP NOT NULL
    )';
  END IF;

  MERGE INTO forma_pago f
  USING (SELECT 'YAPE' nombre FROM dual) s
  ON (UPPER(TRIM(f.nombrePago)) = s.nombre)
  WHEN NOT MATCHED THEN INSERT (nombrePago) VALUES (s.nombre);

  COMMIT;
END;
/
