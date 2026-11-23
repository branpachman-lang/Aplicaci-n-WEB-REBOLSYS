package pe.com.rebolsysBackend.service.service.mantenimiento;

import java.time.*;
import java.util.*;
import pe.com.rebolsysBackend.dto.comun.PaginaResponseDTO;
import pe.com.rebolsysBackend.dto.mantenimiento.HabitacionEstadoGuardarDTO;
import pe.com.rebolsysBackend.dto.mantenimiento.HabitacionEstadoResponseDTO;
import pe.com.rebolsysBackend.dto.mantenimiento.HabitacionFiltroDTO;
import pe.com.rebolsysBackend.dto.mantenimiento.HabitacionRequestDTO;
import pe.com.rebolsysBackend.dto.mantenimiento.HabitacionResponseDTO;
import pe.com.rebolsysBackend.dto.mantenimiento.ItemResponseDTO;
import pe.com.rebolsysBackend.service.generic.CrudService;

public interface HabitacionService extends CrudService<HabitacionRequestDTO, HabitacionResponseDTO, Long> {
  @Override
  List<HabitacionResponseDTO> readAll();

  List<HabitacionEstadoResponseDTO> estado(LocalDate desde, LocalDate hasta);

  List<HabitacionResponseDTO> listar();

  PaginaResponseDTO<HabitacionResponseDTO> configuracion(HabitacionFiltroDTO filtro);

  HabitacionResponseDTO cambiarEstado(Long id, HabitacionEstadoGuardarDTO r);

  HabitacionResponseDTO obtener(Long id);

  HabitacionResponseDTO guardar(Long id, HabitacionRequestDTO r);

  List<ItemResponseDTO> mobiliario(Long id);
}
