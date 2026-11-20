package pe.com.rebolsysBackend.dto;

import java.math.BigDecimal;

/** Copia inmutable para generar archivos y correo sin mantener una transacción abierta. */
public record TicketComprobanteDTO(
    CuentaDocumentoDTO documento, SistemaDTO.Marca hospedaje, String correoReceptor,
    BigDecimal subtotal, BigDecimal igv) {}
