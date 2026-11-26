-- Conserva el abono original y registra la devolución por estancia.
BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE detalle_reserva ADD (montoDevuelto NUMBER(12,2)
        CONSTRAINT dr_monto_devuelto_ck CHECK (montoDevuelto > 0))';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -1430 THEN RAISE; END IF;
END;
/

MERGE INTO tipo_movimiento_caja t
USING (SELECT 'DEVOLUCION RESERVA' nombre FROM dual) s
ON (t.nombreMovimiento = s.nombre)
WHEN NOT MATCHED THEN INSERT (nombreMovimiento, signo) VALUES (s.nombre, -1);
COMMIT;
