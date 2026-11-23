package pe.com.rebolsysBackend.dto.reservas;

import jakarta.validation.constraints.*;
import java.util.*;
import pe.com.rebolsysBackend.dto.caja.TurnoDisponibleDTO;

public record CatalogosResponseDTO(
    List<CatalogoItemResponseDTO> tiposDocumento,
    List<CatalogoItemResponseDTO> formasPago,
    List<CatalogoItemResponseDTO> tiposHabitacion,
    List<CatalogoItemResponseDTO> tiposItem,
    List<CatalogoItemResponseDTO> tiposGasto,
    List<CatalogoItemResponseDTO> usuarios,
    List<TurnoDisponibleDTO> turnosAbiertos) {}
