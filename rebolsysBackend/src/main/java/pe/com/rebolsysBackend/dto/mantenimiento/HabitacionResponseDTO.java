package pe.com.rebolsysBackend.dto.mantenimiento;

import jakarta.validation.constraints.*;
import java.util.*;
import pe.com.rebolsysBackend.enums.*;

public record HabitacionResponseDTO(
    Long idHabitacion,
    String numeroCuarto,
    Long idTipoHabitacion,
    String nombreTipo,
    EstadoHabitacion estadoHabitacion,
    int capacidadPersonas,
    int plazasCama,
    int maxMenoresSinCama,
    @io.swagger.v3.oas.annotations.media.Schema(description = "Precio efectivo por noche: tarifa propia de la habitación o, si no existe, precio del tipo.")
    java.math.BigDecimal precioBase,
    java.math.BigDecimal precioPorNoche,
    String imagenRuta) {

  public HabitacionResponseDTO(
      Long idHabitacion,
      String numeroCuarto,
      Long idTipoHabitacion,
      String nombreTipo,
      EstadoHabitacion estadoHabitacion,
      int capacidadPersonas,
      int plazasCama,
      int maxMenoresSinCama,
      java.math.BigDecimal precioBase,
      java.math.BigDecimal precioPorNoche) {
    this(idHabitacion, numeroCuarto, idTipoHabitacion, nombreTipo, estadoHabitacion,
        capacidadPersonas, plazasCama, maxMenoresSinCama, precioBase, precioPorNoche, null);
  }
}
