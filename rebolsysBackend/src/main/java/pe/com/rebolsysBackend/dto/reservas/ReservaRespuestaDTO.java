package pe.com.rebolsysBackend.dto.reservas;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public record ReservaRespuestaDTO(
    Long idReserva,
    Long idCliente,
    Long idUsuario,
    String titular,
    LocalDateTime fechaRegistro,
    String claveRegistro,
    List<ReservaDetalleRespuestaDTO> detalles) {}
