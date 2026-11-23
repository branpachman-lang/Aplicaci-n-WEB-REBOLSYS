package pe.com.rebolsysBackend.service.service.mantenimiento;

import pe.com.rebolsysBackend.dto.mantenimiento.TipoItemRequestDTO;
import pe.com.rebolsysBackend.dto.mantenimiento.TipoItemResponseDTO;
import pe.com.rebolsysBackend.service.generic.CrudService;

public interface TipoItemService extends CrudService<TipoItemRequestDTO, TipoItemResponseDTO, Long> {}
