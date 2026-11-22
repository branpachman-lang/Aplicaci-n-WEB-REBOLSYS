package pe.com.rebolsysBackend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.com.rebolsysBackend.dto.AjusteStockDTO;
import pe.com.rebolsysBackend.dto.ProductoRequestDTO;
import pe.com.rebolsysBackend.entity.Categoria;
import pe.com.rebolsysBackend.entity.MovimientoInventario;
import pe.com.rebolsysBackend.entity.Producto;
import pe.com.rebolsysBackend.entity.TipoMovimientoAlmacen;
import pe.com.rebolsysBackend.entity.Usuario;
import pe.com.rebolsysBackend.enums.EstadoProducto;
import pe.com.rebolsysBackend.exception.ReglaNegocioException;
import pe.com.rebolsysBackend.repository.CategoriaRepository;
import pe.com.rebolsysBackend.repository.MovimientoInventarioRepository;
import pe.com.rebolsysBackend.repository.ProductoRepository;

@ExtendWith(MockitoExtension.class)
class ProductoConfiguracionTest {
  @Mock EntityManager entityManager;
  @Mock ProductoRepository productos;
  @Mock CategoriaRepository categorias;
  @Mock MovimientoInventarioRepository movimientos;
  @Mock Validator validator;
  @Mock ReglasPropuesta reglas;
  @InjectMocks ProductoServiceImpl servicio;

  private Categoria categoria;
  private Producto producto;

  @BeforeEach
  void prepararCatalogo() {
    categoria = new Categoria();
    categoria.setIdCategoria(3L);
    categoria.setNombreCategoria("Bebidas");
    producto = new Producto();
    producto.setIdProducto(5L);
    producto.setNombreProducto("Agua mineral");
    producto.setPrecioActual(new BigDecimal("4.50"));
    producto.setStock(12);
    producto.setStockMinimo(3);
    producto.setCategoria(categoria);
    producto.setEstadoProducto(EstadoProducto.ACTIVO);
  }

  @Test
  void consultaElCatalogoDeProductosActivos() {
    when(productos.findByEstadoProductoOrderByNombreProductoAsc(EstadoProducto.ACTIVO))
        .thenReturn(List.of(producto));

    assertThat(servicio.catalogo()).singleElement()
        .satisfies(respuesta -> assertThat(respuesta.getNombreProducto()).isEqualTo("Agua mineral"));
  }

  @Test
  void registraElProductoYLaEntradaDeExistenciasIniciales() {
    var solicitud = new ProductoRequestDTO(
        "  Agua mineral  ", new BigDecimal("4.50"), 12, 3, 3L, EstadoProducto.ACTIVO);
    solicitud.setIdUsuario(7L);
    var operador = new Usuario();
    operador.setIdUsuario(7L);
    when(validator.validate(solicitud)).thenReturn(Set.of());
    when(categorias.findById(3L)).thenReturn(Optional.of(categoria));
    when(reglas.bloquearUsuario(7L)).thenReturn(operador);
    when(reglas.tipo("AJ_ENT", 1))
        .thenReturn(new TipoMovimientoAlmacen(1L, "AJ_ENT", "Entrada de inventario", 1));
    when(productos.saveAndFlush(any(Producto.class))).thenAnswer(invocacion -> {
      Producto nuevo = invocacion.getArgument(0);
      nuevo.setIdProducto(5L);
      return nuevo;
    });

    var respuesta = servicio.create(solicitud);

    assertThat(respuesta.getNombreProducto()).isEqualTo("Agua mineral");
    assertThat(respuesta.getStock()).isEqualTo(12);
    var registro = ArgumentCaptor.forClass(MovimientoInventario.class);
    verify(movimientos).saveAndFlush(registro.capture());
    assertThat(registro.getValue().getStockResultante()).isEqualTo(12);
    assertThat(registro.getValue().getUsuario()).isSameAs(operador);
  }

  @Test
  void impideCambiarLasExistenciasDesdeElFormularioDeEdicion() {
    var solicitud = new ProductoRequestDTO(
        "Agua mineral", new BigDecimal("4.50"), 30, 3, 3L, EstadoProducto.ACTIVO);
    when(validator.validate(solicitud)).thenReturn(Set.of());
    when(productos.bloquear(5L)).thenReturn(Optional.of(producto));

    assertThatThrownBy(() -> servicio.update(5L, solicitud))
        .isInstanceOf(ReglaNegocioException.class)
        .hasMessageContaining("operación de ajuste");

    assertThat(producto.getStock()).isEqualTo(12);
    verify(productos, never()).saveAndFlush(any(Producto.class));
  }

  @Test
  void conservaElStockActualSiSoloSeEditaElCatalogo() {
    var solicitud = new ProductoRequestDTO(
        "Agua mineral con gas", new BigDecimal("5.00"), 10, 3, 3L, EstadoProducto.ACTIVO);
    solicitud.setStockAnterior(10);
    when(validator.validate(solicitud)).thenReturn(Set.of());
    when(productos.bloquear(5L)).thenReturn(Optional.of(producto));
    when(categorias.findById(3L)).thenReturn(Optional.of(categoria));

    var respuesta = servicio.update(5L, solicitud);

    assertThat(respuesta.getStock()).isEqualTo(12);
    assertThat(respuesta.getNombreProducto()).isEqualTo("Agua mineral con gas");
    verify(productos).saveAndFlush(producto);
  }

  @Test
  void registraUnaSalidaYDevuelveElSaldoDelProducto() {
    var operador = new Usuario();
    operador.setIdUsuario(7L);
    when(productos.bloquear(5L)).thenReturn(Optional.of(producto));
    when(reglas.usuario(7L)).thenReturn(operador);
    when(reglas.tipo("AJ_SAL", -1))
        .thenReturn(new TipoMovimientoAlmacen(2L, "AJ_SAL", "Salida de inventario", -1));

    var respuesta = servicio.ajustarStock(5L,
        new AjusteStockDTO("ajuste-agua", 7L, 12, 8, "Envases dañados"));

    assertThat(respuesta.getStock()).isEqualTo(8);
    var registro = ArgumentCaptor.forClass(MovimientoInventario.class);
    verify(movimientos).saveAndFlush(registro.capture());
    assertThat(registro.getValue().getCantidad()).isEqualTo(4);
    assertThat(ItemServiceImpl.movimiento(registro.getValue()).stockResultante()).isEqualTo(8);
  }

  @Test
  void retiraElProductoConBajaLogica() {
    when(productos.bloquear(5L)).thenReturn(Optional.of(producto));

    servicio.delete(5L);

    assertThat(producto.getEstadoProducto()).isEqualTo(EstadoProducto.INACTIVO);
    verify(productos).save(producto);
  }
}
