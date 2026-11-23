package pe.com.rebolsysBackend.dto.caja.cobros;

import java.math.BigDecimal;

public record CuentaLineaDTO(
    Long idDetalleReserva,
    Long idDetalleVenta,
    String descripcion,
    BigDecimal cantidad,
    BigDecimal total) {}
