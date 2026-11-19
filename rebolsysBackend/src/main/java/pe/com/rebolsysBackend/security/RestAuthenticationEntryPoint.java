package pe.com.rebolsysBackend.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
  private final RespuestaErrorSeguridad errores;

  @Override
  public void commence(HttpServletRequest request, HttpServletResponse response,
      AuthenticationException exception) throws IOException {
    response.setHeader("WWW-Authenticate", "Bearer");
    errores.escribir(request, response, HttpStatus.UNAUTHORIZED,
        "El acceso venció o no es válido. Inicia sesión nuevamente");
  }
}
