package pe.com.rebolsysBackend.service.service.mantenimiento;

import pe.com.rebolsysBackend.dto.mantenimiento.CategoriaRequestDTO;
import pe.com.rebolsysBackend.dto.mantenimiento.CategoriaResponseDTO;
import pe.com.rebolsysBackend.service.generic.CrudService;

public interface CategoriaService extends CrudService<CategoriaRequestDTO, CategoriaResponseDTO, Long> {}
