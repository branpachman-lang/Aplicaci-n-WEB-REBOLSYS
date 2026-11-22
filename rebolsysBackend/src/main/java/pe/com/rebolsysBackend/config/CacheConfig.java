package pe.com.rebolsysBackend.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import pe.com.rebolsysBackend.dto.CatalogoItemResponseDTO;
import pe.com.rebolsysBackend.dto.CategoriaResponseDTO;
import pe.com.rebolsysBackend.dto.TipoHabitacionResponseDTO;
import pe.com.rebolsysBackend.dto.TipoItemResponseDTO;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@EnableCaching(order = Ordered.HIGHEST_PRECEDENCE)
public class CacheConfig {
  public static final String CATEGORIAS = "categorias";
  public static final String TIPOS_HABITACION = "tipos-habitacion";
  public static final String TIPOS_ITEM = "tipos-item";
  public static final String REFERENCIAS = "referencias";

  @Bean
  public CacheManager cacheManager(RedisConnectionFactory conexion,
      @Value("${app.cache.habilitada:true}") boolean habilitada,
      @Value("${app.cache.prefijo:rebolsys:dev:v1:}") String prefijo,
      @Value("${app.cache.ttl-catalogos:10m}") Duration ttlCatalogos,
      @Value("${app.cache.ttl-referencias:30m}") Duration ttlReferencias) {
    if (!habilitada) return new NoOpCacheManager();
    if (prefijo.isBlank() || ttlCatalogos.isNegative() || ttlCatalogos.isZero()
        || ttlReferencias.isNegative() || ttlReferencias.isZero()) {
      throw new IllegalArgumentException("La caché requiere prefijo y tiempos de expiración positivos");
    }
    var base = RedisCacheConfiguration.defaultCacheConfig()
        .disableCachingNullValues().prefixCacheNameWith(prefijo).entryTtl(ttlCatalogos);
    var configuraciones = Map.of(
        CATEGORIAS, lista(base, CategoriaResponseDTO.class),
        TIPOS_HABITACION, lista(base, TipoHabitacionResponseDTO.class),
        TIPOS_ITEM, lista(base, TipoItemResponseDTO.class),
        REFERENCIAS, lista(base.entryTtl(ttlReferencias), CatalogoItemResponseDTO.class));
    // Spring Data Redis puede diferir las escrituras con Lettuce. Aquí deben
    // terminar antes de responder, especialmente una invalidación tras commit.
    var escritor = RedisCacheWriter.create(conexion, opciones -> opciones
        .batchStrategy(BatchStrategies.scan(100)).immediateWrites().collectStatistics());
    var manager = new RedisCacheManager(escritor, base, false, configuraciones) {
      @Override
      protected Cache decorateCache(Cache cache) {
        // El manejo de fallos queda dentro del decorador transaccional: también
        // protege las invalidaciones ejecutadas DESPUÉS de confirmar Oracle.
        return super.decorateCache(new CacheTolerante(cache));
      }
    };
    manager.setTransactionAware(true);
    return manager;
  }

  private RedisCacheConfiguration lista(RedisCacheConfiguration base, Class<?> elemento) {
    var mapper = JsonMapper.builder().build();
    var tipo = mapper.getTypeFactory().constructCollectionType(List.class, elemento);
    return base.serializeValuesWith(SerializationPair.fromSerializer(
        new JacksonJsonRedisSerializer<>(mapper, tipo)));
  }
}
