package pe.com.rebolsysBackend.service.service.caja.cobros;

import pe.com.rebolsysBackend.dto.caja.cobros.PagoCuentaDTO;
import pe.com.rebolsysBackend.dto.comun.comprobantes.ComprobanteCuentaDTO;
import pe.com.rebolsysBackend.dto.comun.comprobantes.CuentaDocumentoDTO;
import pe.com.rebolsysBackend.entity.Comprobante;

public interface CuentaCobroService {
  CuentaDocumentoDTO documento(Comprobante cuenta);
  Comprobante pagar(Comprobante cuenta, PagoCuentaDTO request);
  Comprobante emitir(Comprobante cuenta, ComprobanteCuentaDTO request);
}
