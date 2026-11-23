package pe.com.rebolsysBackend.controller.mantenimiento;

import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import pe.com.rebolsysBackend.dto.comun.PaginaResponseDTO;
import pe.com.rebolsysBackend.dto.mantenimiento.ItemAsignacionDTO;
import pe.com.rebolsysBackend.dto.mantenimiento.ItemConservacionDTO;
import pe.com.rebolsysBackend.dto.mantenimiento.ItemFiltroDTO;
import pe.com.rebolsysBackend.dto.mantenimiento.ItemHistorialDTO;
import pe.com.rebolsysBackend.dto.mantenimiento.ItemRequestDTO;
import pe.com.rebolsysBackend.dto.mantenimiento.ItemResponseDTO;
import pe.com.rebolsysBackend.dto.mantenimiento.MovimientoRespuestaDTO;
import pe.com.rebolsysBackend.service.service.mantenimiento.ItemService;

@RestController
@io.swagger.v3.oas.annotations.tags.Tag(name = "Configuración de mobiliario")
@RequestMapping("/api/v1/items")
@RequiredArgsConstructor
public class ItemController {
  private final ItemService servicio;

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('MOBILIARIO:VER')")
  @GetMapping
  public List<ItemResponseDTO> listar() {
    return servicio.readAll();
  }

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('MOBILIARIO:VER')")
  @GetMapping("/configuracion")
  @io.swagger.v3.oas.annotations.Operation(summary = "Buscar mobiliario por tipo, ubicación y conservación")
  public PaginaResponseDTO<ItemResponseDTO> configuracion(
      @Valid @org.springdoc.core.annotations.ParameterObject @ModelAttribute ItemFiltroDTO filtro) {
    return servicio.configuracion(filtro);
  }

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('MOBILIARIO:CONSERVACION')")
  @PutMapping("/{id}/conservacion")
  @io.swagger.v3.oas.annotations.Operation(summary = "Registrar la conservación del mobiliario asignado")
  public ItemResponseDTO conservacion(@PathVariable Long id, @Valid @RequestBody ItemConservacionDTO r) {
    return servicio.conservacion(id, r);
  }

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('MOBILIARIO:VER_HISTORIAL')")
  @GetMapping("/{id}/historial")
  @io.swagger.v3.oas.annotations.Operation(summary = "Consultar asignaciones, traslados, retiros y conservación")
  public List<ItemHistorialDTO> historial(@PathVariable Long id) {
    return servicio.historial(id);
  }

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('MOBILIARIO:VER')")
  @GetMapping("/{id}")
  public ItemResponseDTO obtener(@PathVariable Long id) {
    return servicio.read(id);
  }

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('MOBILIARIO:CREAR')")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ItemResponseDTO crear(@Valid @RequestBody ItemRequestDTO r) {
    return servicio.create(r);
  }

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('MOBILIARIO:EDITAR')")
  @PutMapping("/{id}")
  public ItemResponseDTO editar(@PathVariable Long id, @Valid @RequestBody ItemRequestDTO r) {
    return servicio.update(id, r);
  }

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('MOBILIARIO:ELIMINAR')")
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void eliminar(@PathVariable Long id) {
    servicio.delete(id);
  }

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('MOBILIARIO:ASIGNAR')")
  @PutMapping("/{id}/asignacion")
  public ItemResponseDTO asignar(@PathVariable Long id, @Valid @RequestBody ItemAsignacionDTO r) {
    return servicio.asignar(id, r);
  }

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('MOBILIARIO:VER_HISTORIAL')")
  @GetMapping("/{id}/movimientos")
  public List<MovimientoRespuestaDTO> movimientos(@PathVariable Long id) {
    return servicio.movimientos(id);
  }
}
