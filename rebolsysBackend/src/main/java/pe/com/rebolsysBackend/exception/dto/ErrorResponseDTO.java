package pe.com.rebolsysBackend.exception.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.Map;
import org.slf4j.MDC;

public record ErrorResponseDTO(
    LocalDateTime timestamp,
    int status,
    String error,
    String message,
    String path,
    Map<String, String> validationErrors,
    @JsonInclude(JsonInclude.Include.NON_NULL) String requestId) {
  public ErrorResponseDTO(
      LocalDateTime timestamp,
      int status,
      String error,
      String message,
      String path,
      Map<String, String> validationErrors) {
    this(timestamp, status, error, message, path, validationErrors, MDC.get("requestId"));
  }
}
