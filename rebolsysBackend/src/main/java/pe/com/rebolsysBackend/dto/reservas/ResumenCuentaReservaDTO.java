package pe.com.rebolsysBackend.dto.reservas;

import java.math.BigDecimal;

public record ResumenCuentaReservaDTO(
    BigDecimal totalCargos,
    BigDecimal totalPagado,
    BigDecimal saldoPendiente,
    BigDecimal saldoAlojamiento) {}
