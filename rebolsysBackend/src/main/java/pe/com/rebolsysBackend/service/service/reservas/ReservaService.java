package pe.com.rebolsysBackend.service.service.reservas;

import pe.com.rebolsysBackend.dto.comun.OperacionUsuarioDTO;
import pe.com.rebolsysBackend.dto.comun.PaginaResponseDTO;
import pe.com.rebolsysBackend.dto.reservas.ReservaDetalleRespuestaDTO;
import pe.com.rebolsysBackend.dto.reservas.ReservaOcupantesGuardarDTO;
import pe.com.rebolsysBackend.dto.reservas.ReservaRegistroRequestDTO;
import pe.com.rebolsysBackend.dto.reservas.ReservaRespuestaDTO;
import pe.com.rebolsysBackend.dto.reservas.ReservaSalidaDTO;


public interface ReservaService {
  ReservaRespuestaDTO registrar(ReservaRegistroRequestDTO r);

  ReservaRespuestaDTO obtener(Long id);

  PaginaResponseDTO<ReservaRespuestaDTO> listar(int pagina, int tamanio);

  ReservaDetalleRespuestaDTO cambiarEstado(Long id, String accion, OperacionUsuarioDTO r);

  ReservaDetalleRespuestaDTO registrarSalida(Long id, ReservaSalidaDTO r);

  ReservaDetalleRespuestaDTO guardarOcupantes(Long id, ReservaOcupantesGuardarDTO r);
}
