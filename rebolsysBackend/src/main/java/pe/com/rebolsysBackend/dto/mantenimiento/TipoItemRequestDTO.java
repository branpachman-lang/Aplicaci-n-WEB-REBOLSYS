package pe.com.rebolsysBackend.dto.mantenimiento;

import jakarta.validation.constraints.*;

public record TipoItemRequestDTO(@NotBlank @Size(max = 30) String nombreItem) {}
