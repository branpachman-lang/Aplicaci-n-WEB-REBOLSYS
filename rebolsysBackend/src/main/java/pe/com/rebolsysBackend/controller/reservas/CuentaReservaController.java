package pe.com.rebolsysBackend.controller.reservas;

import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.com.rebolsysBackend.dto.caja.cobros.CuentaReservaDTO;
import pe.com.rebolsysBackend.dto.comun.comprobantes.CuentaDocumentoDTO;
import pe.com.rebolsysBackend.dto.reservas.ConsumoReservaRespuestaDTO;
import pe.com.rebolsysBackend.dto.reservas.ReservaComprobanteRegistroDTO;
import pe.com.rebolsysBackend.dto.reservas.ReservaPagoRegistroDTO;
import pe.com.rebolsysBackend.dto.ventas.VentaRequestDTO;
import pe.com.rebolsysBackend.service.service.reservas.CuentaReservaService;

@RestController
@RequestMapping("/api/v1/reservas/{idReserva}")
@RequiredArgsConstructor
@Tag(name = "Reservas")
public class CuentaReservaController {
  private final CuentaReservaService servicio;

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('RESERVAS:VER')")
  @GetMapping("/cuenta")
  @Operation(summary = "Consultar alojamiento, consumos, pagos y saldo de la reserva")
  public CuentaReservaDTO cuenta(@PathVariable Long idReserva) {
    return servicio.cuenta(idReserva);
  }

  @PreAuthorize("hasRole('RECEPCIONISTA') and hasAuthority('RESERVAS:COBRAR')")
  @PostMapping("/pagos")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Registrar un abono a la cuenta principal o a un consumo separado",
      description = "Requiere rol RECEPCIONISTA, permiso RESERVAS:COBRAR y turno propio abierto.")
  public CuentaReservaDTO pagar(
      @PathVariable Long idReserva, @Valid @RequestBody ReservaPagoRegistroDTO request) {
    return servicio.pagar(idReserva, request);
  }

  @PreAuthorize("hasRole('RECEPCIONISTA') and hasAuthority('RESERVAS:COBRAR')")
  @DeleteMapping("/pagos/{idPago}")
  @Operation(
      summary = "Eliminar un detalle de pago según reglas de negocio",
      description =
          "Requiere rol RECEPCIONISTA, permiso RESERVAS:COBRAR, cuenta en borrador, estancia no"
              + " finalizada, turno propio abierto sin arqueos y pago no liquidado en línea vía"
              + " Mercado Pago.")
  public CuentaReservaDTO eliminarPago(
      @PathVariable @Positive Long idReserva, @PathVariable @Positive Long idPago) {
    return servicio.eliminarPago(idReserva, idPago);
  }

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('RESERVAS:EMITIR_COMPROBANTE')")
  @PostMapping("/comprobantes")
  @Operation(summary = "Emitir registro interno de boleta o factura de una cuenta",
      description = "La cuenta principal se emite al completar la última salida con la reserva liquidada.")
  public CuentaDocumentoDTO emitir(
      @PathVariable Long idReserva, @Valid @RequestBody ReservaComprobanteRegistroDTO request) {
    return servicio.emitir(idReserva, request);
  }

  @PreAuthorize("hasRole('GERENTE') or (hasAuthority('RESERVAS:REGISTRAR_CONSUMO') and "
      + "hasAuthority('VENTAS:CREAR_Y_CONFIRMAR'))")
  @PostMapping("/consumos")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Registrar y confirmar consumo de una habitación de la reserva")
  public ConsumoReservaRespuestaDTO consumir(
      @PathVariable Long idReserva, @Valid @RequestBody VentaRequestDTO request) {
    return servicio.consumir(idReserva, request);
  }

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('RESERVAS:VER')")
  @GetMapping("/consumos")
  @Operation(summary = "Consultar consumos consolidados y separados de la reserva")
  public List<ConsumoReservaRespuestaDTO> consumos(@PathVariable Long idReserva) {
    return servicio.consumos(idReserva);
  }
}
