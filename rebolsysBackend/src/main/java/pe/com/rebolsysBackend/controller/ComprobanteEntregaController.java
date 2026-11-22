package pe.com.rebolsysBackend.controller;

import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import pe.com.rebolsysBackend.dto.ComprobanteCorreoDTO;
import pe.com.rebolsysBackend.service.impl.*;

@RestController
@RequestMapping("/api/v1/comprobantes/{id}")
@RequiredArgsConstructor
@Tag(name = "Entrega de comprobantes")
public class ComprobanteEntregaController {
  private final ComprobanteDocumentoService documentos;
  private final ComprobanteCorreoService correos;
  private final TicketPdfService tickets;

  @GetMapping("/ticket")
  @PreAuthorize("hasRole('GERENTE') or hasAnyAuthority('FACTURACION:VER', 'RESERVAS:VER', 'VENTAS:VER')")
  @Operation(summary = "Descargar comprobante interno en PDF A4", description = "Solo documentos emitidos; conserva el aviso de prueba. Se valida el permiso sobre su origen, reserva o venta.")
  public ResponseEntity<byte[]> ticket(@PathVariable Long id) {
    var datos = documentos.obtener(id, false);
    return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"PRUEBA-comprobante-" + id + "-A4.pdf\"")
        .body(tickets.generar(datos));
  }

  @GetMapping("/entrega")
  @PreAuthorize("hasRole('GERENTE') or hasAnyAuthority('FACTURACION:VER', 'RESERVAS:VER', 'VENTAS:VER')")
  @Operation(summary = "Consultar correo sugerido del receptor y disponibilidad del envío")
  public Map<String, Object> destino(@PathVariable Long id) { return correos.destino(id); }

  @PostMapping("/correo")
  @PreAuthorize("hasRole('GERENTE') or hasAnyAuthority('RESERVAS:EMITIR_COMPROBANTE', 'VENTAS:EMITIR_COMPROBANTE')")
  @Operation(summary = "Enviar una copia del comprobante con PDF A4 por SMTP", description = "Envío manual explícito; no emite de nuevo ni modifica pagos. No reintentar automáticamente ante un resultado incierto.")
  public Map<String, String> enviar(@PathVariable Long id, @Valid @RequestBody ComprobanteCorreoDTO solicitud) {
    correos.enviar(id, solicitud);
    return Map.of("mensaje", "El servidor de correo aceptó el comprobante para su envío.");
  }
}
