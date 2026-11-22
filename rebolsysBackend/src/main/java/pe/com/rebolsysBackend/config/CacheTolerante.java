package pe.com.rebolsysBackend.config;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;

/** Una caída de Redis no convierte una operación confirmada en Oracle en un error. */
final class CacheTolerante implements Cache {
  private static final Logger log = LoggerFactory.getLogger(CacheTolerante.class);
  private final Cache cache;
  private final LongSupplier reloj;
  private final long pausa;
  private boolean limpiezaPendiente;
  private boolean pausaActiva;
  private long reintento;

  CacheTolerante(Cache cache) {
    this(cache, System::nanoTime, Duration.ofSeconds(5));
  }

  CacheTolerante(Cache cache, LongSupplier reloj, Duration pausa) {
    this.cache = cache;
    this.reloj = reloj;
    this.pausa = pausa.toNanos();
  }

  @Override public String getName() { return cache.getName(); }
  @Override public Object getNativeCache() { return cache.getNativeCache(); }

  @Override
  public ValueWrapper get(Object key) {
    return ejecutar(() -> cache.get(key), null);
  }

  @Override
  public <T> T get(Object key, Class<T> type) {
    var valor = get(key);
    if (valor == null) return null;
    Object resultado = valor.get();
    if (type != null && resultado != null && !type.isInstance(resultado)) {
      throw new IllegalStateException("El valor de caché no coincide con el tipo solicitado");
    }
    @SuppressWarnings("unchecked") T convertido = (T) resultado;
    return convertido;
  }

  @Override
  public <T> T get(Object key, Callable<T> loader) {
    var existente = get(key);
    if (existente != null) {
      @SuppressWarnings("unchecked") T valor = (T) existente.get();
      return valor;
    }
    final T valor;
    try { valor = loader.call(); }
    catch (Exception ex) { throw new ValueRetrievalException(key, loader, ex); }
    put(key, valor);
    return valor;
  }

  @Override
  public void put(Object key, Object value) {
    if (value != null) ejecutar(() -> { cache.put(key, value); return null; }, null);
  }

  @Override
  public void evict(Object key) {
    ejecutar(() -> { cache.evict(key); return null; }, null);
  }

  @Override
  public synchronized void clear() {
    limpiezaPendiente = true;
    ejecutar(() -> null, null);
  }

  private synchronized <T> T ejecutar(Supplier<T> operacion, T alternativo) {
    long ahora = reloj.getAsLong();
    if (pausaActiva && ahora - reintento < 0) return alternativo;
    pausaActiva = false;
    try {
      // Antes de reutilizar Redis se descarta el catálogo que pudo cambiar
      // durante la caída. Nunca se limpian claves ajenas a esta caché.
      if (limpiezaPendiente) {
        cache.clear();
        limpiezaPendiente = false;
      }
      return operacion.get();
    } catch (RuntimeException ex) {
      limpiezaPendiente = true;
      pausaActiva = true;
      reintento = ahora + pausa;
      log.warn("Caché {} no disponible ({}); se continúa sin caché",
          getName(), ex.getClass().getSimpleName());
      return alternativo;
    }
  }
}
