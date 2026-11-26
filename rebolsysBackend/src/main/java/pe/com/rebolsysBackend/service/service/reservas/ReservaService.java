package pe.com.rebolsysBackend.service.service.reservas;

import pe.com.rebolsysBackend.dto.comun.OperacionUsuarioDTO;
import pe.com.rebolsysBackend.dto.comun.PaginaResponseDTO;
import pe.com.rebolsysBackend.dto.reservas.ReservaDetalleRespuestaDTO;
import pe.com.rebolsysBackend.dto.reservas.ReservaOcupantesGuardarDTO;
import pe.com.rebolsysBackend.dto.reservas.ReservaRegistroRequestDTO;
import pe.com.rebolsysBackend.dto.reservas.ReservaRespuestaDTO;


public interface ReservaService {
  ReservaRespuestaDTO registrar(ReservaRegistroRequestDTO r);

  ReservaRespuestaDTO obtener(Long id);

  default PaginaResponseDTO<ReservaRespuestaDTO> listar(int pagina, int tamanio) {
    return listar(pagina, tamanio, false);
  }

  PaginaResponseDTO<ReservaRespuestaDTO> listar(int pagina, int tamanio, boolean incluirCuenta);

  ReservaDetalleRespuestaDTO cambiarEstado(Long id, String accion, OperacionUsuarioDTO r);

  ReservaDetalleRespuestaDTO guardarOcupantes(Long id, ReservaOcupantesGuardarDTO r);
}
