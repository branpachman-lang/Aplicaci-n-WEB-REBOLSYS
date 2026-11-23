package pe.com.rebolsysBackend.controller.reportes;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import pe.com.rebolsysBackend.dto.limpieza.LimpiezaDTO.InformeDetalle;
import pe.com.rebolsysBackend.dto.reportes.ReportesDTO.*;
import pe.com.rebolsysBackend.enums.EstadoInforme;
import pe.com.rebolsysBackend.security.SesionOperacionService;
import pe.com.rebolsysBackend.service.impl.reportes.ReportesService;
import pe.com.rebolsysBackend.service.service.limpieza.LimpiezaService;

@RestController
@RequestMapping("/api/v1/reportes")
@Tag(name = "Reportes", description = "Consultas de Gerencia. Los rangos de fechas incluyen ambos días en hora de Lima.")
@ApiResponse(responseCode = "403", description = "La sesión activa requiere el rol GERENTE")
@PreAuthorize("hasRole('GERENTE')")
@RequiredArgsConstructor
public class ReportesController {
  private final ReportesService reportes;
  private final LimpiezaService limpieza;
  private final SesionOperacionService sesiones;

  @GetMapping("/incidencias")
  public Pagina<Incidencia> incidencias(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
      @RequestParam(required = false) @Size(max = 10) String habitacion,
      @RequestParam(required = false) EstadoInforme estado,
      @RequestParam(defaultValue = "0") @PositiveOrZero int pagina,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanio) {
    return reportes.incidencias(desde, hasta, habitacion, estado, pagina, tamanio);
  }

  @GetMapping("/incidencias/{idInforme}")
  public InformeDetalle informe(@PathVariable @Positive Long idInforme) {
    return limpieza.informe(idInforme);
  }

  @GetMapping("/financiero")
  public Financiero financiero(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
    return reportes.financiero(desde, hasta, sesiones.actual().usuario());
  }

  @GetMapping("/cierres-caja")
  public Pagina<Cierre> cierres(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
      @RequestParam(required = false) @Size(max = 50) String usuario,
      @RequestParam(defaultValue = "0") @PositiveOrZero int pagina,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanio) {
    return reportes.cierres(desde, hasta, usuario, pagina, tamanio);
  }

  @GetMapping("/inventario/productos")
  public Pagina<Existencia> productos(@RequestParam(required = false) @Size(max = 100) String buscar,
      @RequestParam(defaultValue = "false") boolean stockBajo,
      @RequestParam(defaultValue = "0") @PositiveOrZero int pagina,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanio) {
    return reportes.productos(buscar, stockBajo, pagina, tamanio);
  }

  @GetMapping("/inventario/mobiliario")
  public Pagina<Mueble> inventarioMobiliario(@RequestParam(required = false) @Size(max = 100) String buscar,
      @RequestParam(required = false) @Size(max = 10) String habitacion,
      @RequestParam(defaultValue = "0") @PositiveOrZero int pagina,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanio) {
    return reportes.mobiliario(buscar, habitacion, pagina, tamanio);
  }
}
