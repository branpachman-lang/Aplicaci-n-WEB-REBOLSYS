package pe.com.rebolsysBackend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
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
  private final LinkedHashMap<String, ContadorVentana> contadores = new LinkedHashMap<>();

  private static final class ContadorVentana {
    final Instant vence;
    int intentos = 1;

    ContadorVentana(Instant vence) {
      this.vence = vence;
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
        || !RUTA_LOGIN.equals(request.getRequestURI().substring(request.getContextPath().length()));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String ip = request.getRemoteAddr();
    String claveIp = ip == null || ip.isBlank() ? "desconocida" : ip;
    long esperaSegundos = registrarYCalcularEspera(claveIp);
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

  private synchronized long registrarYCalcularEspera(String claveIp) {
    Instant ahora = reloj.instant();
    var contador = contadores.get(claveIp);
    if (contador != null && contador.vence.isAfter(ahora)) {
      if (contador.intentos >= maxIntentosIp) {
        return segundosRestantes(ahora, contador.vence);
      }
      contador.intentos++;
      return 0;
    }
    contadores.remove(claveIp);
    if (contadores.size() >= LIMITE_ENTRADAS) {
      var primera = contadores.firstEntry();
      if (primera.getValue().vence.isAfter(ahora)) {
        return segundosRestantes(ahora, primera.getValue().vence);
      }
      contadores.pollFirstEntry();
    }
    contadores.put(claveIp, new ContadorVentana(ahora.plus(ventana)));
    return 0;
  }

  private long segundosRestantes(Instant ahora, Instant vence) {
    Duration restante = Duration.between(ahora, vence);
    return restante.getSeconds() + (restante.getNano() == 0 ? 0 : 1);
  }
}
