package pe.com.rebolsysBackend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Correlaciona cada petición HTTP mediante MDC, cabecera X-Request-Id y bitácora de tiempos. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
  public static final String CABECERA = "X-Request-Id";
  public static final String CLAVE_MDC = "requestId";
  private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String requestId = UUID.randomUUID().toString().substring(0, 8);
    MDC.put(CLAVE_MDC, requestId);
    response.setHeader(CABECERA, requestId);
    long inicio = System.nanoTime();
    try {
      chain.doFilter(request, response);
    } finally {
      long duracionMs = (System.nanoTime() - inicio) / 1_000_000L;
      String uri = request.getRequestURI();
      if (!uri.startsWith("/actuator") && !"/api/health".equals(uri)) {
        log.info("{} {} -> {} ({} ms)", request.getMethod(), uri, response.getStatus(), duracionMs);
      }
      MDC.remove(CLAVE_MDC);
    }
  }
}
