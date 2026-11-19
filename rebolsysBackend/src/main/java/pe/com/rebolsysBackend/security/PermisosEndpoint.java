package pe.com.rebolsysBackend.security;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.HandlerMethod;

/** Metadatos para el catálogo y OpenAPI. Spring Security evalúa la autorización. */
public final class PermisosEndpoint {
  private static final Pattern PERMISO = Pattern.compile("'([A-Z_]+:[A-Z_]+)'");

  private PermisosEndpoint() {}

  public static PreAuthorize regla(HandlerMethod handler) {
    var regla = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), PreAuthorize.class);
    return regla != null ? regla
        : AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), PreAuthorize.class);
  }

  public static List<String> permisos(HandlerMethod handler) {
    var regla = regla(handler);
    return regla == null ? List.of()
        : PERMISO.matcher(regla.value()).results().map(m -> m.group(1)).toList();
  }
}
