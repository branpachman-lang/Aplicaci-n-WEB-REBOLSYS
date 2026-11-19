package pe.com.rebolsysBackend.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pe.com.rebolsysBackend.dto.SesionActualDTO;
import pe.com.rebolsysBackend.repository.*;

@Service
@RequiredArgsConstructor
public class SesionOperacionService {
  public static final String ATRIBUTO = "rebolsys.sesion.actual";
  private final TurnoCajaRepository turnos;
  private final FormaPagoRepository pagos;
  private final CredencialesService credenciales;
  private final AccesosService accesos;
  public void limpiarCache() {
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos) {
      atributos.getRequest().removeAttribute(ATRIBUTO);
    }
  }

  @Transactional(readOnly = true)
  public SesionActualDTO actual() {
    var atributos = RequestContextHolder.getRequestAttributes();
    var request = atributos instanceof ServletRequestAttributes a ? a.getRequest() : null;
    if (request != null && request.getAttribute(ATRIBUTO) instanceof SesionActualDTO actual) return actual;
    var autenticacion = SecurityContextHolder.getContext().getAuthentication();
    if (autenticacion != null && autenticacion.getPrincipal() instanceof CredencialesService.Principal principal) {
      var detalle = autenticacion.getDetails() instanceof UsuarioDetailsService.Detalle validado
          && principal.equals(validado.principal()) ? validado : cargarDetalle(principal);
      var perfil = detalle.perfil();
      var actual = detalle.requiereCambio()
          ? new SesionActualDTO(principal.idUsuario(), detalle.nombre(), "ACTIVA", null, null, true, false,
              java.util.List.of(), java.util.List.of())
          : new SesionActualDTO(principal.idUsuario(), detalle.nombre(), "ACTIVA", turno(principal.idUsuario()), efectivo(),
              false, perfil.gerente(), perfil.roles(), perfil.permisos());
      if (request != null) request.setAttribute(ATRIBUTO, actual);
      return actual;
    }
    throw new AuthenticationCredentialsNotFoundException("Inicia sesión para continuar");
  }

  public static SesionActualDTO enPeticion() {
    var atributos = RequestContextHolder.getRequestAttributes();
    if (atributos instanceof ServletRequestAttributes req
        && req.getRequest().getAttribute(ATRIBUTO) instanceof SesionActualDTO actual) return actual;
    return null;
  }

  private UsuarioDetailsService.Detalle cargarDetalle(CredencialesService.Principal principal) {
    var cuenta = credenciales.vigente(principal);
    var perfil = cuenta.requiereCambio() ? null : accesos.perfil(principal.idUsuario());
    return new UsuarioDetailsService.Detalle(principal, cuenta.usuario().getUsername(), java.util.List.of(),
        cuenta.requiereCambio(), perfil);
  }

  public static void exigirPermisoEnPeticion(String permiso) {
    var actual = enPeticion();
    if (actual != null && !actual.tiene(permiso)) {
      throw new AccessDeniedException("Tu usuario no tiene acceso a esta función");
    }
  }

  private Long turno(Long idUsuario) {
    var abiertos = turnos.findByUsuarioIdUsuarioAndEstadoSesionAndFechaCierreIsNullOrderByIdTurno(idUsuario, 1);
    return abiertos.size() == 1 ? abiertos.getFirst().getIdTurno() : null;
  }

  private Long efectivo() {
    return pagos.findAll().stream().filter(p -> "EFECTIVO".equalsIgnoreCase(p.getNombrePago().trim()))
        .findFirst().map(p -> p.getIdFormaPago()).orElse(null);
  }
}
