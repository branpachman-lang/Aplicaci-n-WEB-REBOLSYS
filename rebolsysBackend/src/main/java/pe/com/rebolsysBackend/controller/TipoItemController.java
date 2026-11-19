package pe.com.rebolsysBackend.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.com.rebolsysBackend.dto.TipoItemRequestDTO;
import pe.com.rebolsysBackend.dto.TipoItemResponseDTO;
import pe.com.rebolsysBackend.service.service.TipoItemService;

@RestController
@io.swagger.v3.oas.annotations.tags.Tag(name = "Configuración de mobiliario")
@RequestMapping("/api/v1/tipos-item")
@RequiredArgsConstructor
public class TipoItemController {
  private final TipoItemService servicio;

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('MOBILIARIO:VER')")
  @GetMapping
  public Iterable<TipoItemResponseDTO> listar() {
    return servicio.readAll();
  }

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('MOBILIARIO:VER')")
  @GetMapping("/{id}")
  public TipoItemResponseDTO obtener(@PathVariable Long id) {
    return servicio.read(id);
  }

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('MOBILIARIO:CREAR_TIPO')")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public TipoItemResponseDTO crear(@Valid @RequestBody TipoItemRequestDTO r) {
    return servicio.create(r);
  }

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('MOBILIARIO:EDITAR_TIPO')")
  @PutMapping("/{id}")
  public TipoItemResponseDTO editar(@PathVariable Long id, @Valid @RequestBody TipoItemRequestDTO r) {
    return servicio.update(id, r);
  }

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('MOBILIARIO:ELIMINAR_TIPO')")
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void eliminar(@PathVariable Long id) {
    servicio.delete(id);
  }
}
