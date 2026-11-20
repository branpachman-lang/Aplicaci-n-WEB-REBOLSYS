package pe.com.rebolsysBackend.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/** El usuario y el turno se obtienen de la operación autenticada, nunca de este objeto. */
public record CobroInmediatoDTO(
    @NotNull @Positive Long idFormaPago,
    @NotNull @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal monto,
    @Size(max = 50) String numeroOperacion) {}
