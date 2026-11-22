package pe.com.rebolsysBackend.service.impl;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import pe.com.rebolsysBackend.repository.DetalleReservaRepository;

class CheckoutAutomaticoTest {
  @Test void recorreMasDeUnLoteAunqueElPrimeroTengaDeudasSinSaltarRegistros() {
    var detalles = mock(DetalleReservaRepository.class);
    var checkout = mock(CheckoutVencidoService.class);
    when(detalles.vencidas(any(), eq(0L), any())).thenReturn(LongStream.rangeClosed(1, 100).boxed().toList());
    when(detalles.vencidas(any(), eq(100L), any())).thenReturn(List.of(101L, 102L));
    when(detalles.vencidas(any(), eq(102L), any())).thenReturn(List.of());
    when(checkout.finalizar(eq(101L), any())).thenThrow(new IllegalStateException("Fallo aislado"));
    new CheckoutAutomatico(detalles, checkout).ejecutar();
    verify(checkout, times(102)).finalizar(anyLong(), any());
    verify(checkout).finalizar(eq(102L), any());
    verify(detalles, times(3)).vencidas(any(), anyLong(), argThat((Pageable p) -> p.getPageSize() == 100 && p.getPageNumber() == 0));
  }

  @Test void noEjecutaCheckoutsCuandoNoHayCandidatas() {
    var detalles = mock(DetalleReservaRepository.class);
    var checkout = mock(CheckoutVencidoService.class);
    when(detalles.vencidas(any(), anyLong(), any())).thenReturn(List.of());
    new CheckoutAutomatico(detalles, checkout).ejecutar();
    verifyNoInteractions(checkout);
  }
}
