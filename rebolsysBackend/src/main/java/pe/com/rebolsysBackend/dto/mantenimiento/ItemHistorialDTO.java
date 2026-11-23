package pe.com.rebolsysBackend.dto.mantenimiento;

import java.time.LocalDateTime;
import pe.com.rebolsysBackend.enums.EstadoConservacion;

public record ItemHistorialDTO(
    Long idMovimiento, String accion, Long idUsuario, String usuario,
    LocalDateTime fechaMovimiento, Long idHabitacionOrigen, String numeroOrigen,
    Long idHabitacionDestino, String numeroDestino, EstadoConservacion estadoConservacion,
    String observacion) {}
