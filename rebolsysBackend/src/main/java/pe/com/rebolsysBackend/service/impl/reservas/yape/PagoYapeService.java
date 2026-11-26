package pe.com.rebolsysBackend.service.impl.reservas.yape;

import static pe.com.rebolsysBackend.service.impl.comun.ReglasPropuesta.exigir;
import static pe.com.rebolsysBackend.service.impl.reservas.yape.PagoYapeDatos.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import pe.com.rebolsysBackend.dto.caja.cobros.PagoCuentaDTO;
import pe.com.rebolsysBackend.entity.Reserva;
import pe.com.rebolsysBackend.enums.EstadoReserva;
import pe.com.rebolsysBackend.enums.EstadoUsuario;
import pe.com.rebolsysBackend.exception.RecursoNoEncontradoException;
import pe.com.rebolsysBackend.repository.caja.TurnoCajaRepository;
import pe.com.rebolsysBackend.repository.caja.cobros.FormaPagoRepository;
import pe.com.rebolsysBackend.repository.caja.cobros.PagoRepository;
import pe.com.rebolsysBackend.repository.reservas.DetalleReservaRepository;
import pe.com.rebolsysBackend.security.SesionOperacionService;
import pe.com.rebolsysBackend.service.impl.caja.cobros.CuentaReservaGestion;
import pe.com.rebolsysBackend.service.service.caja.cobros.CuentaCobroService;

/** Orquesta los cobros con Yape sobre la cuenta principal de una reserva. */
@Service
public class PagoYapeService {
  private static final String FORMA = "YAPE";
  private final PagoYapeRegistro registro;
  private final PagoYapeProveedor proveedor;
  private final CuentaReservaGestion gestion;
  private final CuentaCobroService cobros;
  private final FormaPagoRepository formas;
  private final PagoRepository pagos;
  private final TurnoCajaRepository turnos;
  private final DetalleReservaRepository detalles;
  private final SesionOperacionService sesion;
  private final TransactionTemplate tx;

  PagoYapeService(
      PagoYapeRegistro registro,
      PagoYapeProveedor proveedor,
      CuentaReservaGestion gestion,
      CuentaCobroService cobros,
      FormaPagoRepository formas,
      PagoRepository pagos,
      TurnoCajaRepository turnos,
      DetalleReservaRepository detalles,
      SesionOperacionService sesion,
      PlatformTransactionManager manager) {
    this.registro = registro;
    this.proveedor = proveedor;
    this.gestion = gestion;
    this.cobros = cobros;
    this.formas = formas;
    this.pagos = pagos;
    this.turnos = turnos;
    this.detalles = detalles;
    this.sesion = sesion;
    this.tx = new TransactionTemplate(manager);
  }

  public Configuracion configuracion() {
    return proveedor.configuracion();
  }

  public Respuesta ultimo(long idReserva) {
    exigir(proveedor.habilitado(), "Los pagos con Yape están deshabilitados");
    return registro.ultimo(idReserva).map(Intento::respuesta).orElse(null);
  }

  public Respuesta iniciar(
      long idReserva, UUID clave, BigDecimal importeSolicitado, String telefono, String otp) {
    exigir(proveedor.habilitado(), "Los pagos con Yape están deshabilitados");
    String telefonoLimpio = telefono == null ? "" : telefono.trim();
    String otpLimpio = otp == null ? "" : otp.trim();
    exigir(telefonoLimpio.matches("[0-9]{9}"), "El número de celular Yape debe tener 9 dígitos");
    exigir(otpLimpio.matches("[0-9]{6}"), "El código de aprobación Yape debe tener 6 dígitos");
    exigir(
        importeSolicitado != null && importeSolicitado.signum() > 0 && importeSolicitado.scale() <= 2,
        "El importe a cobrar debe ser positivo con hasta dos decimales");
    BigDecimal importe = importeSolicitado.setScale(2, RoundingMode.UNNECESSARY);
    String telefonoEnmascarado = enmascararTelefono(telefonoLimpio);

    var actual = sesion.actual();
    var anterior = registro.buscar(clave, idReserva).orElse(null);
    if (anterior != null) {
      return recuperar(anterior, actual.idUsuario(), importe, telefonoEnmascarado);
    }

    proveedor.validarVendedor();
    var inicio =
        tx.execute(
            estadoTx -> {
              var reserva = gestion.bloquear(idReserva);
              var previo = registro.buscar(clave, idReserva).orElse(null);
              if (previo != null) {
                recuperar(previo, actual.idUsuario(), importe, telefonoEnmascarado);
                return new Inicio(previo, false);
              }
              exigir(
                  !registro.tienePendientes(idReserva),
                  "Consulta el intento Yape existente antes de iniciar otro");
              exigir(activa(reserva), "La reserva debe tener una estancia confirmada o en curso");
              BigDecimal saldoActual = saldo(reserva);
              exigir(saldoActual.signum() > 0, "La reserva no tiene saldo pendiente por cobrar");
              exigir(
                  importe.compareTo(saldoActual) <= 0,
                  "El importe supera el saldo pendiente de la reserva");
              var turno = gestion.turnoOperador(actual.idUsuario());
              forma();
              var nuevo =
                  new Intento(
                      clave,
                      idReserva,
                      actual.idUsuario(),
                      turno.getIdTurno(),
                      importe,
                      telefonoEnmascarado,
                      proveedor.modo(),
                      proveedor.ambiente(),
                      Estado.ENVIANDO,
                      null,
                      null,
                      "Enviando solicitud a Yape",
                      Instant.now());
              registro.crear(nuevo);
              return new Inicio(nuevo, true);
            });
    if (!inicio.nuevo()) return inicio.intento().respuesta();
    var resultado = proveedor.enviar(inicio.intento(), telefonoLimpio, otpLimpio);
    return aplicar(inicio.intento(), resultado);
  }

  public Respuesta conciliar(long idReserva, UUID clave) {
    exigir(proveedor.habilitado(), "Los pagos con Yape están deshabilitados");
    var i =
        registro
            .buscar(clave, idReserva)
            .orElseThrow(
                () -> new RecursoNoEncontradoException("El intento Yape no existe en esta reserva"));
    if (terminal(i)) return i.respuesta();
    exigir(i.modo() == proveedor.modo(), "Vuelve al modo de Yape que inició este intento");
    return aplicar(i, proveedor.consultar(i));
  }

  public void notificar(
      String orden, String requestId, String firma, boolean eventoValido, boolean liveMode) {
    exigir(proveedor.habilitado(), "Los pagos con Yape están deshabilitados");
    proveedor.validarNotificacion(orden, requestId, firma);
    boolean ambienteEsperadoLive = proveedor.ambiente() == Ambiente.PRODUCCION;
    if (!eventoValido || liveMode != ambienteEsperadoLive) {
      throw new AccessDeniedException(
          "La notificación no corresponde al ambiente de Yape configurado");
    }
    var resultado = proveedor.consultarOrden(orden, proveedor.ambiente());
    if (resultado == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
    registro
        .buscar(resultado.referencia())
        .filter(i -> i.modo() == Modo.MERCADO_PAGO && i.ambiente() == proveedor.ambiente())
        .ifPresent(i -> aplicar(i, resultado));
  }

  public void desvincularPagoEliminado(long idPago) {
    var intento = registro.buscarPorPago(idPago).orElse(null);
    if (intento == null) return;
    exigir(
        intento.modo() == Modo.SIMULADO,
        "Los cobros acreditados vía Mercado Pago en línea no pueden eliminarse desde caja;"
            + " gestiona la devolución en Mercado Pago");
    registro.actualizar(
        intento,
        Estado.RECHAZADO,
        intento.ordenProveedor(),
        null,
        "Pago simulado eliminado en caja");
  }

  private Respuesta aplicar(Intento previo, Resultado resultado) {
    return tx.execute(
        estadoTx -> {
          var reserva = gestion.bloquear(previo.idReserva());
          var i = registro.buscar(previo.claveOperacion(), previo.idReserva()).orElseThrow();
          if (terminal(i)) return i.respuesta();
          String orden = i.ordenProveedor();
          if (resultado == null) {
            return guardar(
                i,
                Estado.PENDIENTE,
                orden,
                null,
                "Confirmación pendiente; consulta el estado para conciliar");
          }
          if (!i.claveOperacion().equals(resultado.referencia())
              || (orden != null && !orden.equals(resultado.orden()))) {
            return guardar(
                i,
                Estado.REVISION,
                orden,
                null,
                "La referencia o identificador de orden no coincide");
          }
          orden = resultado.orden();
          if (resultado.importe().compareTo(i.importe()) != 0) {
            return guardar(
                i, Estado.REVISION, orden, null, "El importe devuelto no coincide con el solicitado");
          }
          if (resultado.estado() != Estado.APLICADO) {
            return guardar(i, resultado.estado(), orden, null, resultado.mensaje());
          }
          var turno = turnos.bloquear(i.idTurno()).orElseThrow();
          boolean abierto =
              turno.getEstadoSesion() == 1
                  && turno.getFechaCierre() == null
                  && turno.getUsuario().getIdUsuario().equals(i.idUsuario())
                  && turno.getUsuario().getEstadoUsuario() == EstadoUsuario.ACTIVO;
          if (!abierto || !activa(reserva) || saldo(reserva).compareTo(i.importe()) < 0) {
            return guardar(
                i,
                Estado.REVISION,
                orden,
                null,
                "El saldo, estancia o turno cambió antes de acreditar el abono");
          }
          var cuenta = gestion.principal(reserva, turno);
          String clavePago = "YAPE-" + i.claveOperacion();
          cobros.pagar(
              cuenta,
              new PagoCuentaDTO(
                  clavePago,
                  i.idUsuario(),
                  i.idTurno(),
                  forma(),
                  i.importe(),
                  resultado.pagoProveedor()));
          Long idPago = pagos.findByClaveIdempotencia(clavePago).orElseThrow().getIdPago();
          return guardar(i, Estado.APLICADO, orden, idPago, resultado.mensaje());
        });
  }

  private boolean activa(Reserva r) {
    return detalles.findByReservaIdReservaOrderByIdDetalleReserva(r.getIdReserva()).stream()
        .anyMatch(
            d ->
                d.getEstadoDetalle() == EstadoReserva.CONFIRMADA
                    || d.getEstadoDetalle() == EstadoReserva.EN_CURSO);
  }

  private BigDecimal saldo(Reserva r) {
    return gestion.principal(r).map(gestion::saldo).orElseGet(() -> gestion.alojamiento(r));
  }

  private Long forma() {
    var medios = formas.findByNombrePagoIgnoreCase(FORMA);
    exigir(medios.size() == 1, "Aplica V23__pago_yape.sql para configurar la forma de pago YAPE");
    return medios.getFirst().getIdFormaPago();
  }

  private static Respuesta recuperar(
      Intento i, long usuario, BigDecimal importe, String telefonoEnmascarado) {
    exigir(
        i.idUsuario() == usuario
            && i.importe().compareTo(importe) == 0
            && i.telefonoEnmascarado().equals(telefonoEnmascarado),
        "La clave ya corresponde a otra solicitud Yape");
    return i.respuesta();
  }

  private static boolean terminal(Intento i) {
    return i.estado() == Estado.APLICADO
        || i.estado() == Estado.RECHAZADO
        || i.estado() == Estado.REVISION;
  }

  private Respuesta guardar(
      Intento i, Estado estado, String orden, Long idPago, String mensaje) {
    registro.actualizar(i, estado, orden, idPago, mensaje);
    return new Respuesta(
        i.claveOperacion(),
        i.importe(),
        i.telefonoEnmascarado(),
        i.modo(),
        i.ambiente(),
        estado,
        orden,
        idPago,
        mensaje,
        i.creado());
  }

  private record Inicio(Intento intento, boolean nuevo) {}
}
