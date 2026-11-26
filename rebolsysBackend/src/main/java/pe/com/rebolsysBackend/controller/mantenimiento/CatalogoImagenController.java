package pe.com.rebolsysBackend.controller.mantenimiento;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pe.com.rebolsysBackend.dto.mantenimiento.ImagenCatalogoResponseDTO;
import pe.com.rebolsysBackend.service.impl.mantenimiento.CatalogoImagenService;

@RestController
@RequestMapping(CatalogoImagenService.RUTA)
@RequiredArgsConstructor
@Tag(name = "Imágenes de catálogo")
public class CatalogoImagenController {
  private final CatalogoImagenService imagenes;

  @PreAuthorize("isAuthenticated()")
  @GetMapping("/{categoria}/{nombre}")
  @Operation(summary = "Descargar una imagen de habitación, mobiliario o producto")
  public ResponseEntity<Resource> obtener(
      @PathVariable String categoria, @PathVariable String nombre) {
    var recurso = imagenes.obtener(categoria, nombre);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noCache())
        .contentType(tipoContenido(nombre))
        .body(recurso);
  }

  @PreAuthorize("hasRole('GERENTE') or hasAnyAuthority('HABITACIONES:CREAR', 'HABITACIONES:EDITAR')")
  @PostMapping(value = "/habitaciones/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "Subir imagen referencial de una habitación")
  public ImagenCatalogoResponseDTO subirHabitacion(
      @PathVariable @Positive Long id, @RequestPart("archivo") MultipartFile archivo) {
    return imagenes.guardar("habitaciones", id, archivo);
  }

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('HABITACIONES:EDITAR')")
  @DeleteMapping("/habitaciones/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Eliminar imagen propia de una habitación")
  public void eliminarHabitacion(@PathVariable @Positive Long id) {
    imagenes.eliminar("habitaciones", id);
  }

  @PreAuthorize("hasRole('GERENTE') or hasAnyAuthority('MOBILIARIO:CREAR', 'MOBILIARIO:EDITAR')")
  @PostMapping(value = "/mobiliario/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "Subir imagen referencial de una unidad de mobiliario")
  public ImagenCatalogoResponseDTO subirMobiliario(
      @PathVariable @Positive Long id, @RequestPart("archivo") MultipartFile archivo) {
    return imagenes.guardar("mobiliario", id, archivo);
  }

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('MOBILIARIO:EDITAR')")
  @DeleteMapping("/mobiliario/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Eliminar imagen propia de una unidad de mobiliario")
  public void eliminarMobiliario(@PathVariable @Positive Long id) {
    imagenes.eliminar("mobiliario", id);
  }

  @PreAuthorize("hasRole('GERENTE') or hasAnyAuthority('PRODUCTOS:CREAR', 'PRODUCTOS:EDITAR')")
  @PostMapping(value = "/productos/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "Subir imagen de un producto")
  public ImagenCatalogoResponseDTO subirProducto(
      @PathVariable @Positive Long id, @RequestPart("archivo") MultipartFile archivo) {
    return imagenes.guardar("productos", id, archivo);
  }

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('PRODUCTOS:EDITAR')")
  @DeleteMapping("/productos/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Eliminar imagen propia de un producto")
  public void eliminarProducto(@PathVariable @Positive Long id) {
    imagenes.eliminar("productos", id);
  }

  private static MediaType tipoContenido(String nombre) {
    if (nombre.endsWith(".png")) return MediaType.IMAGE_PNG;
    if (nombre.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
    if (nombre.endsWith(".gif")) return MediaType.IMAGE_GIF;
    return MediaType.IMAGE_JPEG;
  }
}
