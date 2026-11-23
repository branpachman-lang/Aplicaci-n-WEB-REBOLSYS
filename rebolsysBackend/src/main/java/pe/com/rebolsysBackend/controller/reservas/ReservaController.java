package pe.com.rebolsysBackend.controller.reservas;

import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
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
import pe.com.rebolsysBackend.service.service.reservas.ReservaService;

@RestController
@RequestMapping("/api/v1/reservas")
@RequiredArgsConstructor
@Tag(name = "Reservas")
public class ReservaController {
  private final ReservaService reservaservice;

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('RESERVAS:CREAR')")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Registrar reserva con ocupantes opcionales y pago completo en una transacción")
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
      @RequestParam(defaultValue = "10") @Min(1) @Max(100) int tamanio) {
    return reservaservice.listar(pagina, tamanio);
  }

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('RESERVAS:REGISTRAR_INGRESO')")
  @PostMapping("/detalles/{id}/ingreso")
  public ReservaDetalleRespuestaDTO ingreso(@PathVariable Long id, @Valid @RequestBody OperacionUsuarioDTO r) {
    return reservaservice.cambiarEstado(id, "ingreso", r);
  }

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('RESERVAS:REGISTRAR_SALIDA')")
  @PostMapping("/detalles/{id}/salida")
  public ReservaDetalleRespuestaDTO salida(@PathVariable Long id, @Valid @RequestBody OperacionUsuarioDTO r) {
    return reservaservice.cambiarEstado(id, "salida", r);
  }

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('RESERVAS:CANCELAR')")
  @PostMapping("/detalles/{id}/cancelacion")
  @Operation(operationId = "cancelarEstancia")
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
