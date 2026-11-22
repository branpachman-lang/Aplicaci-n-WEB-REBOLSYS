package pe.com.rebolsysBackend.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class CacheToleranteTest {
  @Test
  void permiteDesactivarLaCacheSinAccederARedis() {
    var conexion = mock(RedisConnectionFactory.class);
    var manager = new CacheConfig().cacheManager(conexion, false, "rebolsys:test:",
        Duration.ofMinutes(10), Duration.ofMinutes(30));
    assertThat(manager).isInstanceOf(NoOpCacheManager.class);
    manager.getCache("categorias").put("todos", List.of());
    assertThat(manager.getCache("categorias").get("todos")).isNull();
    verifyNoInteractions(conexion);
  }

  @Test
  void evitaReintentosPorCadaPeticionYDescartaElCatalogoViejoAntesDeRecuperarse() {
    var memoria = spy(new ConcurrentMapCache("categorias"));
    memoria.put("todos", "anterior");
    var reloj = new AtomicLong();
    var cache = new CacheTolerante(memoria, reloj::get, Duration.ofSeconds(5));
    doThrow(new IllegalStateException("Redis caído")).when(memoria).get("todos");

    assertThat(cache.get("todos")).isNull();
    cache.clear(); // Hubo una escritura mientras Redis estaba caído.
    cache.put("todos", "nuevo");
    assertThat(cache.get("todos")).isNull();
    verify(memoria, times(1)).get("todos");
    verify(memoria, never()).clear();

    doCallRealMethod().when(memoria).get("todos");
    reloj.set(Duration.ofSeconds(5).toNanos());
    assertThat(cache.get("todos")).isNull();
    verify(memoria).clear();
    cache.put("todos", "nuevo");
    assertThat(cache.get("todos", String.class)).isEqualTo("nuevo");
  }

  @Test
  void unaInvalidacionFallidaDespuesDelCommitNoConvierteLaOperacionEnError() {
    Cache redis = mock(Cache.class);
    when(redis.getName()).thenReturn("categorias");
    doThrow(new IllegalStateException("Redis caído")).when(redis).clear();
    var cache = new TransactionAwareCacheDecorator(new CacheTolerante(redis));
    TransactionSynchronizationManager.initSynchronization();
    try {
      cache.clear();
      verify(redis, never()).clear();
      assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations()
          .forEach(TransactionSynchronization::afterCommit)).doesNotThrowAnyException();
      verify(redis).clear();
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void noGuardaNullNiOcultaUnErrorDelNegocio() {
    var memoria = spy(new ConcurrentMapCache("categorias"));
    var cache = new CacheTolerante(memoria);
    cache.put("todos", null);
    verify(memoria, never()).put(any(), any());
    var causa = new IllegalArgumentException("Consulta rechazada");
    assertThatThrownBy(() -> cache.get("todos", () -> { throw causa; }))
        .isInstanceOf(Cache.ValueRetrievalException.class).hasCause(causa);
  }
}
