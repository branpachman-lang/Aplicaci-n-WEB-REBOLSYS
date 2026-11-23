package pe.com.rebolsysBackend.dto.comun.comprobantes;

import java.math.BigDecimal;
import pe.com.rebolsysBackend.dto.configuracion.SistemaDTO;

/** Copia inmutable para generar archivos y correo sin mantener una transacción abierta. */
public record TicketComprobanteDTO(
    CuentaDocumentoDTO documento, SistemaDTO.Marca hospedaje, String correoReceptor,
    BigDecimal subtotal, BigDecimal igv) {}
