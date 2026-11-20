package pe.com.rebolsysBackend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record VentaConfirmacionDTO(
    @NotNull @Positive Long idUsuario,
    @NotNull @Valid CobroInmediatoDTO cobro) {}
