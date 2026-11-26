package pe.com.rebolsysBackend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Limita intentos consecutivos de inicio de sesión por IP origen para mitigar fuerza bruta. */
@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {
  private static final String RUTA_LOGIN = "/api/v1/sesion/iniciar";
  private static final int LIMITE_ENTRADAS = 2048;

  private final RespuestaErrorSeguridad errores;
  private final boolean habilitado;
  private final int maxIntentosIp;
  private final Duration ventana;
  private final Clock reloj;
  private final Map<String, ContadorVentana> contadores = new ConcurrentHashMap<>();

  private static final class ContadorVentana {
    Instant inicio;
    int intentos;

    ContadorVentana(Instant inicio) {
      this.inicio = inicio;
      this.intentos = 0;
    }
  }

  @Autowired
  public LoginRateLimitFilter(
      RespuestaErrorSeguridad errores,
      @Value("${app.seguridad.rate-limit-login.habilitado:true}") boolean habilitado,
      @Value("${app.seguridad.rate-limit-login.max-intentos-ip:30}") int maxIntentosIp,
      @Value("${app.seguridad.rate-limit-login.ventana:1m}") Duration ventana) {
    this(errores, habilitado, maxIntentosIp, ventana, Clock.systemUTC());
  }

  LoginRateLimitFilter(
      RespuestaErrorSeguridad errores,
      boolean habilitado,
      int maxIntentosIp,
      Duration ventana,
      Clock reloj) {
    this.errores = errores;
    this.habilitado = habilitado;
    this.maxIntentosIp = Math.max(1, maxIntentosIp);
    this.ventana = ventana.isNegative() || ventana.isZero() ? Duration.ofMinutes(1) : ventana;
    this.reloj = reloj;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !habilitado
        || !HttpMethod.POST.matches(request.getMethod())
        || !RUTA_LOGIN.equals(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Instant ahora = reloj.instant();
    if (contadores.size() >= LIMITE_ENTRADAS) {
      contadores.entrySet().removeIf(e -> !e.getValue().inicio.plus(ventana).isAfter(ahora));
    }
    String claveIp = request.getRemoteAddr() == null || request.getRemoteAddr().isBlank()
        ? "desconocida"
        : request.getRemoteAddr();
    long esperaSegundos = registrarYCalcularEspera(claveIp, ahora);
    if (esperaSegundos > 0) {
      response.setHeader("Retry-After", Long.toString(esperaSegundos));
      errores.escribir(
          request,
          response,
          HttpStatus.TOO_MANY_REQUESTS,
          "Demasiados intentos de inicio de sesión. Espera antes de volver a intentarlo");
      return;
    }
    chain.doFilter(request, response);
  }

  private long registrarYCalcularEspera(String claveIp, Instant ahora) {
    ContadorVentana contador = contadores.computeIfAbsent(claveIp, k -> new ContadorVentana(ahora));
    synchronized (contador) {
      if (!contador.inicio.plus(ventana).isAfter(ahora)) {
        contador.inicio = ahora;
        contador.intentos = 1;
        return 0;
      }
      if (contador.intentos >= maxIntentosIp) {
        return Math.max(1, Duration.between(ahora, contador.inicio.plus(ventana)).toSeconds());
      }
      contador.intentos++;
      return 0;
    }
  }
}
