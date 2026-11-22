package pe.com.rebolsysBackend.dto;

import java.time.LocalDateTime;

public record CuentaEstadiaDTO(String habitacion, LocalDateTime fechaIngreso, LocalDateTime fechaSalida) {}
