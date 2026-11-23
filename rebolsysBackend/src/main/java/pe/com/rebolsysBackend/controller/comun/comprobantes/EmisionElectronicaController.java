package pe.com.rebolsysBackend.controller.comun.comprobantes;

import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import pe.com.rebolsysBackend.dto.comun.OperacionUsuarioDTO;
import pe.com.rebolsysBackend.dto.comun.comprobantes.EmisionElectronicaDTO;
import pe.com.rebolsysBackend.dto.comun.comprobantes.EmisionElectronicaRequestDTO;
import pe.com.rebolsysBackend.service.service.comun.comprobantes.EmisionElectronicaService;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name="Facturación electrónica beta")
public class EmisionElectronicaController {
  private final EmisionElectronicaService servicio;

  @PreAuthorize("hasRole('GERENTE') or hasAnyAuthority('FACTURACION:VER', 'RESERVAS:VER', 'VENTAS:VER')")
  @GetMapping("/facturacion/configuracion")
  @Operation(summary="Consultar la habilitación y los datos pendientes de facturación, sin credenciales")
  public Map<String,Object> configuracion() { return servicio.configuracion(); }

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('FACTURACION:EMITIR')")
  @PostMapping("/comprobantes/{id}/emision-electronica")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary="Preparar una boleta o factura beta desde un comprobante interno emitido",
      description="Los precios incluyen IGV de 18% para boletas y facturas. Las líneas son gravadas (afectación 10). No envía a APIsPerú todavía.")
  public EmisionElectronicaDTO preparar(@PathVariable Long id,@Valid @RequestBody EmisionElectronicaRequestDTO r) {
    return servicio.preparar(id,r);
  }
  @PreAuthorize("hasRole('GERENTE') or hasAuthority('FACTURACION:EMITIR')")
  @PostMapping("/comprobantes/{id}/simulacion")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary="Simular localmente una boleta o factura pagada sin credenciales del proveedor", description="Persiste el desglose de impuesto y el estado PREPARADO en BETA. No envía a SUNAT, no genera CDR ni XML firmado y no declara aceptación fiscal.")
  public EmisionElectronicaDTO simular(@PathVariable Long id,@Valid @RequestBody EmisionElectronicaRequestDTO r) {
    return servicio.simular(id,r);
  }
  @PreAuthorize("hasRole('GERENTE') or hasAuthority('FACTURACION:VER')")
  @GetMapping("/comprobantes/{id}/emision-electronica")
  @Operation(summary="Consultar el estado persistido de la emisión beta")
  public EmisionElectronicaDTO obtener(@PathVariable Long id) { return servicio.obtener(id); }

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('FACTURACION:ENVIAR')")
  @PostMapping("/comprobantes/{id}/emision-electronica/envio")
  @Operation(summary="Enviar a APIsPerú una copia fiscal preparada, con empresa verificada en beta")
  public EmisionElectronicaDTO enviar(@PathVariable Long id,@Valid @RequestBody OperacionUsuarioDTO r) {
    return servicio.enviar(id,r);
  }
  @PreAuthorize("hasRole('GERENTE') or hasAuthority('FACTURACION:CONSULTAR_PROVEEDOR')")
  @PostMapping("/comprobantes/{id}/emision-electronica/consulta")
  @Operation(summary="Consultar el CDR de una emisión sin volver a emitir")
  public EmisionElectronicaDTO consultar(@PathVariable Long id,@Valid @RequestBody OperacionUsuarioDTO r) {
    return servicio.consultar(id,r);
  }
  @PreAuthorize("hasRole('GERENTE') or hasAuthority('FACTURACION:VER')")
  @GetMapping("/comprobantes/{id}/emision-electronica/xml")
  @Operation(summary="Descargar el XML firmado recibido del proveedor beta")
  public ResponseEntity<byte[]> xml(@PathVariable Long id) { return archivo(id,"xml","application/xml"); }
  @PreAuthorize("hasRole('GERENTE') or hasAuthority('FACTURACION:VER')")
  @GetMapping("/comprobantes/{id}/emision-electronica/cdr")
  @Operation(summary="Descargar el ZIP del CDR recibido del proveedor beta")
  public ResponseEntity<byte[]> cdr(@PathVariable Long id) { return archivo(id,"cdr","application/zip"); }
  @PreAuthorize("hasRole('GERENTE') or hasAuthority('FACTURACION:VER')")
  @GetMapping("/comprobantes/{id}/emision-electronica/pdf")
  @Operation(summary="Obtener una representación PDF de prueba desde APIsPerú")
  public ResponseEntity<byte[]> pdf(@PathVariable Long id) { return archivo(id,"pdf","application/pdf"); }

  private ResponseEntity<byte[]> archivo(Long id,String tipo,String media) {
    return ResponseEntity.ok().contentType(MediaType.parseMediaType(media))
        .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"BETA-comprobante-"+id+"."+(tipo.equals("cdr")?"zip":tipo)+"\"")
        .header("X-Comprobante-Ambiente","BETA").body(servicio.archivo(id,tipo));
  }
}
