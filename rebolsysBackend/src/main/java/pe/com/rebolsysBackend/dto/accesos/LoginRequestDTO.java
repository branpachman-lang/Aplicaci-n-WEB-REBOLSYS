package pe.com.rebolsysBackend.dto.accesos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "Iniciar")
public record LoginRequestDTO(
    @NotBlank @Size(max = 50) String usuario,
    @NotBlank @Size(max = 128) String contrasena) {
  @Override public String toString() { return "Solicitud de inicio de sesión"; }
}
