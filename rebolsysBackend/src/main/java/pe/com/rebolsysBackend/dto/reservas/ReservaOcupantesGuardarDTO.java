package pe.com.rebolsysBackend.dto.reservas;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record ReservaOcupantesGuardarDTO(
    @NotNull @Positive Long idUsuario,
    @NotEmpty @Size(max = 99) List<@NotNull @Valid ReservaOcupanteRegistroDTO> ocupantes) {}
