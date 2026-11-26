package pe.com.rebolsysBackend.controller.reservas;

import static pe.com.rebolsysBackend.service.impl.reservas.yape.PagoYapeDatos.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.com.rebolsysBackend.service.impl.reservas.yape.PagoYapeService;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Pagos Yape")
public class PagoYapeController {
  private static final String COBRAR =
      "hasRole('RECEPCIONISTA') and hasAuthority('RESERVAS:COBRAR')";
  private final PagoYapeService servicio;

  @GetMapping("/pagos/yape/config")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Consultar la configuración y modo vigente de Yape")
  public Configuracion config() {
    return servicio.configuracion();
  }

  @GetMapping("/reservas/{id}/yape")
  @PreAuthorize(COBRAR)
  @Operation(summary = "Consultar el último intento de cobro Yape de una reserva")
  public ResponseEntity<Respuesta> ultimo(@PathVariable @Positive Long id) {
    var r = servicio.ultimo(id);
    return r == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(r);
  }

  @PostMapping("/reservas/{id}/yape")
  @PreAuthorize(COBRAR)
  @Operation(summary = "Iniciar o recuperar un cobro Yape mediante teléfono y código OTP")
  public Respuesta iniciar(
      @PathVariable @Positive Long id, @Valid @RequestBody SolicitudCobroYape r) {
    return servicio.iniciar(id, r.claveOperacion(), r.importe(), r.telefono(), r.otp());
  }

  @PostMapping("/reservas/{id}/yape/{clave}/conciliar")
  @PreAuthorize(COBRAR)
  @Operation(summary = "Consultar y aplicar el estado de una orden Yape pendiente")
  public Respuesta conciliar(@PathVariable @Positive Long id, @PathVariable UUID clave) {
    return servicio.conciliar(id, clave);
  }

  @PostMapping("/webhooks/yape")
  @PreAuthorize("permitAll()")
  @Operation(summary = "Recibir notificaciones firmadas de órdenes Yape desde Mercado Pago")
  public ResponseEntity<Void> notificar(
      @RequestParam("data.id") String orden,
      @RequestHeader("x-request-id") String requestId,
      @RequestHeader("x-signature") String firma,
      @RequestBody JsonNode evento) {
    boolean eventoValido =
        "order".equals(evento.path("type").asText())
            && evento.path("live_mode").isBoolean()
            && orden.equalsIgnoreCase(evento.path("data").path("id").asText());
    servicio.notificar(orden, requestId, firma, eventoValido, evento.path("live_mode").asBoolean());
    return ResponseEntity.noContent().build();
  }

  public record SolicitudCobroYape(
      @NotNull UUID claveOperacion,
      @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal importe,
      @NotBlank @Pattern(regexp = "[0-9]{9}", message = "Ingresa un celular de 9 dígitos")
          String telefono,
      @NotBlank @Pattern(regexp = "[0-9]{6}", message = "Ingresa el código OTP de 6 dígitos")
          String otp) {
    @Override
    public String toString() {
      return "SolicitudCobroYape[claveOperacion="
          + claveOperacion
          + ", importe="
          + importe
          + ", telefono=[PROTEGIDO], otp=[PROTEGIDO]]";
    }
  }
}
