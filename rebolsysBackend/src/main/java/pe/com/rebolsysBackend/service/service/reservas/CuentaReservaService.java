package pe.com.rebolsysBackend.service.service.reservas;

import java.util.List;
import pe.com.rebolsysBackend.dto.caja.cobros.CuentaReservaDTO;
import pe.com.rebolsysBackend.dto.comun.comprobantes.CuentaDocumentoDTO;
import pe.com.rebolsysBackend.dto.reservas.ConsumoReservaRespuestaDTO;
import pe.com.rebolsysBackend.dto.reservas.ReservaComprobanteRegistroDTO;
import pe.com.rebolsysBackend.dto.reservas.ReservaPagoRegistroDTO;
import pe.com.rebolsysBackend.dto.ventas.VentaRequestDTO;

public interface CuentaReservaService {
  CuentaReservaDTO cuenta(Long idReserva);

  CuentaReservaDTO pagar(Long idReserva, ReservaPagoRegistroDTO request);

  CuentaReservaDTO eliminarPago(Long idReserva, Long idPago);

  CuentaDocumentoDTO emitir(Long idReserva, ReservaComprobanteRegistroDTO request);

  ConsumoReservaRespuestaDTO consumir(Long idReserva, VentaRequestDTO request);

  List<ConsumoReservaRespuestaDTO> consumos(Long idReserva);
}
