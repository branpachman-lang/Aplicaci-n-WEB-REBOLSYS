package pe.com.rebolsysBackend.service.impl.reservas;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.com.rebolsysBackend.service.impl.caja.cobros.CuentaReservaGestion;
import pe.com.rebolsysBackend.service.impl.comun.ReglasPropuesta;
import pe.com.rebolsysBackend.entity.AuditoriaGeneral;
import pe.com.rebolsysBackend.enums.AccionAuditoria;
import pe.com.rebolsysBackend.enums.EstadoHabitacion;
import pe.com.rebolsysBackend.enums.EstadoReserva;
import pe.com.rebolsysBackend.repository.reservas.DetalleReservaRepository;
import pe.com.rebolsysBackend.repository.mantenimiento.HabitacionRepository;
import pe.com.rebolsysBackend.repository.reservas.ReservaRepository;

@Service
@RequiredArgsConstructor
public class CheckoutVencidoService {
  private final EntityManager em;
  private final DetalleReservaRepository detalles;
  private final ReservaRepository reservas;
  private final HabitacionRepository habitaciones;
  private final CuentaReservaGestion cuenta;
  private final SalidaReservaGestion salida;

  /** Una transacción por estancia evita que un fallo revierta otras salidas del lote. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean finalizar(Long id, LocalDateTime ahora) {
    var previo = detalles.findById(id).orElse(null);
    if (previo == null) return false;
    reservas.bloquear(previo.getReserva().getIdReserva()).orElseThrow();
    var habitacion = habitaciones.bloquear(previo.getHabitacion().getIdHabitacion()).orElseThrow();
    em.refresh(habitacion);
    var detalle = detalles.bloquear(id).orElseThrow();
    em.refresh(detalle);
    // Releer después de los bloqueos: una salida manual u otra instancia pudo adelantarse.
    if (detalle.getEstadoDetalle() != EstadoReserva.EN_CURSO
        || detalle.getFechaSalida().isAfter(ahora)) return false;
    if (!cuenta.salidaLiquidada(detalle.getReserva())) return false;
    ReglasPropuesta.exigir(habitacion.getEstadoHabitacion() == EstadoHabitacion.OCUPADO,
        "La habitación de una estancia en curso debe estar ocupada");
    salida.finalizar(detalle, ahora);
    auditar("DETALLE_RESERVA", id, "EN_CURSO", "FINALIZADA", ahora);
    auditar("HABITACION", habitacion.getIdHabitacion(), "OCUPADO", "POR_LIMPIAR", ahora);
    em.flush();
    return true;
  }

  private void auditar(String tabla, Long id, String anterior, String nuevo, LocalDateTime fecha) {
    var registro = new AuditoriaGeneral();
    registro.setNombreTabla(tabla);
    registro.setClaveRegistro("{\"id\":" + id + "}");
    registro.setAccion(AccionAuditoria.UPDATE);
    registro.setDatosAnteriores(anterior);
    registro.setDatosNuevos("CHECKOUT_AUTOMATICO: " + nuevo);
    registro.setFechaHora(fecha);
    // Operación del sistema: no atribuir la salida al usuario que creó la reserva.
    em.persist(registro);
  }
}
