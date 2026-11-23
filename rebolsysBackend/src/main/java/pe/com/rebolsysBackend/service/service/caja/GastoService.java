package pe.com.rebolsysBackend.service.service.caja;

import java.util.*;
import pe.com.rebolsysBackend.dto.caja.GastoFiltroDTO;
import pe.com.rebolsysBackend.dto.caja.GastoHistorialDTO;
import pe.com.rebolsysBackend.dto.caja.GastoRequestDTO;
import pe.com.rebolsysBackend.dto.caja.GastoResponseDTO;
import pe.com.rebolsysBackend.dto.reservas.CatalogoItemResponseDTO;

public interface GastoService {
  GastoResponseDTO registrar(GastoRequestDTO r);

  GastoResponseDTO obtener(Long id);

  List<GastoResponseDTO> listar(Long idTurno);

  GastoHistorialDTO historial(GastoFiltroDTO filtros);

  List<CatalogoItemResponseDTO> tipos();
}
