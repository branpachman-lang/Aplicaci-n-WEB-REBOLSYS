package pe.com.rebolsysBackend.service.impl.mantenimiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.com.rebolsysBackend.service.impl.comun.ReglasPropuesta;
import pe.com.rebolsysBackend.dto.mantenimiento.ItemAsignacionDTO;
import pe.com.rebolsysBackend.entity.Habitacion;
import pe.com.rebolsysBackend.entity.Item;
import pe.com.rebolsysBackend.entity.MovimientoInventario;
import pe.com.rebolsysBackend.entity.TipoItem;
import pe.com.rebolsysBackend.entity.TipoMovimientoAlmacen;
import pe.com.rebolsysBackend.entity.Usuario;
import pe.com.rebolsysBackend.enums.EstadoConservacion;
import pe.com.rebolsysBackend.enums.EstadoItem;
import pe.com.rebolsysBackend.exception.ReglaNegocioException;
import pe.com.rebolsysBackend.repository.mantenimiento.HabitacionRepository;
import pe.com.rebolsysBackend.repository.limpieza.InformeRevisionRepository;
import pe.com.rebolsysBackend.repository.mantenimiento.ItemRepository;
import pe.com.rebolsysBackend.repository.mantenimiento.MovimientoInventarioRepository;
import pe.com.rebolsysBackend.repository.mantenimiento.TipoItemRepository;

@ExtendWith(MockitoExtension.class)
class MovimientoMobiliarioTest {
  @Mock EntityManager entityManager;
  @Mock ItemRepository items;
  @Mock TipoItemRepository tipos;
  @Mock HabitacionRepository habitaciones;
  @Mock MovimientoInventarioRepository movimientos;
  @Mock ReglasPropuesta reglas;
  @Mock InformeRevisionRepository informes;
  @InjectMocks ItemServiceImpl servicio;

  @Test
  void asignaElMobiliarioYRegistraLaHabitacionEnElMovimiento() {
    var item = itemActivo();
    var habitacion = new Habitacion();
    habitacion.setIdHabitacion(23L);
    habitacion.setNumeroCuarto("204");
    var operador = new Usuario();
    operador.setIdUsuario(7L);
    when(items.findById(4L)).thenReturn(Optional.of(item));
    when(items.bloquear(4L)).thenReturn(Optional.of(item));
    when(habitaciones.bloquear(23L)).thenReturn(Optional.of(habitacion));
    when(habitaciones.getReferenceById(23L)).thenReturn(habitacion);
    when(reglas.usuario(7L)).thenReturn(operador);
    when(reglas.tipo("ASIG_ITEM", 0))
        .thenReturn(new TipoMovimientoAlmacen(1L, "ASIG_ITEM", "Asignar ítem", 0));

    var respuesta = servicio.asignar(4L,
        new ItemAsignacionDTO("asigna-lampara", 7L, 23L, EstadoConservacion.BUENO,
            "Lámpara instalada"));

    assertThat(respuesta.idHabitacion()).isEqualTo(23L);
    assertThat(respuesta.estadoItem()).isEqualTo(EstadoItem.EN_HABITACION);
    var registro = ArgumentCaptor.forClass(MovimientoInventario.class);
    verify(movimientos).saveAndFlush(registro.capture());
    assertThat(registro.getValue().getHabitacion()).isSameAs(habitacion);
    assertThat(registro.getValue().getStockResultante()).isNull();
  }

  @Test
  void devuelveMovimientosDeMobiliarioSinSaldoDeProducto() {
    var operador = new Usuario();
    operador.setIdUsuario(7L);
    var registro = new MovimientoInventario();
    registro.setIdMovimiento(9L);
    registro.setItem(itemActivo());
    registro.setUsuario(operador);
    registro.setTipoMovimientoAlmacen(
        new TipoMovimientoAlmacen(1L, "ASIG_ITEM", "Asignar ítem", 0));
    registro.setCantidad(1);
    registro.setObservacion("Lámpara instalada [" + "a".repeat(64) + "]");
    when(items.existsById(4L)).thenReturn(true);
    when(movimientos.findByItemIdItemOrderByIdMovimientoDesc(4L)).thenReturn(List.of(registro));

    assertThat(servicio.movimientos(4L)).singleElement().satisfies(respuesta -> {
      assertThat(respuesta.stockResultante()).isNull();
      assertThat(respuesta.observacion()).isEqualTo("Lámpara instalada");
    });
  }

  @Test
  void impideDarDeBajaMobiliarioAunAsignado() {
    var item = itemActivo();
    item.setEstadoItem(EstadoItem.EN_HABITACION);
    when(items.bloquear(4L)).thenReturn(Optional.of(item));

    assertThatThrownBy(() -> servicio.delete(4L))
        .isInstanceOf(ReglaNegocioException.class)
        .hasMessageContaining("Retira el ítem");

    assertThat(item.getEstadoItem()).isEqualTo(EstadoItem.EN_HABITACION);
    verify(items, never()).saveAndFlush(any(Item.class));
    verifyNoInteractions(movimientos);
  }

  @Test
  void conservaElRegistroAlDarDeBajaMobiliarioSinAsignar() {
    var item = itemActivo();
    when(items.bloquear(4L)).thenReturn(Optional.of(item));

    servicio.delete(4L);

    assertThat(item.getEstadoItem()).isEqualTo(EstadoItem.INACTIVO);
    verify(items).saveAndFlush(item);
  }

  private Item itemActivo() {
    var item = new Item();
    item.setIdItem(4L);
    item.setNombreItem("Lámpara de lectura");
    item.setTipoItem(new TipoItem(3L, "Iluminación"));
    return item;
  }
}
