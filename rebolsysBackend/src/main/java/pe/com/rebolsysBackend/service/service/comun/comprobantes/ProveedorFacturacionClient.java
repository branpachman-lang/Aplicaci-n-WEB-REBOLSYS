package pe.com.rebolsysBackend.service.service.comun.comprobantes;

import pe.com.rebolsysBackend.dto.comun.comprobantes.ResultadoProveedorCpeDTO;

public interface ProveedorFacturacionClient {
  ResultadoProveedorCpeDTO enviar(String solicitud, String ruc, String tipo, String serie, long numero);
  ResultadoProveedorCpeDTO consultar(String ruc, String tipo, String serie, long numero);
  byte[] pdf(String solicitud, String ruc);
}
