package pe.com.rebolsysBackend.service.service.ventas;

import pe.com.rebolsysBackend.dto.caja.cobros.PagoCuentaDTO;
import pe.com.rebolsysBackend.dto.comun.comprobantes.ComprobanteCuentaDTO;
import pe.com.rebolsysBackend.dto.comun.comprobantes.CuentaDocumentoDTO;


public interface CuentaVentaService {
  CuentaDocumentoDTO cuenta(Long idVenta);
  CuentaDocumentoDTO pagar(Long idVenta, PagoCuentaDTO request);
  CuentaDocumentoDTO emitir(Long idVenta, ComprobanteCuentaDTO request);
}
