package pe.com.rebolsysBackend.dto.mantenimiento;

import jakarta.validation.constraints.*;
import java.util.*;

public record AjusteStockDTO(
        @NotBlank @Size(max = 64)
        String claveOperacion,
        @NotNull @Positive
        Long idUsuario,
        @NotNull @Min(0) @Max(999999999)
        Integer stockAnterior,
        @NotNull @Min(0) @Max(999999999)
        Integer stockNuevo,
        @NotBlank @Size(max = 300)
        String observacion) {
}
