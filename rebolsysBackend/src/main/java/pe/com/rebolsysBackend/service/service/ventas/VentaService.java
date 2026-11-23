package pe.com.rebolsysBackend.service.service.ventas;

import pe.com.rebolsysBackend.dto.comun.OperacionUsuarioDTO;
import pe.com.rebolsysBackend.dto.comun.PaginaResponseDTO;
import pe.com.rebolsysBackend.dto.ventas.VentaConfirmacionDTO;
import pe.com.rebolsysBackend.dto.ventas.VentaEditarDTO;
import pe.com.rebolsysBackend.dto.ventas.VentaRequestDTO;
import pe.com.rebolsysBackend.dto.ventas.VentaResponseDTO;


public interface VentaService {
  VentaResponseDTO registrar(VentaRequestDTO r);

  VentaResponseDTO registrarConfirmada(VentaRequestDTO r);

  VentaResponseDTO editar(Long id, VentaEditarDTO r);

  VentaResponseDTO obtener(Long id);

  PaginaResponseDTO<VentaResponseDTO> listar(int pagina, int tamanio);

  VentaResponseDTO confirmar(Long id, OperacionUsuarioDTO r);

  VentaResponseDTO confirmarCobrada(Long id, VentaConfirmacionDTO r);

  VentaResponseDTO anular(Long id, OperacionUsuarioDTO r);
}
