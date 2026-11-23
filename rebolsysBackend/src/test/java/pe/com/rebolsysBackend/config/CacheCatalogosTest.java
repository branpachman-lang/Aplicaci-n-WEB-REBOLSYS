package pe.com.rebolsysBackend.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pe.com.rebolsysBackend.dto.*;
import pe.com.rebolsysBackend.dto.SistemaDTO.ParametrosComerciales;
import pe.com.rebolsysBackend.entity.*;
import pe.com.rebolsysBackend.repository.*;
import pe.com.rebolsysBackend.service.impl.*;
import pe.com.rebolsysBackend.service.service.*;

class CacheCatalogosTest {
  @Configuration(proxyBeanMethods = false)
  @EnableCaching(order = Ordered.HIGHEST_PRECEDENCE)
  @EnableTransactionManagement
  static class Infraestructura {}

  private AnnotationConfigApplicationContext contexto;
  private final CategoriaRepository categorias = mock(CategoriaRepository.class);
  private final TipoHabitacionRepository habitaciones = mock(TipoHabitacionRepository.class);
  private final TipoItemRepository items = mock(TipoItemRepository.class);
  private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
  private final TurnoCajaRepository turnos = mock(TurnoCajaRepository.class);
  private final TipoDocumentoRepository documentos = mock(TipoDocumentoRepository.class);
  private final FormaPagoRepository pagos = mock(FormaPagoRepository.class);
  private final TipoMovimientoCajaRepository gastos = mock(TipoMovimientoCajaRepository.class);
  private final AbstractPlatformTransactionManager transacciones = new AbstractPlatformTransactionManager() {
    @Override protected Object doGetTransaction() { return new Object(); }
    @Override protected boolean isExistingTransaction(Object tx) {
      return TransactionSynchronizationManager.isActualTransactionActive();
    }
    @Override protected void doBegin(Object tx, TransactionDefinition d) {}
    @Override protected void doCommit(DefaultTransactionStatus s) {}
    @Override protected void doRollback(DefaultTransactionStatus s) {}
  };

  @BeforeEach
  void preparar() {
    contexto = new AnnotationConfigApplicationContext();
    contexto.registerBean("cacheManager", CacheManager.class, () ->
        new TransactionAwareCacheManagerProxy(new ConcurrentMapCacheManager(
            CacheConfig.CATEGORIAS, CacheConfig.TIPOS_HABITACION,
            CacheConfig.TIPOS_ITEM, CacheConfig.REFERENCIAS)));
    contexto.registerBean("transactionManager", AbstractPlatformTransactionManager.class, () -> transacciones);
    contexto.registerBean(CategoriaRepository.class, () -> categorias);
    contexto.registerBean(TipoHabitacionRepository.class, () -> habitaciones);
    contexto.registerBean(TipoItemRepository.class, () -> items);
    contexto.registerBean(UsuarioRepository.class, () -> usuarios);
    contexto.registerBean(TurnoCajaRepository.class, () -> turnos);
    contexto.registerBean(TipoDocumentoRepository.class, () -> documentos);
    contexto.registerBean(FormaPagoRepository.class, () -> pagos);
    contexto.registerBean(TipoMovimientoCajaRepository.class, () -> gastos);
    contexto.registerBean(ProductoRepository.class, () -> mock(ProductoRepository.class));
    contexto.registerBean(HabitacionRepository.class, () -> mock(HabitacionRepository.class));
    contexto.registerBean(ItemRepository.class, () -> mock(ItemRepository.class));
    contexto.registerBean(ReglasPropuesta.class, () -> mock(ReglasPropuesta.class));
    contexto.register(Infraestructura.class, CategoriaServiceImpl.class, TipoHabitacionServiceImpl.class,
        TipoItemServiceImpl.class, CatalogosEstaticosService.class, CatalogoServiceImpl.class);
    contexto.refresh();
    when(categorias.findAllByOrderByNombreCategoriaAsc()).thenReturn(List.of(categoria("Bebidas")));
    when(categorias.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    when(habitaciones.findAll(any(Sort.class))).thenReturn(List.of());
    when(items.findAll(any(Sort.class))).thenReturn(List.of());
  }

  @AfterEach void cerrar() { contexto.close(); }

  @Test
  void reutilizaLaConsultaDeCategorias() {
    var servicio = contexto.getBean(CategoriaService.class);
    servicio.readAll();
    servicio.readAll();
    verify(categorias, times(1)).findAllByOrderByNombreCategoriaAsc();
  }

  @Test
  void crearInvalidaSoloDespuesDeConfirmarLaTransaccionExterna() {
    var servicio = contexto.getBean(CategoriaService.class);
    servicio.readAll();
    new TransactionTemplate(transacciones).executeWithoutResult(tx -> {
      servicio.create(new CategoriaRequestDTO("Snacks"));
      servicio.readAll();
      verify(categorias, times(1)).findAllByOrderByNombreCategoriaAsc();
    });
    servicio.readAll();
    verify(categorias, times(2)).findAllByOrderByNombreCategoriaAsc();
  }

  @Test
  void rollbackNoInvalidaElCatalogo() {
    var servicio = contexto.getBean(CategoriaService.class);
    servicio.readAll();
    new TransactionTemplate(transacciones).executeWithoutResult(tx -> {
      servicio.create(new CategoriaRequestDTO("Snacks"));
      tx.setRollbackOnly();
    });
    servicio.readAll();
    verify(categorias, times(1)).findAllByOrderByNombreCategoriaAsc();
  }

  @Test
  void unaEscrituraRechazadaConservaLaCacheVigente() {
    var servicio = contexto.getBean(CategoriaService.class);
    servicio.readAll();
    when(categorias.existsByNombreCategoria("Bebidas")).thenReturn(true);
    assertThatThrownBy(() -> servicio.create(new CategoriaRequestDTO("Bebidas")))
        .hasMessageContaining("Ya existe");
    servicio.readAll();
    verify(categorias, times(1)).findAllByOrderByNombreCategoriaAsc();
  }

  @Test
  void editarYEliminarCategoriaInvalidanLaLista() {
    var servicio = contexto.getBean(CategoriaService.class);
    when(categorias.findById(1L)).thenReturn(Optional.of(categoria("Bebidas")));
    servicio.readAll();
    servicio.update(1L, new CategoriaRequestDTO("Bebidas frías"));
    servicio.readAll();
    servicio.delete(1L);
    servicio.readAll();
    verify(categorias, times(3)).findAllByOrderByNombreCategoriaAsc();
  }

  @Test
  void cambiarTarifaOCapacidadInvalidaLosTiposDeHabitacion() {
    var tipo = new TipoHabitacion(1L, "Simple", 2, new BigDecimal("80.00"));
    when(habitaciones.bloquear(1L)).thenReturn(Optional.of(tipo));
    when(habitaciones.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    var servicio = contexto.getBean(TipoHabitacionService.class);
    servicio.readAll();
    servicio.actualizarParametros(1L, new ParametrosComerciales(2, new BigDecimal("90.00")));
    servicio.readAll();
    verify(habitaciones, times(2)).findAll(any(Sort.class));
  }

  @Test
  void editarTipoDeMobiliarioInvalidaSuLista() {
    when(items.findById(1L)).thenReturn(Optional.of(new TipoItem(1L, "Mesas")));
    when(items.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    var servicio = contexto.getBean(TipoItemService.class);
    servicio.readAll();
    servicio.update(1L, new TipoItemRequestDTO("Mesas y sillas"));
    servicio.readAll();
    verify(items, times(2)).findAll(any(Sort.class));
  }

  @Test
  void cacheaReferenciasPeroSiempreConsultaUsuariosYTurnos() {
    var servicio = contexto.getBean(CatalogoService.class);
    servicio.catalogos();
    servicio.catalogos();
    verify(documentos, times(1)).findByNombreDocumentoNotOrderByIdTipoDocumento("INTERNO");
    verify(pagos, times(1)).findAll();
    verify(gastos, times(1)).findAll();
    verify(habitaciones, times(1)).findAll(any(Sort.class));
    verify(items, times(1)).findAll(any(Sort.class));
    verify(usuarios, times(2)).findAll();
    verify(turnos, times(2)).findAll();
  }

  private Categoria categoria(String nombre) {
    var categoria = new Categoria();
    categoria.setIdCategoria(1L);
    categoria.setNombreCategoria(nombre);
    return categoria;
  }
}
