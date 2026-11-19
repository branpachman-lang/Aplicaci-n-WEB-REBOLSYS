package pe.com.rebolsysBackend.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import pe.com.rebolsysBackend.security.PermisosEndpoint;
import pe.com.rebolsysBackend.security.SesionOperacionService;

/** Prepara el actor y turno de la petición para reglas de negocio y auditoría; no autoriza accesos. */
@Configuration
@RequiredArgsConstructor
public class SesionWebConfig implements WebMvcConfigurer {
  private final SesionOperacionService sesion;

  @Override
  public void addInterceptors(InterceptorRegistry registro) {
    registro.addInterceptor(new HandlerInterceptor() {
      @Override
      public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod metodo)) return true;
        var regla = PermisosEndpoint.regla(metodo);
        if (regla != null && !"permitAll()".equals(regla.value())
            && !"/api/v1/sesion/cerrar".equals(request.getRequestURI())) sesion.actual();
        return true;
      }
    }).addPathPatterns("/api/v1/**").order(-100);
  }
}
