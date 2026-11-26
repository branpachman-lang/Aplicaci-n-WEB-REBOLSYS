package pe.com.rebolsysBackend.service.impl.reservas;

import static pe.com.rebolsysBackend.service.impl.comun.ReglasPropuesta.*;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.rebolsysBackend.dto.caja.cobros.CuentaLineaDTO;
import pe.com.rebolsysBackend.dto.caja.cobros.CuentaReservaDTO;
import pe.com.rebolsysBackend.dto.caja.cobros.PagoCuentaDTO;
import pe.com.rebolsysBackend.dto.comun.comprobantes.ComprobanteCuentaDTO;
import pe.com.rebolsysBackend.dto.comun.comprobantes.CuentaDocumentoDTO;
import pe.com.rebolsysBackend.dto.reservas.ConsumoReservaRespuestaDTO;
import pe.com.rebolsysBackend.dto.reservas.CuentaEstadiaDTO;
import pe.com.rebolsysBackend.dto.reservas.ReservaComprobanteRegistroDTO;
import pe.com.rebolsysBackend.dto.reservas.ReservaPagoRegistroDTO;
import pe.com.rebolsysBackend.dto.ventas.VentaRequestDTO;
import pe.com.rebolsysBackend.repository.caja.ArqueoCajaRepository;
import pe.com.rebolsysBackend.repository.caja.FlujoCajaRepository;
import pe.com.rebolsysBackend.repository.caja.cobros.PagoRepository;
import pe.com.rebolsysBackend.repository.comun.comprobantes.ComprobanteRepository;
import pe.com.rebolsysBackend.repository.reservas.DetalleReservaRepository;
import pe.com.rebolsysBackend.repository.reservas.ReservaRepository;
import pe.com.rebolsysBackend.repository.ventas.VentaRepository;
import pe.com.rebolsysBackend.security.SesionOperacionService;
import pe.com.rebolsysBackend.service.impl.caja.cobros.CuentaReservaGestion;
import pe.com.rebolsysBackend.service.impl.comun.ReglasPropuesta;
import pe.com.rebolsysBackend.service.impl.reservas.yape.PagoYapeService;
import pe.com.rebolsysBackend.service.service.caja.cobros.CuentaCobroService;
import pe.com.rebolsysBackend.service.service.reservas.CuentaReservaService;
import pe.com.rebolsysBackend.service.service.ventas.VentaService;
import pe.com.rebolsysBackend.entity.*;
import pe.com.rebolsysBackend.enums.*;
import pe.com.rebolsysBackend.exception.*;
import pe.com.rebolsysBackend.util.IgvIncluido;

@Service
@RequiredArgsConstructor
public class CuentaReservaServiceImpl implements CuentaReservaService {
  private final EntityManager em;
  private final CuentaReservaGestion gestion;
  private final CuentaCobroService cobros;
  private final ReservaRepository reservas;
  private final DetalleReservaRepository detalles;
  private final VentaRepository ventas;
  private final ComprobanteRepository cuentas;
  private final PagoRepository pagos;
  private final FlujoCajaRepository flujos;
  private final ArqueoCajaRepository arqueos;
  private final VentaService ventaService;
  private final SesionOperacionService sesion;
  private final PagoYapeService pagoYape;
  private final ReglasPropuesta reglas;

  private Reserva buscar(Long id) {
    return reservas
        .findById(id)
        .orElseThrow(() -> new RecursoNoEncontradoException("La reserva no existe"));
  }

  @Override
  @Transactional(readOnly = true)
  public CuentaReservaDTO cuenta(Long id) {
    var r = buscar(id);
    var docs = new ArrayList<CuentaDocumentoDTO>();
    var principal = gestion.principal(r);
    if (principal.isEmpty()) {
      var cargos =
          r.getDetalles().stream()
              .filter(d -> d.getEstadoDetalle() != EstadoReserva.CANCELADA)
              .map(
                  d ->
                      new CuentaLineaDTO(
                          d.getIdDetalleReserva(),
                          null,
                          "Alojamiento habitación " + d.getHabitacion().getNumeroCuarto(),
                          BigDecimal.ONE,
                          d.getCostoEstadia()))
              .toList();
      var impuesto = IgvIncluido.resumir(cargos.stream().map(CuentaLineaDTO::total));
      docs.add(
          new CuentaDocumentoDTO(
              null,
              id,
              "R" + id,
              null,
              "CUENTA",
              EstadoComprobante.BORRADOR,
              "CTA",
              null,
              r.getCliente().getIdPerfil(),
              null,
              null,
              null,
              gestion.alojamiento(r),
              impuesto.subtotal(),
              impuesto.igv(),
              BigDecimal.ZERO,
              gestion.alojamiento(r),
              cargos,
              List.of(),
              r.getDetalles().stream()
                  .filter(d -> d.getEstadoDetalle() != EstadoReserva.CANCELADA)
                  .map(d -> new CuentaEstadiaDTO(d.getHabitacion().getNumeroCuarto(), d.getFechaIngreso(), d.getFechaSalida()))
                  .toList()));
    }
    cuentas.cuentasReserva(id).forEach(c -> docs.add(documento(c)));
    var total =
        docs.stream().map(CuentaDocumentoDTO::total).reduce(BigDecimal.ZERO, BigDecimal::add);
    var pagado =
        docs.stream().map(CuentaDocumentoDTO::pagado).reduce(BigDecimal.ZERO, BigDecimal::add);
    return new CuentaReservaDTO(
        id,
        "R" + id,
        gestion.alojamiento(r),
        total,
        pagado,
        total.subtract(pagado),
        principal.map(c -> c.getEstadoComprobante() == EstadoComprobante.BORRADOR)
            .orElseGet(() -> r.getDetalles().stream().anyMatch(d -> d.getEstadoDetalle() == EstadoReserva.EN_CURSO)),
        docs);
  }

  @Override
  @Transactional
  public CuentaReservaDTO pagar(Long id, ReservaPagoRegistroDTO request) {
    reglas.validar(request);
    var r = gestion.bloquear(id);
    reglas.usuario(request.idUsuario());
    var previo = pagos.findByClaveIdempotencia(request.claveOperacion());
    if (previo.isPresent()) {
      var p = previo.get();
      var c = p.getComprobante();
      boolean mismoAmbito =
          request.idVenta() == null
              ? c.getReserva() != null && c.getReserva().getIdReserva().equals(id)
              : c.getVenta() != null
                  && c.getVenta().getIdVenta().equals(request.idVenta())
                  && c.getVenta().getDetalleReserva() != null
                  && c.getVenta().getDetalleReserva().getReserva().getIdReserva().equals(id);
      exigir(
          mismoAmbito
              && p.getUsuario().getIdUsuario().equals(request.idUsuario())
              && p.getTurnoCaja().getIdTurno().equals(request.idTurno())
              && p.getFormaPago().getIdFormaPago().equals(request.idFormaPago())
              && p.getMonto().compareTo(request.monto()) == 0
              && Objects.equals(p.getNumeroOperacion(), limpiar(request.numeroOperacion())),
          "La clave de pago ya corresponde a otra solicitud");
      return cuenta(id);
    }
    var turno = gestion.turno(request.idTurno(), request.idUsuario());
    var c = cuentaObjetivo(r, request.idVenta(), turno);
    cobros.pagar(c, new PagoCuentaDTO(request.claveOperacion(), request.idUsuario(), request.idTurno(),
        request.idFormaPago(), request.monto(), request.numeroOperacion()));
    return cuenta(id);
  }

  @Override
  @Transactional
  public CuentaReservaDTO eliminarPago(Long idReserva, Long idPago) {
    var actor = sesion.actual();
    reglas.usuario(actor.idUsuario());
    gestion.bloquear(idReserva);
    var p =
        pagos
            .findById(idPago)
            .orElseThrow(() -> new RecursoNoEncontradoException("El pago no existe"));
    var cuentaPago = p.getComprobante();
    boolean pertenece =
        (cuentaPago.getReserva() != null
                && cuentaPago.getReserva().getIdReserva().equals(idReserva))
            || (cuentaPago.getVenta() != null
                && cuentaPago.getVenta().getDetalleReserva() != null
                && cuentaPago.getVenta().getDetalleReserva().getReserva().getIdReserva().equals(idReserva));
    exigir(pertenece, "El pago no pertenece a esta reserva");
    var c =
        cuentas
            .bloquear(cuentaPago.getIdComprobante())
            .orElseThrow(() -> new RecursoNoEncontradoException("La cuenta no existe"));
    em.refresh(c);
    exigir(
        c.getEstadoComprobante() == EstadoComprobante.BORRADOR,
        "Solo se pueden eliminar pagos de una cuenta en borrador sin comprobante emitido");
    var listaDetalles = detalles.findByReservaIdReservaOrderByIdDetalleReserva(idReserva);
    exigir(
        !detalles.existsByIdPagoSalida(idPago)
            && listaDetalles.stream().noneMatch(d -> d.getEstadoDetalle() == EstadoReserva.FINALIZADA),
        "No se puede eliminar un pago cuando la reserva tiene check-out finalizado");
    exigir(
        listaDetalles.stream()
            .anyMatch(
                d ->
                    d.getEstadoDetalle() == EstadoReserva.CONFIRMADA
                        || d.getEstadoDetalle() == EstadoReserva.EN_CURSO),
        "La reserva debe tener una estancia confirmada o en curso");
    var turno = gestion.turno(p.getTurnoCaja().getIdTurno(), actor.idUsuario());
    exigir(
        arqueos.findByTurnoCajaIdTurnoOrderByFormaPagoIdFormaPago(turno.getIdTurno()).isEmpty(),
        "El turno ya tiene arqueos guardados y no permite eliminar pagos");
    if ("YAPE".equalsIgnoreCase(p.getFormaPago().getNombrePago())) {
      pagoYape.desvincularPagoEliminado(idPago);
    }
    flujos.deleteByPagoIdPago(idPago);
    flujos.flush();
    pagos.delete(p);
    pagos.flush();
    return cuenta(idReserva);
  }

  private Comprobante cuentaObjetivo(Reserva r, Long idVenta, TurnoCaja turno) {
    if (idVenta == null) {
      exigir(
          r.getDetalles().stream().anyMatch(d -> d.getEstadoDetalle() != EstadoReserva.CANCELADA),
          "La reserva está anulada");
      return gestion.principal(r, turno);
    }
    var v = ventaDeReserva(r.getIdReserva(), idVenta);
    exigir(cuentas.findByClaveIdempotencia("CUENTA-V-" + v.getIdVenta()).isPresent(),
        "Este consumo histórico se paga en la cuenta principal");
    return gestion.cuentaVenta(v);
  }

  private Venta ventaDeReserva(Long idReserva, Long idVenta) {
    var v =
        ventas
            .findById(idVenta)
            .orElseThrow(() -> new RecursoNoEncontradoException("El consumo no existe"));
    exigir(
        v.getDetalleReserva() != null
            && v.getDetalleReserva().getReserva().getIdReserva().equals(idReserva),
        "El consumo no pertenece a esta reserva");
    return v;
  }

  @Override
  @Transactional
  public CuentaDocumentoDTO emitir(Long id, ReservaComprobanteRegistroDTO request) {
    reglas.validar(request);
    var r = gestion.bloquear(id);
    reglas.usuario(request.idUsuario());
    var turno = gestion.turno(request.idTurno(), request.idUsuario());
    var c = cuentaObjetivo(r, request.idVenta(), turno);
    if (request.idVenta() == null && c.getEstadoComprobante() != EstadoComprobante.EMITIDO)
      gestion.validarEmisionFinal(r);
    return cobros.documento(cobros.emitir(c, new ComprobanteCuentaDTO(request.idUsuario(),
        request.idTurno(), request.tipo(), request.idClienteReceptor())));
  }

  @Override
  @Transactional
  public ConsumoReservaRespuestaDTO consumir(Long id, VentaRequestDTO request) {
    reglas.validar(request);
    gestion.bloquear(id);
    exigir(
        request.idDetalleReserva() != null, "Indica la habitación de la reserva para este consumo");
    // El servicio de ventas valida existencia, estado y stock; todo participa de esta transacción.
    var detalle =
        buscar(id).getDetalles().stream()
            .filter(d -> d.getIdDetalleReserva().equals(request.idDetalleReserva()))
            .findFirst();
    exigir(detalle.isPresent(), "La habitación del consumo no pertenece a esta reserva");
    exigir(request.modalidadCobro() == ModalidadCobro.RESERVA, "El consumo debe quedar asociado a la reserva");
    var v = ventaService.registrarConfirmada(request);
    return consumo(ventas.findById(v.idVenta()).orElseThrow());
  }

  @Override
  @Transactional(readOnly = true)
  public List<ConsumoReservaRespuestaDTO> consumos(Long id) {
    buscar(id);
    return ventas.findByDetalleReservaReservaIdReservaOrderByIdVenta(id).stream()
        .map(this::consumo)
        .toList();
  }

  private ConsumoReservaRespuestaDTO consumo(Venta v) {
    Long id = v.getDetalleReserva().getReserva().getIdReserva();
    Long idCuenta = cuentas.findByClaveIdempotencia("CUENTA-V-" + v.getIdVenta())
        .map(Comprobante::getIdComprobante)
        .orElseGet(() -> v.getModalidadCobro() == ModalidadCobro.RESERVA
            ? gestion.principal(v.getDetalleReserva().getReserva()).map(Comprobante::getIdComprobante).orElse(null)
            : null);
    return new ConsumoReservaRespuestaDTO(
        id, "R" + id, idCuenta, ventaService.obtener(v.getIdVenta()));
  }

  private CuentaDocumentoDTO documento(Comprobante c) {
    return cobros.documento(c);
  }
}
