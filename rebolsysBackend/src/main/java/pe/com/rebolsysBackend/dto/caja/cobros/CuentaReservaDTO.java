package pe.com.rebolsysBackend.dto.caja.cobros;

import java.math.BigDecimal;
import java.util.List;
import pe.com.rebolsysBackend.dto.comun.comprobantes.CuentaDocumentoDTO;

public record CuentaReservaDTO(
    Long idReserva,
    String numeroReserva,
    BigDecimal totalAlojamiento,
    BigDecimal totalCargos,
    BigDecimal totalPagado,
    BigDecimal saldoPendiente,
    boolean admiteConsumosConsolidados,
    List<CuentaDocumentoDTO> cuentas) {}
