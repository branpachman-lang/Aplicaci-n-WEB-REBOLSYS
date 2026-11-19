package pe.com.rebolsysBackend.dto;

import java.time.LocalDateTime;

public record HabitacionReservaResponseDTO(
    String referencia,
    String cliente,
    LocalDateTime ingreso,
    LocalDateTime salida,
    String estado) {}
