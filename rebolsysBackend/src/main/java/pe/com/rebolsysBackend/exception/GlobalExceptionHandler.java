package pe.com.rebolsysBackend.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import pe.com.rebolsysBackend.security.SesionOperacionService;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import pe.com.rebolsysBackend.exception.dto.ErrorResponseDTO;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    @ExceptionHandler({AuthenticationException.class, AccessDeniedException.class})
    public ResponseEntity<ErrorResponseDTO> handleSecurity(RuntimeException ex, HttpServletRequest request) {
        var estado = ex instanceof AuthenticationException ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN;
        String mensaje = ex.getMessage();
        if (ex instanceof AuthorizationDeniedException) {
            var sesion = SesionOperacionService.enPeticion();
            mensaje = sesion != null && sesion.requiereCambio()
                ? "Cambia la contraseña temporal para continuar" : "Tu usuario no tiene acceso a esta función";
        }
        return ResponseEntity.status(estado).header("Cache-Control", "no-store").body(new ErrorResponseDTO(
            LocalDateTime.now(ZONA_LIMA), estado.value(), estado.getReasonPhrase(), mensaje, request.getRequestURI(), null));
    }

    @ExceptionHandler(ServicioExternoException.class)
    public ResponseEntity<ErrorResponseDTO> handleExternalService(
        ServicioExternoException ex,
        HttpServletRequest request
    ) {
        ErrorResponseDTO error = new ErrorResponseDTO(
            LocalDateTime.now(ZONA_LIMA),
            ex.getStatus().value(),
            ex.getStatus().getReasonPhrase(),
            ex.getMessage(),
            request.getRequestURI(),
            null
        );
        return ResponseEntity.status(ex.getStatus()).body(error);
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorResponseDTO> handleNotFound(
        RecursoNoEncontradoException ex,
        HttpServletRequest request
    ) {
        return notFound(ex.getMessage(), request);
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ErrorResponseDTO> handleMissingRoute(HttpServletRequest request) {
        return notFound("El recurso solicitado no existe", request);
    }

    private ResponseEntity<ErrorResponseDTO> notFound(String mensaje, HttpServletRequest request) {
        var estado = HttpStatus.NOT_FOUND;
        return ResponseEntity.status(estado).body(new ErrorResponseDTO(
            LocalDateTime.now(ZONA_LIMA), estado.value(), estado.getReasonPhrase(),
            mensaje, request.getRequestURI(), null));
    }

    @ExceptionHandler(ReglaNegocioException.class)
    public ResponseEntity<ErrorResponseDTO> handleBusinessRule(ReglaNegocioException ex, HttpServletRequest request) {
        ErrorResponseDTO error = new ErrorResponseDTO(
            LocalDateTime.now(ZONA_LIMA),
            HttpStatus.CONFLICT.value(),
            "Conflict",
            ex.getMessage(),
            request.getRequestURI(),
            null
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidation(
        MethodArgumentNotValidException ex,
        HttpServletRequest request
    ) {
        Map<String, String> validationErrors = new LinkedHashMap<>();
        ex
            .getBindingResult()
            .getFieldErrors()
            .forEach(error -> validationErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ex
            .getBindingResult()
            .getGlobalErrors()
            .forEach(error -> validationErrors.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));
        ErrorResponseDTO error = new ErrorResponseDTO(
            LocalDateTime.now(ZONA_LIMA),
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            "Existen errores de validación",
            request.getRequestURI(),
            validationErrors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponseDTO> handleConstraintValidation(
        ConstraintViolationException ex,
        HttpServletRequest request
    ) {
        Map<String, String> validationErrors = new LinkedHashMap<>();
        ex
            .getConstraintViolations()
            .forEach(error -> validationErrors.putIfAbsent(error.getPropertyPath().toString(), error.getMessage()));
        ErrorResponseDTO error = new ErrorResponseDTO(
            LocalDateTime.now(ZONA_LIMA),
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            "Existen errores de validación",
            request.getRequestURI(),
            validationErrors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponseDTO> handleParameterValidation(
        HandlerMethodValidationException ex,
        HttpServletRequest request
    ) {
        Map<String, String> validationErrors = new LinkedHashMap<>();
        ex
            .getParameterValidationResults()
            .forEach(result -> {
                String parameter = result.getMethodParameter().getParameterName();
                result
                    .getResolvableErrors()
                    .forEach(error -> validationErrors.putIfAbsent(parameter, error.getDefaultMessage()));
            });
        ErrorResponseDTO error = new ErrorResponseDTO(
            LocalDateTime.now(ZONA_LIMA),
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            "Existen errores de validación",
            request.getRequestURI(),
            validationErrors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDTO> handleDatabaseConstraint(
        DataIntegrityViolationException ex,
        HttpServletRequest request
    ) {
        log.warn("Conflicto de integridad en {} {}", request.getMethod(), request.getRequestURI());
        String message = "Los datos entran en conflicto con un registro existente o relacionado. Revisa la operación.";
        ErrorResponseDTO error = new ErrorResponseDTO(
            LocalDateTime.now(ZONA_LIMA),
            HttpStatus.CONFLICT.value(),
            "Conflict",
            message,
            request.getRequestURI(),
            null
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponseDTO> handleConcurrentUpdate(
        PessimisticLockingFailureException ex,
        HttpServletRequest request
    ) {
        ErrorResponseDTO error = new ErrorResponseDTO(
            LocalDateTime.now(ZONA_LIMA),
            HttpStatus.CONFLICT.value(),
            "Conflict",
            "El registro está siendo modificado por otra operación",
            request.getRequestURI(),
            null
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDTO> handleInvalidJson(
        HttpMessageNotReadableException ex,
        HttpServletRequest request
    ) {
        ErrorResponseDTO error = new ErrorResponseDTO(
            LocalDateTime.now(ZONA_LIMA),
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            "La solicitud no tiene un formato válido",
            request.getRequestURI(),
            null
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponseDTO> handleInvalidParameter(
        MethodArgumentTypeMismatchException ex,
        HttpServletRequest request
    ) {
        Map<String, String> validationErrors = new LinkedHashMap<>();
        validationErrors.put(ex.getName(), "El parámetro " + ex.getName() + " no tiene un formato válido");
        ErrorResponseDTO error = new ErrorResponseDTO(
            LocalDateTime.now(ZONA_LIMA),
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            "Existen errores de validación",
            request.getRequestURI(),
            validationErrors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponseDTO> handleMissingParameter(
        MissingServletRequestParameterException ex, HttpServletRequest request
    ) {
        var error = new ErrorResponseDTO(LocalDateTime.now(ZONA_LIMA), HttpStatus.BAD_REQUEST.value(),
            "Bad Request", "Falta un parámetro obligatorio", request.getRequestURI(),
            Map.of(ex.getParameterName(), "El parámetro " + ex.getParameterName() + " es obligatorio"));
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponseDTO> handleMethodNotAllowed(
        HttpRequestMethodNotSupportedException ex,
        HttpServletRequest request
    ) {
        ErrorResponseDTO error = new ErrorResponseDTO(
            LocalDateTime.now(ZONA_LIMA),
            HttpStatus.METHOD_NOT_ALLOWED.value(),
            "Method Not Allowed",
            "El método HTTP no está permitido para este recurso",
            request.getRequestURI(),
            null
        );
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).headers(ex.getHeaders()).body(error);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponseDTO> handleUnsupportedMediaType(
        HttpMediaTypeNotSupportedException ex, HttpServletRequest request
    ) {
        var status = HttpStatus.UNSUPPORTED_MEDIA_TYPE;
        var mensaje = "El tipo de contenido no es compatible. Tipos admitidos: " + ex.getSupportedMediaTypes();
        return ResponseEntity.status(status).headers(ex.getHeaders()).body(new ErrorResponseDTO(
            LocalDateTime.now(ZONA_LIMA), status.value(), status.getReasonPhrase(), mensaje,
            request.getRequestURI(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Error no controlado en {}", request.getRequestURI(), ex);
        ErrorResponseDTO error = new ErrorResponseDTO(
            LocalDateTime.now(ZONA_LIMA),
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "Internal Server Error",
            "Ocurrió un error interno",
            request.getRequestURI(),
            null
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    @ExceptionHandler({MaxUploadSizeExceededException.class, MissingServletRequestPartException.class})
    public ResponseEntity<ErrorResponseDTO> handleMultipart(Exception ex, HttpServletRequest request) {
        boolean excedeLimite = ex instanceof MaxUploadSizeExceededException;
        var estado = excedeLimite ? HttpStatus.CONTENT_TOO_LARGE : HttpStatus.BAD_REQUEST;
        var mensaje = excedeLimite ? "El archivo supera el tamaño permitido" : "Falta el archivo adjunto";
        var error = new ErrorResponseDTO(
            LocalDateTime.now(ZONA_LIMA), estado.value(), estado.getReasonPhrase(), mensaje, request.getRequestURI(), null
        );
        return ResponseEntity.status(estado).body(error);
    }
}
