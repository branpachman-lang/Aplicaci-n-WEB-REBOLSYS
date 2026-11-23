package pe.com.rebolsysBackend.controller.ventas;

import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import pe.com.rebolsysBackend.dto.comun.OperacionUsuarioDTO;
import pe.com.rebolsysBackend.dto.comun.PaginaResponseDTO;
import pe.com.rebolsysBackend.dto.ventas.VentaConfirmacionDTO;
import pe.com.rebolsysBackend.dto.ventas.VentaEditarDTO;
import pe.com.rebolsysBackend.dto.ventas.VentaRequestDTO;
import pe.com.rebolsysBackend.dto.ventas.VentaResponseDTO;
import pe.com.rebolsysBackend.service.service.ventas.VentaService;

@RestController
@RequestMapping("/api/v1/ventas")
@RequiredArgsConstructor
@Tag(name = "Ventas de productos")
public class VentaController {
  private final VentaService servicio;

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('VENTAS:CREAR')")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Crear borrador de venta con varios productos")
  public VentaResponseDTO registrar(@Valid @RequestBody VentaRequestDTO r) {
    return servicio.registrar(r);
  }

  //venta producto
  @PreAuthorize("hasRole('GERENTE') or hasAuthority('VENTAS:CREAR_Y_CONFIRMAR')")
  @PostMapping("/confirmadas")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Registrar, confirmar y cobrar el total de la venta en una transacción")
  public VentaResponseDTO registrarConfirmada(@Valid @RequestBody VentaRequestDTO r) {
    return servicio.registrarConfirmada(r);
  }

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('VENTAS:EDITAR')")
  @PutMapping("/{id}")
  @Operation(summary = "Editar comprador y productos de una venta en borrador")
  public VentaResponseDTO editar(@PathVariable Long id, @Valid @RequestBody VentaEditarDTO r) {
    return servicio.editar(id, r);
  }

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('VENTAS:VER')")
  @GetMapping("/{id}")
  public VentaResponseDTO obtener(@PathVariable Long id) {
    return servicio.obtener(id);
  }

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('VENTAS:VER')")
  @GetMapping
  public PaginaResponseDTO<VentaResponseDTO> listar(
      @RequestParam(defaultValue = "0") @Min(0) int pagina,
      @RequestParam(defaultValue = "10") @Min(1) @Max(100) int tamanio) {
    return servicio.listar(pagina, tamanio);
  }

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('VENTAS:CONFIRMAR')")
  @PostMapping("/{id}/confirmacion")
  @Operation(summary = "Confirmar el borrador con pago completo y descuento de stock atómicos")
  public VentaResponseDTO confirmar(
      @PathVariable Long id, @Valid @RequestBody VentaConfirmacionDTO r) {
    return servicio.confirmarCobrada(id, r);
  }

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('VENTAS:ANULAR')")
  @PostMapping("/{id}/anulacion")
  @Operation(summary = "Anular venta sin cuenta ni cobros y reponer stock")
  public VentaResponseDTO anular(@PathVariable Long id, @Valid @RequestBody OperacionUsuarioDTO r) {
    return servicio.anular(id, r);
  }
}
