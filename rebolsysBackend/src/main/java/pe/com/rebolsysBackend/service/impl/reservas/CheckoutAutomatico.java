package pe.com.rebolsysBackend.service.impl.reservas;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.com.rebolsysBackend.service.impl.comun.ReglasPropuesta;
import pe.com.rebolsysBackend.repository.reservas.DetalleReservaRepository;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.reservas.checkout-automatico.habilitado", havingValue = "true",
    matchIfMissing = true)
public class CheckoutAutomatico {
  private final DetalleReservaRepository detalles;
  private final CheckoutVencidoService checkout;

  @Scheduled(fixedDelayString = "${app.reservas.checkout-automatico.intervalo:60000}",
      initialDelayString = "${app.reservas.checkout-automatico.espera-inicial:10000}")
  public void ejecutar() {
    var ahora = ReglasPropuesta.ahora();
    long desde = 0;
    while (true) {
      var ids = detalles.vencidas(ahora, desde, PageRequest.of(0, 100));
      if (ids.isEmpty()) return;
      for (Long id : ids) {
        try {
          if (checkout.finalizar(id, ahora)) log.info("Checkout automático de estancia {}", id);
        } catch (RuntimeException ex) {
          log.error("No se pudo finalizar automáticamente la estancia {}; se reintentará", id, ex);
        }
      }
      // Avanzar por clave, no por offset: finalizar elimina filas del conjunto de candidatas.
      // Las deudas del primer lote tampoco impiden atender las siguientes estancias.
      desde = ids.getLast();
    }
  }
}
