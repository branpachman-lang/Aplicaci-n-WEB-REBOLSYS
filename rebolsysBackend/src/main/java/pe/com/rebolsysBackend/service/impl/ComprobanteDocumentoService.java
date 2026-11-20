package pe.com.rebolsysBackend.service.impl;

import static pe.com.rebolsysBackend.service.impl.ReglasPropuesta.exigir;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.rebolsysBackend.dto.*;
import pe.com.rebolsysBackend.entity.Comprobante;
import pe.com.rebolsysBackend.enums.EstadoComprobante;
import pe.com.rebolsysBackend.exception.RecursoNoEncontradoException;
import pe.com.rebolsysBackend.repository.*;
import pe.com.rebolsysBackend.service.service.CuentaCobroService;

@Service
@RequiredArgsConstructor
public class ComprobanteDocumentoService {
  private final ComprobanteRepository comprobantes;
  private final EmisionElectronicaRepository emisiones;
  private final CuentaCobroService cuentas;
  private final HospedajeService hospedaje;

  @Transactional(readOnly = true)
  public TicketComprobanteDTO obtener(Long id, boolean envio) {
    var c = comprobantes.findById(id)
        .orElseThrow(() -> new RecursoNoEncontradoException("El comprobante no existe"));
    autorizar(c, envio);
    exigir(c.getEstadoComprobante() == EstadoComprobante.EMITIDO,
        "Genera la boleta o factura antes de imprimirla o enviarla por correo");
    var documento = cuentas.documento(c);
    var emision = emisiones.findByComprobanteIdComprobante(id);
    return new TicketComprobanteDTO(documento, hospedaje.marca(),
        c.getCliente() == null ? null : c.getCliente().getCorreo(),
        emision.map(e -> e.getSubtotal()).orElse(c.getSubtotal()),
        emision.map(e -> e.getIgv()).orElse(c.getIgv()));
  }

  private void autorizar(Comprobante c, boolean envio) {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null) throw new AccessDeniedException("Inicia sesión para continuar");
    var autoridades = auth.getAuthorities().stream().map(a -> a.getAuthority()).toList();
    String permiso = (c.getVenta() == null ? "RESERVAS:" : "VENTAS:")
        + (envio ? "EMITIR_COMPROBANTE" : "VER");
    if (!autoridades.contains("ROLE_GERENTE") && !autoridades.contains(permiso)
        && !( !envio && autoridades.contains("FACTURACION:VER")))
      throw new AccessDeniedException("Tu usuario no tiene acceso a este comprobante");
  }
}
