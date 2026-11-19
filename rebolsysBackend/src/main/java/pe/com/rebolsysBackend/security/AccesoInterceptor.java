package pe.com.rebolsysBackend.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import pe.com.rebolsysBackend.controller.GestionAccesosController;

@RequiredArgsConstructor
public class AccesoInterceptor implements HandlerInterceptor {
  private final SesionOperacionService sesion;

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (!(handler instanceof HandlerMethod metodo)) return true;
    var reglas = Arrays.asList(AccesosService.regla(metodo));
    if (reglas.contains(Acceso.PUBLICO) || reglas.contains(Acceso.CIERRE)) return true;
    var actual = sesion.actual();
    if (reglas.contains(Acceso.SESION)) return true;
    if (actual.requiereCambio()) throw new AccessDeniedException("Cambia la contraseña temporal para continuar");
    if (reglas.contains(Acceso.GERENTE)) {
      if ("ACTIVA".equals(actual.modo()) && actual.gerente()) return true;
      throw new AccessDeniedException("Esta función requiere el rol GERENTE");
    }
    boolean pruebaOperativa = "PRUEBA".equals(actual.modo())
        && metodo.getBeanType() != GestionAccesosController.class;
    if (!reglas.isEmpty() && (reglas.contains(Acceso.CATALOGOS) || actual.gerente()
        || pruebaOperativa || reglas.stream().anyMatch(actual.permisos()::contains))) return true;
    throw new AccessDeniedException("Tu usuario no tiene acceso a esta función");
  }
}
