package pe.com.rebolsysBackend.dto.mantenimiento;

import jakarta.validation.constraints.*;
import java.util.*;
import pe.com.rebolsysBackend.enums.*;

public record ItemResponseDTO(
    Long idItem,
    String nombreItem,
    Long idTipoItem,
    EstadoItem estadoItem,
    Long idHabitacion,
    EstadoConservacion estadoActualItem,
    String nombreTipo,
    String numeroCuarto,
    String imagenRuta) {
  public ItemResponseDTO(
      Long idItem,
      String nombreItem,
      Long idTipoItem,
      EstadoItem estadoItem,
      Long idHabitacion,
      EstadoConservacion estadoActualItem,
      String nombreTipo,
      String numeroCuarto) {
    this(idItem, nombreItem, idTipoItem, estadoItem, idHabitacion, estadoActualItem, nombreTipo, numeroCuarto, null);
  }
}
