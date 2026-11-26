package pe.com.rebolsysBackend.dto.mantenimiento;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import pe.com.rebolsysBackend.dto.reservas.HabitacionReservaResponseDTO;
import pe.com.rebolsysBackend.enums.*;

public record HabitacionEstadoResponseDTO(
    Long idHabitacion,
    String numero,
    String tipo,
    int capacidad,
    int plazasCama,
    int maxMenoresSinCama,
    BigDecimal precio,
    EstadoHabitacion estado,
    boolean ocupadaActualmente,
    boolean disponible,
    List<HabitacionReservaResponseDTO> reservas,
    java.time.LocalDateTime ultimaSalidaActiva) {}
