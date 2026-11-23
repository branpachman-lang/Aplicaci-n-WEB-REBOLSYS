package pe.com.rebolsysBackend.dto.accesos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "Cambiar")
public record CambioPasswordRequestDTO(
    @NotBlank @Size(max = 128) String actual,
    @NotBlank @Size(max = 128) String nueva) {
  @Override public String toString() { return "Solicitud de cambio de contraseña"; }
}
