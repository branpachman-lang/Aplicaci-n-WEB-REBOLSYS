package pe.com.rebolsysBackend.dto.reservas;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import pe.com.rebolsysBackend.dto.caja.cobros.CobroInmediatoDTO;

public record ReservaSalidaDTO(
    @NotNull @Positive Long idUsuario,
    @Positive Long idTurno,
    @Size(min = 1, max = 64) @Pattern(regexp = ".*\\S.*") String claveOperacion,
    @Valid CobroInmediatoDTO cobro,
    @Valid Comprobante comprobante) {

  public record Comprobante(
      @NotNull @Pattern(regexp = "BOLETA|FACTURA") String tipo,
      @NotNull @Positive Long idClienteReceptor) {}
}
