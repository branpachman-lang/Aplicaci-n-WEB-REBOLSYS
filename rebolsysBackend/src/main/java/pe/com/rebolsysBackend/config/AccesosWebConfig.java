package pe.com.rebolsysBackend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import pe.com.rebolsysBackend.security.AccesoInterceptor;
import pe.com.rebolsysBackend.security.SesionOperacionService;

@Configuration
@RequiredArgsConstructor
public class AccesosWebConfig implements WebMvcConfigurer {
  private final SesionOperacionService sesion;

  @Override
  public void addInterceptors(InterceptorRegistry registro) {
    registro.addInterceptor(new AccesoInterceptor(sesion)).addPathPatterns("/api/v1/**").order(-100);
  }
}
