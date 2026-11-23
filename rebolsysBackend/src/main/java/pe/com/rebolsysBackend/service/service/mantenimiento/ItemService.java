package pe.com.rebolsysBackend.service.service.mantenimiento;

import java.util.*;
import pe.com.rebolsysBackend.dto.comun.PaginaResponseDTO;
import pe.com.rebolsysBackend.dto.mantenimiento.ItemAsignacionDTO;
import pe.com.rebolsysBackend.dto.mantenimiento.ItemConservacionDTO;
import pe.com.rebolsysBackend.dto.mantenimiento.ItemFiltroDTO;
import pe.com.rebolsysBackend.dto.mantenimiento.ItemHistorialDTO;
import pe.com.rebolsysBackend.dto.mantenimiento.ItemRequestDTO;
import pe.com.rebolsysBackend.dto.mantenimiento.ItemResponseDTO;
import pe.com.rebolsysBackend.dto.mantenimiento.MovimientoRespuestaDTO;
import pe.com.rebolsysBackend.service.generic.CrudService;

public interface ItemService extends CrudService<ItemRequestDTO, ItemResponseDTO, Long> {
  @Override
  List<ItemResponseDTO> readAll();

  List<ItemResponseDTO> listar();

  PaginaResponseDTO<ItemResponseDTO> configuracion(ItemFiltroDTO filtro);

  List<ItemResponseDTO> enHabitacion(Long id);

  ItemResponseDTO conservacion(Long id, ItemConservacionDTO r);

  List<ItemHistorialDTO> historial(Long id);

  ItemResponseDTO obtener(Long id);

  ItemResponseDTO guardar(Long id, ItemRequestDTO r);

  ItemResponseDTO asignar(Long id, ItemAsignacionDTO r);

  List<MovimientoRespuestaDTO> movimientos(Long id);
}
