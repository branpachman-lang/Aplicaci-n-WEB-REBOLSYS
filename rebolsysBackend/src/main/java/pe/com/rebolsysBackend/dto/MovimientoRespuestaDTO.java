package pe.com.rebolsysBackend.dto;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import pe.com.rebolsysBackend.enums.*;

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
