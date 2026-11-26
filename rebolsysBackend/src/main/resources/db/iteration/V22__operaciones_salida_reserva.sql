-- Ejecutar antes de iniciar el backend actualizado. Conserva los registros existentes.
BEGIN
    EXECUTE IMMEDIATE '
        CREATE TABLE reserva_salida_operacion (
            claveOperacion VARCHAR2(64) NOT NULL,
            idDetalleReserva NUMBER NOT NULL,
            idUsuario NUMBER NOT NULL,
            firmaSolicitud VARCHAR2(64) NOT NULL,
            idPago NUMBER,
            idComprobante NUMBER,
            CONSTRAINT rso_pk PRIMARY KEY (claveOperacion),
            CONSTRAINT rso_detalle_uq UNIQUE (idDetalleReserva),
            CONSTRAINT rso_detalle_fk FOREIGN KEY (idDetalleReserva) REFERENCES detalle_reserva(idDetalleReserva),
            CONSTRAINT rso_usuario_fk FOREIGN KEY (idUsuario) REFERENCES usuario(idUsuario),
            CONSTRAINT rso_pago_fk FOREIGN KEY (idPago) REFERENCES pago(idPago),
            CONSTRAINT rso_comprobante_fk FOREIGN KEY (idComprobante) REFERENCES comprobante(idComprobante)
        )';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -955 THEN RAISE; END IF;
END;
/
