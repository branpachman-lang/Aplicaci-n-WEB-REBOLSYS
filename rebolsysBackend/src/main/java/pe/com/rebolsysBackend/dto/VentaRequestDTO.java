package pe.com.rebolsysBackend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import pe.com.rebolsysBackend.enums.*;

public record VentaRequestDTO(
    @NotBlank @Size(max = 64) String claveRegistro,
    @NotNull @Positive Long idUsuario,
    @Positive Long idClienteComprador,
    @Size(max = 250) String compradorNombreLibre,
    @NotNull ModalidadCobro modalidadCobro,
    @Positive Long idDetalleReserva,
    @NotEmpty @Size(max = 100) List<@NotNull @Valid VentaLineaRegistroDTO> detalles,
    @Valid CobroInmediatoDTO cobro) {
  public VentaRequestDTO(String claveRegistro, Long idUsuario, Long idClienteComprador,
      String compradorNombreLibre, ModalidadCobro modalidadCobro, Long idDetalleReserva,
      List<VentaLineaRegistroDTO> detalles) {
    this(claveRegistro, idUsuario, idClienteComprador, compradorNombreLibre, modalidadCobro,
        idDetalleReserva, detalles, null);
  }
}
