package pe.com.rebolsysBackend.service.impl.caja.cobros;

import static pe.com.rebolsysBackend.service.impl.comun.ReglasPropuesta.exigir;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.com.rebolsysBackend.dto.caja.cobros.CobroInmediatoDTO;
import pe.com.rebolsysBackend.dto.caja.cobros.PagoCuentaDTO;
import pe.com.rebolsysBackend.service.impl.comun.ReglasPropuesta;
import pe.com.rebolsysBackend.entity.Comprobante;
import pe.com.rebolsysBackend.entity.TurnoCaja;
import pe.com.rebolsysBackend.service.service.caja.cobros.CuentaCobroService;
import pe.com.rebolsysBackend.repository.caja.cobros.PagoRepository;
import java.util.Objects;

/** Participa de la transacción de registro: cualquier fallo revierte también reserva, venta y stock. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class CobroInmediatoService {
  private final ReglasPropuesta reglas;
  private final CuentaReservaGestion gestion;
  private final CuentaCobroService cobros;
  private final PagoRepository pagos;

  public boolean recuperar(Comprobante cuenta, CobroInmediatoDTO cobro, Long usuario) {
    if (gestion.saldo(cuenta).signum() != 0) return false;
    reglas.validar(cobro);
    exigir(cobro.monto().compareTo(cuenta.getTotalPagar()) == 0,
        "El pago debe cubrir exactamente el total de la operación");
    if (cuenta.getTotalPagar().signum() == 0) {
      exigir(cuenta.getTurnoCaja().getUsuario().getIdUsuario().equals(usuario),
          "La confirmación corresponde a otro usuario");
      return true;
    }
    var anterior = pagos.findByClaveIdempotencia("COBRO-INMEDIATO-" + cuenta.getIdComprobante());
    if (anterior.isEmpty()) return false; // Cuenta histórica: conservar su flujo de regularización.
    var p = anterior.get();
    exigir(p.getUsuario().getIdUsuario().equals(usuario)
            && p.getFormaPago().getIdFormaPago().equals(cobro.idFormaPago())
            && p.getMonto().compareTo(cobro.monto()) == 0
            && Objects.equals(p.getNumeroOperacion(), ReglasPropuesta.limpiar(cobro.numeroOperacion())),
        "La confirmación ya corresponde a otro pago");
    return true;
  }

  public void cobrar(Comprobante cuenta, CobroInmediatoDTO cobro, Long usuario, TurnoCaja turno) {
    exigir(cobro != null, "Se requiere el pago completo para confirmar la operación");
    reglas.validar(cobro);
    exigir(cobro.monto().compareTo(cuenta.getTotalPagar()) == 0,
        "El pago debe cubrir exactamente el total de la operación");
    // Las cuentas gratuitas se liquidan sin crear pagos de importe cero (el DDL los prohíbe).
    if (cuenta.getTotalPagar().signum() > 0)
      cobros.pagar(cuenta, new PagoCuentaDTO("COBRO-INMEDIATO-" + cuenta.getIdComprobante(),
          usuario, turno.getIdTurno(), cobro.idFormaPago(), cobro.monto(), cobro.numeroOperacion()));
    exigir(gestion.saldo(cuenta).signum() == 0, "La operación debe quedar completamente pagada");
  }
}
