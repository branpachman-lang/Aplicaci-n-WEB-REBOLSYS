package pe.com.rebolsysBackend.controller.mantenimiento;

import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.com.rebolsysBackend.dto.comun.PaginaResponseDTO;
import pe.com.rebolsysBackend.dto.mantenimiento.ProductoRequestDTO;
import pe.com.rebolsysBackend.dto.mantenimiento.ProductoResponseDTO;
import pe.com.rebolsysBackend.enums.EstadoProducto;
import pe.com.rebolsysBackend.service.service.mantenimiento.ProductoService;

@RestController
@io.swagger.v3.oas.annotations.tags.Tag(name = "Configuración de productos")
@RequestMapping("/api/v1/productos")
public class ProductoController {

  private final ProductoService productoService;

  public ProductoController(ProductoService productoService) {
    this.productoService = productoService;
  }

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('PRODUCTOS:VER')")
  @GetMapping
  public ResponseEntity<PaginaResponseDTO<ProductoResponseDTO>> listar(
      @RequestParam(defaultValue = "0") @Min(value = 0, message = "La página no puede ser negativa")
          int pagina,
      @RequestParam(defaultValue = "10")
          @Min(value = 1, message = "El tamaño debe ser positivo")
          @Max(value = 100, message = "El tamaño no debe superar 100")
          int tamanio,
      @RequestParam(defaultValue = "") String buscar,
      @RequestParam(required = false) @jakarta.validation.constraints.Positive Long idCategoria,
      @RequestParam(required = false) EstadoProducto estado,
      @RequestParam(required = false) Boolean stockBajo) {
    return ResponseEntity.ok(productoService.listar(pagina, tamanio, buscar, idCategoria, estado, stockBajo));
  }

  @PreAuthorize("hasRole('GERENTE') or hasAnyAuthority('PRODUCTOS:VER', 'VENTAS:CREAR', 'VENTAS:CREAR_Y_CONFIRMAR', 'VENTAS:EDITAR', 'RESERVAS:REGISTRAR_CONSUMO')")
  @GetMapping("/catalogo")
  public ResponseEntity<List<ProductoResponseDTO>> catalogo() {
    return ResponseEntity.ok(productoService.catalogo());
  }

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('PRODUCTOS:VER')")
  @GetMapping("/{id}")
  public ResponseEntity<ProductoResponseDTO> read(@PathVariable Long id) {
    return ResponseEntity.ok(productoService.read(id));
  }

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('PRODUCTOS:CREAR')")
  @PostMapping
  public ResponseEntity<ProductoResponseDTO> create(
      @Valid @RequestBody ProductoRequestDTO request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(productoService.create(request));
  }

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('PRODUCTOS:EDITAR')")
  @PutMapping("/{id}")
  public ResponseEntity<ProductoResponseDTO> update(
      @PathVariable Long id, @Valid @RequestBody ProductoRequestDTO request) {
    return ResponseEntity.ok(productoService.update(id, request));
  }

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('PRODUCTOS:ELIMINAR')")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    productoService.delete(id);
    return ResponseEntity.noContent().build();
  }

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('PRODUCTOS:AJUSTAR_STOCK')")
  @PostMapping("/{id}/ajustes-stock")
  public ProductoResponseDTO ajustar(
      @PathVariable Long id, @Valid @RequestBody pe.com.rebolsysBackend.dto.mantenimiento.AjusteStockDTO r) {
    return productoService.ajustarStock(id, r);
  }

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('PRODUCTOS:VER_MOVIMIENTOS')")
  @GetMapping("/{id}/movimientos")
  public List<pe.com.rebolsysBackend.dto.mantenimiento.MovimientoRespuestaDTO> movimientos(
      @PathVariable Long id) {
    return productoService.movimientos(id);
  }
}
