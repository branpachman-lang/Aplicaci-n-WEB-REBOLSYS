package pe.com.rebolsysBackend.dto.reservas;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.*;
import java.util.*;

public record ReservaRespuestaDTO(
    Long idReserva,
    Long idCliente,
    Long idUsuario,
    String titular,
    LocalDateTime fechaRegistro,
    String claveRegistro,
    List<ReservaDetalleRespuestaDTO> detalles,
    @JsonInclude(JsonInclude.Include.NON_NULL) ResumenCuentaReservaDTO cuenta) {}
