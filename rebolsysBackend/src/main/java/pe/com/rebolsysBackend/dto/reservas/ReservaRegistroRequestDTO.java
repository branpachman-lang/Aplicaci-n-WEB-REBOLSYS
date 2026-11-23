package pe.com.rebolsysBackend.dto.reservas;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import pe.com.rebolsysBackend.dto.caja.cobros.CobroInmediatoDTO;

public record ReservaRegistroRequestDTO(
    @NotBlank @Size(max = 64) String claveRegistro,
    @NotNull @Positive Long idCliente,
    @NotNull @Positive Long idUsuario,
    @NotEmpty @Size(max = 50) List<@NotNull @Valid ReservaDetalleRegistroDTO> detalles,
    @NotNull @Valid CobroInmediatoDTO cobro) {
  public ReservaRegistroRequestDTO(String claveRegistro, Long idCliente, Long idUsuario,
      List<ReservaDetalleRegistroDTO> detalles) {
    this(claveRegistro, idCliente, idUsuario, detalles, null);
  }
}
