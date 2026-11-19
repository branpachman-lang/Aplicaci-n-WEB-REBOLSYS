package pe.com.rebolsysBackend.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import pe.com.rebolsysBackend.exception.dto.ErrorResponseDTO;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class RespuestaErrorSeguridad {
  private final ObjectMapper json;

  public void escribir(HttpServletRequest request, HttpServletResponse response,
      HttpStatus estado, String mensaje) throws IOException {
    response.setStatus(estado.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    response.setHeader("Cache-Control", "no-store");
    json.writeValue(response.getOutputStream(), new ErrorResponseDTO(
        LocalDateTime.now(ZoneId.of("America/Lima")), estado.value(), estado.getReasonPhrase(),
        mensaje, request.getRequestURI(), null));
  }
}
