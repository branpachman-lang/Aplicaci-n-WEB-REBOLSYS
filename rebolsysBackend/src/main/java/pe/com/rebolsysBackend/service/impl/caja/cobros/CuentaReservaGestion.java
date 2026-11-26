package pe.com.rebolsysBackend.service.impl.caja.cobros;

import static pe.com.rebolsysBackend.service.impl.comun.ReglasPropuesta.*;

import jakarta.persistence.EntityManager;
import java.math.*;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pe.com.rebolsysBackend.dto.reservas.ResumenCuentaReservaDTO;
import pe.com.rebolsysBackend.repository.caja.FlujoCajaRepository;
import pe.com.rebolsysBackend.repository.caja.TipoMovimientoCajaRepository;
import pe.com.rebolsysBackend.repository.caja.TurnoCajaRepository;
import pe.com.rebolsysBackend.repository.caja.cobros.PagoRepository;
import pe.com.rebolsysBackend.repository.comun.comprobantes.ComprobanteDetalleRepository;
import pe.com.rebolsysBackend.repository.comun.comprobantes.ComprobanteRepository;
import pe.com.rebolsysBackend.repository.comun.comprobantes.SerieComprobanteRepository;
import pe.com.rebolsysBackend.repository.comun.comprobantes.TipoComprobanteRepository;
import pe.com.rebolsysBackend.repository.reservas.DetalleReservaRepository;
import pe.com.rebolsysBackend.repository.reservas.ReservaRepository;
import pe.com.rebolsysBackend.entity.*;
import pe.com.rebolsysBackend.enums.*;
import pe.com.rebolsysBackend.exception.*;

/** Operaciones de cuenta bajo el bloqueo de la reserva y una transacción del servicio. */
@Component
@RequiredArgsConstructor
public class CuentaReservaGestion {
  private final EntityManager em;
  private final ReservaRepository reservas;
  private final DetalleReservaRepository detallesReserva;
  private final ComprobanteRepository cuentas;
  private final ComprobanteDetalleRepository lineas;
  private final PagoRepository pagos;
  private final TipoComprobanteRepository tipos;
  private final SerieComprobanteRepository series;
  private final TurnoCajaRepository turnos;
  private final FlujoCajaRepository flujos;
  private final TipoMovimientoCajaRepository movimientos;

  public Reserva bloquear(Long id) {
    var r = reservas.bloquear(id).orElseThrow(() -> new RecursoNoEncontradoException("La reserva no existe"));
    return r;
  }

  public BigDecimal alojamiento(Reserva r) {
    return alojamiento(detallesReserva.findByReservaIdReservaOrderByIdDetalleReserva(r.getIdReserva()));
  }

  public BigDecimal alojamiento(List<DetalleReserva> detalles) {
    return detalles.stream()
        .filter(d -> d.getEstadoDetalle() != EstadoReserva.CANCELADA)
        .map(DetalleReserva::getCostoEstadia)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  public Optional<Comprobante> principal(Reserva r) {
    return cuentas.findByClaveIdempotencia(clavePrincipal(r));
  }

  public String clavePrincipal(Reserva r) {
    return clavePrincipal(r.getIdReserva());
  }

  private static String clavePrincipal(Long id) {
    return "CUENTA-R-" + id;
  }

  public Map<Long, ResumenCuentaReservaDTO> resumirReservas(Map<Long, List<DetalleReserva>> detallesPorReserva) {
    var ids = detallesPorReserva.keySet();
    var principales = cuentas.clavesExistentes(ids.stream().map(CuentaReservaGestion::clavePrincipal).toList());
    var documentos = cuentas.totalesCuentasReserva(ids).stream()
        .collect(Collectors.groupingBy(ComprobanteRepository.TotalesCuenta::getIdReserva));
    var cargosCancelados = lineas.cargosCancelados(ids).stream().collect(Collectors.toMap(
        l -> l.getComprobante().getReserva().getIdReserva(), this::total, BigDecimal::add));
    var resumen = new HashMap<Long, ResumenCuentaReservaDTO>();
    detallesPorReserva.forEach((id, detalles) -> {
      var alojamiento = alojamiento(detalles);
      boolean tienePrincipal = principales.contains(clavePrincipal(id));
      BigDecimal total = tienePrincipal ? BigDecimal.ZERO : alojamiento;
      BigDecimal pagado = BigDecimal.ZERO;
      BigDecimal saldoAlojamiento = tienePrincipal ? null : alojamiento;
      for (var doc : documentos.getOrDefault(id, List.of())) {
        total = total.add(doc.getTotal());
        pagado = pagado.add(doc.getPagado());
        if (saldoAlojamiento == null && doc.getIdVenta() == null) {
          saldoAlojamiento = doc.getTotal().subtract(doc.getPagado());
        }
      }
      var ajuste = cargosCancelados.getOrDefault(id, BigDecimal.ZERO);
      total = total.subtract(ajuste);
      var devuelto = devuelto(detalles);
      pagado = pagado.subtract(devuelto);
      resumen.put(id, new ResumenCuentaReservaDTO(total, pagado, total.subtract(pagado),
          saldoAlojamiento == null ? BigDecimal.ZERO : saldoAlojamiento.subtract(ajuste).add(devuelto)));
    });
    return resumen;
  }

  public BigDecimal pagado(Comprobante c) {
    return pagos.findByComprobanteIdComprobante(c.getIdComprobante()).stream()
        .filter(p -> p.getVerificado() == 1)
        .map(Pago::getMonto)
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .subtract(c.getReserva() == null ? BigDecimal.ZERO : devuelto(c.getReserva().getDetalles()));
  }

  public static BigDecimal devuelto(List<DetalleReserva> detalles) {
    return detalles.stream().map(DetalleReserva::getMontoDevuelto).filter(Objects::nonNull)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  public BigDecimal cargosCancelados(Comprobante c) {
    if (c.getReserva() == null || c.getEstadoComprobante() == EstadoComprobante.BORRADOR) return BigDecimal.ZERO;
    return lineas.findByComprobanteIdComprobanteOrderByNumeroLinea(c.getIdComprobante()).stream()
        .filter(l -> l.getDetalleReserva() != null && l.getDetalleReserva().getEstadoDetalle() == EstadoReserva.CANCELADA)
        .map(this::total).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  public BigDecimal saldo(Comprobante c) {
    return c.getTotalPagar().subtract(cargosCancelados(c)).subtract(pagado(c));
  }

  public TurnoCaja turno(Long id, Long usuario) {
    var t =
        turnos
            .bloquear(id)
            .orElseThrow(() -> new RecursoNoEncontradoException("El turno no existe"));
    em.refresh(t);
    exigir(
        t.getEstadoSesion() == 1 && t.getFechaCierre() == null,
        "El turno de caja debe estar abierto");
    exigir(t.getUsuario().getIdUsuario().equals(usuario), "El turno pertenece a otro usuario");
    return t;
  }

  public TurnoCaja turnoOperador(Long usuario) {
    var abiertos =
        turnos.findByUsuarioIdUsuarioAndEstadoSesionAndFechaCierreIsNullOrderByIdTurno(usuario, 1);
    exigir(
        abiertos.size() == 1,
        "El operador debe tener exactamente un turno abierto de caja");
    return turno(abiertos.getFirst().getIdTurno(), usuario);
  }

  public Comprobante principal(Reserva r, TurnoCaja turno) {
    return principal(r)
        .orElseGet(
            () -> {
              var c = nueva(turno, clavePrincipal(r));
              c.setReserva(r);
              c.setCliente(r.getCliente());
              cuentas.saveAndFlush(c);
              int n = 0;
              for (var d :
                  detallesReserva.findByReservaIdReservaOrderByIdDetalleReserva(r.getIdReserva())) {
                if (d.getEstadoDetalle() == EstadoReserva.CANCELADA) continue;
                var l =
                    base(
                        c,
                        ++n,
                        "Alojamiento habitación " + d.getHabitacion().getNumeroCuarto(),
                        BigDecimal.ONE,
                        d.getCostoEstadia(),
                        BigDecimal.ZERO);
                l.setDetalleReserva(d);
                lineas.save(l);
              }
              recalcular(c);
              return c;
            });
  }

  private Comprobante nueva(TurnoCaja turno, String clave) {
    var tipo =
        tipos
            .findByCodigo("CUENTA")
            .orElseThrow(
                () -> new ReglaNegocioException("Falta configurar el tipo CUENTA de desarrollo"));
    var serie =
        series.findById(
            new pe.com.rebolsysBackend.entity.id.SerieComprobanteId(
                tipo.getIdTipoComprobante(), "CTA"));
    exigir(serie.isPresent(), "Falta configurar la serie CTA de desarrollo");
    var c = new Comprobante();
    c.setTipoComprobante(tipo);
    c.setSerie("CTA");
    c.setTurnoCaja(turno);
    c.setClaveIdempotencia(clave);
    c.setEstadoComprobante(EstadoComprobante.BORRADOR);
    c.setSubtotal(BigDecimal.ZERO);
    c.setIgv(BigDecimal.ZERO);
    c.setTotalPagar(BigDecimal.ZERO);
    return c;
  }

  public Comprobante cuentaVenta(Venta v) {
    return cuentas
        .findByClaveIdempotencia("CUENTA-V-" + v.getIdVenta())
        .orElseThrow(() -> new ReglaNegocioException("El consumo no tiene cuenta separada"));
  }

  public Comprobante cuentaVenta(Venta v, TurnoCaja turno) {
    exigir(v.getEstadoVenta() == EstadoVenta.CONFIRMADA, "Confirma la venta antes de cobrarla");
    var existente = cuentas.findByClaveIdempotencia("CUENTA-V-" + v.getIdVenta());
    if (existente.isPresent()) return existente.get();
    var c = nueva(turno, "CUENTA-V-" + v.getIdVenta());
    c.setVenta(v);
    c.setCliente(v.getClienteComprador());
    cuentas.saveAndFlush(c);
    int n = 0;
    for (var d : v.getDetalles()) {
      var l = base(c, ++n, d.getDescripcion(), BigDecimal.valueOf(d.getCantidad()), d.getValorUnitario(), d.getIgv());
      l.setDetalleVenta(d);
      lineas.save(l);
    }
    recalcular(c);
    return c;
  }

  public void validarVenta(Venta v) {
    if (v.getDetalleReserva() != null)
      bloquear(v.getDetalleReserva().getReserva().getIdReserva());
  }

  public Comprobante registrarVenta(Venta v, Long operador) {
    validarVenta(v);
    // Conservar los cargos históricos ya consolidados sin trasladarlos ni duplicarlos.
    if (lineas.existsByDetalleVentaVentaIdVenta(v.getIdVenta()))
      return cuentas.findByClaveIdempotencia("CUENTA-V-" + v.getIdVenta())
          .orElseGet(() -> principal(v.getDetalleReserva().getReserva()).orElseThrow());
    return cuentaVenta(v, turnoOperador(operador));
  }

  public void cargarConsumo(Venta venta, TurnoCaja turno) {
    var reserva = venta.getDetalleReserva().getReserva();
    var cuenta = principal(reserva, turno);
    exigir(cuenta.getEstadoComprobante() == EstadoComprobante.BORRADOR,
        "La cuenta de alojamiento ya fue emitida. Registra el consumo en una cuenta separada");
    int numero = lineas.findByComprobanteIdComprobanteOrderByNumeroLinea(cuenta.getIdComprobante()).stream()
        .mapToInt(ComprobanteDetalle::getNumeroLinea).max().orElse(0);
    for (var detalle : venta.getDetalles()) {
      var linea = base(cuenta, ++numero, detalle.getDescripcion(),
          BigDecimal.valueOf(detalle.getCantidad()), detalle.getValorUnitario(), detalle.getIgv());
      linea.setDetalleVenta(detalle);
      lineas.save(linea);
    }
    recalcular(cuenta);
  }

  public void validarEmisionFinal(Reserva reserva) {
    exigir(detallesReserva.findByReservaIdReservaOrderByIdDetalleReserva(reserva.getIdReserva()).stream()
        .allMatch(d -> d.getEstadoDetalle() == EstadoReserva.FINALIZADA
            || d.getEstadoDetalle() == EstadoReserva.CANCELADA),
        "El comprobante de la reserva se emite al completar la última salida");
    validarSalida(reserva);
  }

  private ComprobanteDetalle base(
      Comprobante c,
      int n,
      String descripcion,
      BigDecimal cantidad,
      BigDecimal precio,
      BigDecimal igv) {
    exigir(n <= 9999, "La cuenta alcanzó el máximo de líneas");
    var l = new ComprobanteDetalle();
    l.setComprobante(c);
    l.setNumeroLinea(n);
    l.setDescripcion(descripcion);
    l.setCantidad(cantidad);
    l.setValorUnitario(precio);
    l.setIgv(igv);
    return l;
  }

  public BigDecimal total(ComprobanteDetalle l) {
    return l.getCantidad()
        .multiply(l.getValorUnitario())
        .setScale(2, RoundingMode.HALF_UP)
        .add(l.getIgv());
  }

  public void recalcular(Comprobante c) {
    var ls = lineas.findByComprobanteIdComprobanteOrderByNumeroLinea(c.getIdComprobante());
    var total = ls.stream().map(this::total).reduce(BigDecimal.ZERO, BigDecimal::add);
    var igv = ls.stream().map(ComprobanteDetalle::getIgv).reduce(BigDecimal.ZERO, BigDecimal::add);
    exigir(
        total.compareTo(new BigDecimal("9999999999.99")) <= 0,
        "El total de la cuenta supera el máximo");
    c.setSubtotal(total.subtract(igv));
    c.setIgv(igv);
    c.setTotalPagar(total);
    cuentas.saveAndFlush(c);
  }

  public void validarSalida(Reserva r) {
    var pendientes = cuentas.cuentasReserva(r.getIdReserva()).stream()
        .filter(c -> c.getVenta() != null && saldo(c).signum() != 0)
        .map(c -> "V" + c.getVenta().getIdVenta()).toList();
    exigir(pendientes.isEmpty(), "Liquida las cuentas de venta antes del check-out: " + String.join(", ", pendientes));
    exigir(principal(r).map(this::saldo).orElseGet(() -> alojamiento(r)).signum() == 0,
        "Liquida el saldo de alojamiento y consumos antes del check-out");
  }

  public boolean salidaLiquidada(Reserva r) {
    var principal = principal(r);
    if (principal.map(this::saldo).orElseGet(() -> alojamiento(r)).signum() != 0) return false;
    return cuentas.cuentasReserva(r.getIdReserva()).stream()
        .filter(c -> c.getVenta() != null)
        .allMatch(c -> saldo(c).signum() == 0);
  }

  public void cancelarDetalle(DetalleReserva d, Long operador) {
    var principal = principal(d.getReserva());
    if (principal.isEmpty()) return;
    var c = principal.get();
    var cargos = lineas.findByDetalleReservaIdDetalleReserva(d.getIdDetalleReserva());
    var monto = pagado(c).max(BigDecimal.ZERO).min(d.getCostoEstadia());
    if (monto.signum() > 0) {
      var turno = turnoOperador(operador);
      var tipo = movimientos.findByNombreMovimiento(TipoMovimientoCaja.DEVOLUCION_RESERVA)
          .orElseThrow(() -> new ReglaNegocioException("Falta configurar el movimiento de devolución de reservas"));
      exigir(tipo.getSigno() == -1, "La devolución de reserva debe estar configurada como salida de caja");
      var flujo = new FlujoCaja();
      flujo.setTurnoCaja(turno);
      flujo.setTipoMovimientoCaja(tipo);
      flujo.setMonto(monto);
      flujo.setClaveRegistro("DEV-RES-" + d.getIdDetalleReserva());
      flujo.setConcepto("Devolución de reserva R" + d.getReserva().getIdReserva()
          + " · habitación " + d.getHabitacion().getNumeroCuarto());
      flujos.save(flujo);
      d.setMontoDevuelto(monto);
    }
    if (c.getEstadoComprobante() == EstadoComprobante.BORRADOR) {
      lineas.deleteAll(cargos);
      lineas.flush();
      recalcular(c);
    }
  }
}
