package pe.com.rebolsysBackend.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.cache.CacheManager;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pe.com.rebolsysBackend.dto.*;
import pe.com.rebolsysBackend.entity.Categoria;
import pe.com.rebolsysBackend.repository.CategoriaRepository;
import pe.com.rebolsysBackend.repository.ProductoRepository;
import pe.com.rebolsysBackend.service.impl.CategoriaServiceImpl;
import pe.com.rebolsysBackend.service.impl.ReglasPropuesta;
import pe.com.rebolsysBackend.service.service.CategoriaService;

/** Redis local real y repositorio controlado: nunca conecta con Oracle. */
@EnabledIfEnvironmentVariable(named = "RUN_REDIS_TESTS", matches = "true")
class RedisCatalogosIntegracionTest {
  private AnnotationConfigApplicationContext contexto;
  private final CategoriaRepository categorias = mock(CategoriaRepository.class);
  private final String prefijo = "rebolsys:test:" + UUID.randomUUID() + ":";
  private CacheManager manager;

  @BeforeEach
  void preparar() {
    contexto = new AnnotationConfigApplicationContext();
    contexto.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance());
    contexto.getEnvironment().getPropertySources().addFirst(new MapPropertySource("redis-test", Map.of(
        "app.cache.habilitada", "true", "app.cache.prefijo", prefijo)));
    contexto.registerBean(RedisConnectionFactory.class, () ->
        new LettuceConnectionFactory("127.0.0.1", Integer.getInteger("test.redis.port", 6380)));
    contexto.registerBean(CategoriaRepository.class, () -> categorias);
    contexto.registerBean(ProductoRepository.class, () -> mock(ProductoRepository.class));
    contexto.registerBean(ReglasPropuesta.class, () -> mock(ReglasPropuesta.class));
    contexto.register(CacheConfig.class, CategoriaServiceImpl.class);
    contexto.refresh();
    manager = contexto.getBean(CacheManager.class);
  }

  @AfterEach
  void cerrar() {
    if (manager != null) manager.getCacheNames().forEach(nombre -> manager.getCache(nombre).clear());
    if (contexto != null) contexto.close();
  }

  @Test
  void reutilizaRedisEInvalidaAlCrearUnaCategoriaSinCambiarElContrato() {
    var categoria = new Categoria();
    categoria.setIdCategoria(1L);
    categoria.setNombreCategoria("Bebidas");
    when(categorias.findAllByOrderByNombreCategoriaAsc()).thenReturn(List.of(categoria));
    when(categorias.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    var servicio = contexto.getBean(CategoriaService.class);
    assertThat(servicio.readAll()).containsExactly(new CategoriaResponseDTO(1L, "Bebidas"));
    assertThat(servicio.readAll()).containsExactly(new CategoriaResponseDTO(1L, "Bebidas"));
    verify(categorias, times(1)).findAllByOrderByNombreCategoriaAsc();
    var redis = new StringRedisTemplate(contexto.getBean(RedisConnectionFactory.class));
    String clave = prefijo + "categorias::todos";
    assertThat(redis.getExpire(clave)).isBetween(1L, 600L);
    assertThat(redis.opsForValue().get(clave)).contains("Bebidas").doesNotContain("@class");
    servicio.create(new CategoriaRequestDTO("Snacks"));
    assertThat(redis.hasKey(clave)).isFalse();
    servicio.readAll();
    verify(categorias, times(2)).findAllByOrderByNombreCategoriaAsc();
  }

  @Test
  void conservaTiposDeRecordsDecimalesYListasEnTodosLosCatalogos() {
    assertThat(TransactionSynchronizationManager.isSynchronizationActive()).as("Sin transacción externa").isFalse();
    var redis = new StringRedisTemplate(contexto.getBean(RedisConnectionFactory.class));
    Map<String, List<?>> ejemplos = Map.of(
        CacheConfig.CATEGORIAS, List.of(new CategoriaResponseDTO(1L, "Bebidas")),
        CacheConfig.TIPOS_HABITACION, List.of(
            new TipoHabitacionResponseDTO(1L, "Matrimonial", 2, new BigDecimal("120.50"))),
        CacheConfig.TIPOS_ITEM, List.of(new TipoItemResponseDTO(1L, "Televisores")),
        CacheConfig.REFERENCIAS, List.of(new CatalogoItemResponseDTO(1L, "DNI", "1")));
    ejemplos.forEach((nombre, lista) -> {
      var cache = manager.getCache(nombre);
      assertThat(cache).as("Manager %s", manager.getClass().getName())
          .isInstanceOf(org.springframework.cache.transaction.TransactionAwareCacheDecorator.class);
      cache.put("prueba", lista);
      assertThat(redis.opsForValue().get(prefijo + nombre + "::prueba"))
          .as("JSON guardado para %s", nombre).isNotNull();
      assertThat(cache.get("prueba")).as("Caché %s", nombre).isNotNull();
      assertThat(cache.get("prueba").get()).isEqualTo(lista);
      cache.put("prueba", null);
      assertThat(cache.get("prueba").get()).isEqualTo(lista);
    });
    assertThat(manager.getCache("no-declarada")).isNull();
  }
}
