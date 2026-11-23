package pe.com.rebolsysBackend.service.service.configuracion;

import pe.com.rebolsysBackend.dto.configuracion.TipoHabitacionRequestDTO;
import pe.com.rebolsysBackend.dto.configuracion.TipoHabitacionResponseDTO;
import pe.com.rebolsysBackend.dto.configuracion.SistemaDTO.ParametrosComerciales;
import pe.com.rebolsysBackend.service.generic.CrudService;

public interface TipoHabitacionService extends CrudService<TipoHabitacionRequestDTO, TipoHabitacionResponseDTO, Long> {
  TipoHabitacionResponseDTO actualizarParametros(Long id, ParametrosComerciales datos);
}
