package pe.com.rebolsysBackend.dto;

import jakarta.validation.constraints.*;

public record ComprobanteCorreoDTO(
    @NotBlank @Email @Size(max = 254) String correo) {}
