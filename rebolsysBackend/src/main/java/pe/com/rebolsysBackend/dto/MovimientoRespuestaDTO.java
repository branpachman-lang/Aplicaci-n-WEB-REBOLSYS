package pe.com.rebolsysBackend.dto;

import java.time.LocalDateTime;

public record MovimientoRespuestaDTO(
    Long idMovimiento,
    String claveOperacion,
    Long idUsuario,
    String tipo,
    int signo,
    int cantidad,
    Integer stockResultante,
    LocalDateTime fechaMovimiento,
    String observacion) {}
