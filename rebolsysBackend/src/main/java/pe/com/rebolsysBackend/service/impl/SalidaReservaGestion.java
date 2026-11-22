package pe.com.rebolsysBackend.service.impl;

import static pe.com.rebolsysBackend.service.impl.ReglasPropuesta.exigir;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pe.com.rebolsysBackend.entity.DetalleReserva;
import pe.com.rebolsysBackend.enums.EstadoHabitacion;
import pe.com.rebolsysBackend.enums.EstadoReserva;

/** Regla compartida de salida; el llamador mantiene bloqueadas reserva, habitación y estancia. */
@Component
@RequiredArgsConstructor
public class SalidaReservaGestion {
  private final CuentaReservaGestion cuenta;

  public void finalizar(DetalleReserva detalle, LocalDateTime fecha) {
    exigir(detalle.getEstadoDetalle() == EstadoReserva.EN_CURSO,
        "Solo puede salir una estancia en curso");
    exigir(detalle.getFechaIngresoReal() != null && !fecha.isBefore(detalle.getFechaIngresoReal()),
        "La salida no puede ser anterior al ingreso real");
    cuenta.validarSalida(detalle.getReserva());
    detalle.setFechaSalidaReal(fecha);
    detalle.setEstadoDetalle(EstadoReserva.FINALIZADA);
    detalle.getHabitacion().setEstadoHabitacion(EstadoHabitacion.POR_LIMPIAR);
  }
}
