package pe.com.rebolsysBackend.controller.reservas;

import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import pe.com.rebolsysBackend.dto.comun.OperacionUsuarioDTO;
import pe.com.rebolsysBackend.dto.comun.PaginaResponseDTO;
import pe.com.rebolsysBackend.dto.reservas.ReservaDetalleRespuestaDTO;
import pe.com.rebolsysBackend.dto.reservas.ReservaOcupantesGuardarDTO;
import pe.com.rebolsysBackend.dto.reservas.ReservaRegistroRequestDTO;
import pe.com.rebolsysBackend.dto.reservas.ReservaRespuestaDTO;
import pe.com.rebolsysBackend.dto.reservas.ReservaSalidaDTO;
import pe.com.rebolsysBackend.service.service.reservas.ReservaService;

@RestController
@RequestMapping("/api/v1/reservas")
@RequiredArgsConstructor
@Tag(name = "Reservas")
public class ReservaController {
  private final ReservaService reservaservice;

  @PreAuthorize("(hasRole('GERENTE') or hasAuthority('RESERVAS:CREAR')) and "
      + "(#r.cobro() == null or (hasRole('RECEPCIONISTA') and hasAuthority('RESERVAS:COBRAR')))")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Registrar reserva con ocupantes y anticipo opcionales")
  public ReservaRespuestaDTO registrar(@Valid @RequestBody ReservaRegistroRequestDTO r) {
    return reservaservice.registrar(r);
  }

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('RESERVAS:VER')")
  @GetMapping("/{id}")
  public ReservaRespuestaDTO obtener(@PathVariable Long id) {
    return reservaservice.obtener(id);
  }

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('RESERVAS:VER')")
  @GetMapping
  public PaginaResponseDTO<ReservaRespuestaDTO> listar(
      @RequestParam(defaultValue = "0") @Min(0) int pagina,
      @RequestParam(defaultValue = "10") @Min(1) @Max(100) int tamanio,
      @Parameter(description = "Incluir importes resumidos de la cuenta por reserva")
      @RequestParam(defaultValue = "false") boolean incluirCuenta) {
    return reservaservice.listar(pagina, tamanio, incluirCuenta);
  }

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('RESERVAS:REGISTRAR_INGRESO')")
  @PostMapping("/detalles/{id}/ingreso")
  public ReservaDetalleRespuestaDTO ingreso(@PathVariable Long id, @Valid @RequestBody OperacionUsuarioDTO r) {
    return reservaservice.cambiarEstado(id, "ingreso", r);
  }

  @PreAuthorize("(hasRole('GERENTE') or hasAuthority('RESERVAS:REGISTRAR_SALIDA')) and "
      + "(#r.cobro() == null or (hasRole('RECEPCIONISTA') and hasAuthority('RESERVAS:COBRAR'))) and "
      + "(#r.comprobante() == null or hasRole('GERENTE') or hasAuthority('RESERVAS:EMITIR_COMPROBANTE'))")
  @PostMapping("/detalles/{id}/salida")
  @Operation(summary = "Registrar check-out y opcionalmente liquidar y emitir comprobante",
      description = "Con cobro o emisión, envía claveOperacion y consérvala para los reintentos.")
  public ReservaDetalleRespuestaDTO salida(@PathVariable Long id, @Valid @RequestBody ReservaSalidaDTO r) {
    return reservaservice.registrarSalida(id, r);
  }

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('RESERVAS:CANCELAR')")
  @PostMapping("/detalles/{id}/cancelacion")
  @Operation(operationId = "cancelarEstancia", summary = "Cancelar una estancia sin check-in",
      description = "Si hay un importe pagado, registra la devolución en el turno abierto del operador. Repetir la cancelación recupera el resultado.")
  public ReservaDetalleRespuestaDTO cancelar(@PathVariable Long id, @Valid @RequestBody OperacionUsuarioDTO r) {
    return reservaservice.cambiarEstado(id, "cancelacion", r);
  }

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('RESERVAS:EDITAR_OCUPANTES')")
  @PutMapping("/detalles/{id}/ocupantes")
  @Operation(summary = "Completar o corregir ocupantes antes del check-in")
  public ReservaDetalleRespuestaDTO ocupantes(
      @PathVariable Long id, @Valid @RequestBody ReservaOcupantesGuardarDTO r) {
    return reservaservice.guardarOcupantes(id, r);
  }
}
