package pe.com.rebolsysBackend.dto;

import jakarta.validation.constraints.*;
import java.util.*;

public record CatalogosResponseDTO(
    List<CatalogoItemResponseDTO> tiposDocumento,
    List<CatalogoItemResponseDTO> formasPago,
    List<CatalogoItemResponseDTO> tiposHabitacion,
    List<CatalogoItemResponseDTO> tiposItem,
    List<CatalogoItemResponseDTO> tiposGasto,
    List<CatalogoItemResponseDTO> usuarios,
    List<TurnoDisponibleDTO> turnosAbiertos) {}
