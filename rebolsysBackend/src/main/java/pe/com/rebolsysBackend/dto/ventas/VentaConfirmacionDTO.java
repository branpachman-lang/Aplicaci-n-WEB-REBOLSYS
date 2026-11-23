package pe.com.rebolsysBackend.dto.ventas;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import pe.com.rebolsysBackend.dto.caja.cobros.CobroInmediatoDTO;

public record VentaConfirmacionDTO(
    @NotNull @Positive Long idUsuario,
    @NotNull @Valid CobroInmediatoDTO cobro) {}
