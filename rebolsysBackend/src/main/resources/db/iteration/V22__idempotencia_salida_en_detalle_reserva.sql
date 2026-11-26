-- Guarda la clave de reintento directamente en la estancia, sin una tabla auxiliar.
BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE detalle_reserva ADD (claveSalida VARCHAR2(64))';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -1430 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE detalle_reserva ADD (firmaSalida VARCHAR2(64))';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -1430 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE detalle_reserva ADD (idUsuarioSalida NUMBER)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -1430 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE detalle_reserva ADD (idPagoSalida NUMBER)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -1430 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE detalle_reserva ADD (idComprobanteSalida NUMBER)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -1430 THEN RAISE; END IF;
END;
/

DECLARE
    PROCEDURE agregar_restriccion(p_nombre VARCHAR2, p_definicion VARCHAR2) IS
        v_existe NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_existe FROM user_constraints
        WHERE table_name = 'DETALLE_RESERVA' AND constraint_name = UPPER(p_nombre);

        IF v_existe = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE detalle_reserva ADD CONSTRAINT '
                || p_nombre || ' ' || p_definicion;
        END IF;
    END;
BEGIN
    agregar_restriccion('dr_salida_clave_uq', 'UNIQUE (claveSalida)');
    agregar_restriccion('dr_salida_usuario_fk', 'FOREIGN KEY (idUsuarioSalida) REFERENCES usuario(idUsuario)');
    agregar_restriccion('dr_salida_pago_fk', 'FOREIGN KEY (idPagoSalida) REFERENCES pago(idPago)');
    agregar_restriccion('dr_salida_comprobante_fk', 'FOREIGN KEY (idComprobanteSalida) REFERENCES comprobante(idComprobante)');
    agregar_restriccion('dr_salida_datos_ck', 'CHECK (
        (claveSalida IS NULL AND firmaSalida IS NULL AND idUsuarioSalida IS NULL
            AND idPagoSalida IS NULL AND idComprobanteSalida IS NULL)
        OR (claveSalida IS NOT NULL AND firmaSalida IS NOT NULL AND idUsuarioSalida IS NOT NULL)
    )');
END;
/

-- Si se ejecutó la V22 anterior, conserva la respuesta de cada salida ya registrada.
DECLARE
    v_tabla_anterior NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_tabla_anterior
    FROM user_tables WHERE table_name = 'RESERVA_SALIDA_OPERACION';

    IF v_tabla_anterior > 0 THEN
        EXECUTE IMMEDIATE '
            MERGE INTO detalle_reserva d
            USING reserva_salida_operacion s
            ON (d.idDetalleReserva = s.idDetalleReserva)
            WHEN MATCHED THEN UPDATE SET
                d.claveSalida = s.claveOperacion,
                d.firmaSalida = s.firmaSolicitud,
                d.idUsuarioSalida = s.idUsuario,
                d.idPagoSalida = s.idPago,
                d.idComprobanteSalida = s.idComprobante
            WHERE d.claveSalida IS NULL';
        COMMIT;
    END IF;
END;
/
