package pe.com.rebolsysBackend.dto.mantenimiento;

import jakarta.validation.constraints.NotNull;
import pe.com.rebolsysBackend.enums.EstadoHabitacion;

public record HabitacionEstadoGuardarDTO(@NotNull EstadoHabitacion estadoHabitacion) {}
