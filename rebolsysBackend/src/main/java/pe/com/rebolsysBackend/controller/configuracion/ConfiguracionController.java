package pe.com.rebolsysBackend.controller.configuracion;

import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pe.com.rebolsysBackend.dto.configuracion.ConfiguracionCatalogosDTO;
import pe.com.rebolsysBackend.service.service.configuracion.ConfiguracionService;

@RestController
@RequestMapping("/api/v1/configuraciones")
@RequiredArgsConstructor
@Tag(name = "Configuración")
public class ConfiguracionController {
  private final ConfiguracionService servicio;

  @PreAuthorize("hasAuthority('ACCESO_OPERATIVO')")
  @GetMapping("/catalogos")
  @Operation(summary = "Obtener tipos, categorías y estados para los formularios de configuración")
  public ConfiguracionCatalogosDTO catalogos() {
    return servicio.catalogos();
  }
}
