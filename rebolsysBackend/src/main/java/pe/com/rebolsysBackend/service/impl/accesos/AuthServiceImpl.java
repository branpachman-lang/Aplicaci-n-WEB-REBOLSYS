package pe.com.rebolsysBackend.service.impl.accesos;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import pe.com.rebolsysBackend.dto.accesos.AutenticacionResponseDTO;
import pe.com.rebolsysBackend.dto.accesos.SesionActualDTO;
import pe.com.rebolsysBackend.dto.accesos.CambioPasswordRequestDTO;
import pe.com.rebolsysBackend.dto.accesos.LoginRequestDTO;
import pe.com.rebolsysBackend.security.*;
import pe.com.rebolsysBackend.service.service.accesos.AuthService;


@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
  private final AuthenticationManager authenticationManager;
  private final UsuarioDetailsService usuarios;
  private final CredencialesService credenciales;
  private final JwtService jwt;
  private final PoliticaSeguridadService politica;
  private final SesionOperacionService sesion;

  @Override
  public AutenticacionResponseDTO iniciar(LoginRequestDTO datos) {
    var autenticacion = authenticationManager.authenticate(
        UsernamePasswordAuthenticationToken.unauthenticated(datos.usuario(), datos.contrasena()));
    return autenticar((UsuarioDetailsService.Detalle) autenticacion.getDetails());
  }

  @Override
  public SesionActualDTO actual() {
    return sesion.actual();
  }

  @Override
  public AutenticacionResponseDTO cambiar(CambioPasswordRequestDTO datos, JwtService.Verificado tokenActual) {
    var autenticacion = SecurityContextHolder.getContext().getAuthentication();
    if (autenticacion == null || !(autenticacion.getPrincipal() instanceof CredencialesService.Principal principal)) {
      throw new AuthenticationCredentialsNotFoundException("Inicia sesión para continuar");
    }
    var nuevo = credenciales.cambiar(principal, datos.actual(), datos.nueva());
    if (tokenActual != null) jwt.revocar(tokenActual);
    return autenticar(usuarios.cargar(nuevo.idUsuario()));
  }

  @Override
  public void cerrar(JwtService.Verificado tokenActual) {
    if (tokenActual != null) jwt.revocar(tokenActual);
    SecurityContextHolder.clearContext();
  }

  private AutenticacionResponseDTO autenticar(UsuarioDetailsService.Detalle usuario) {
    var contexto = SecurityContextHolder.createEmptyContext();
    var autenticacion = UsernamePasswordAuthenticationToken.authenticated(
        usuario.principal(), null, usuario.getAuthorities());
    autenticacion.setDetails(usuario);
    contexto.setAuthentication(autenticacion);
    SecurityContextHolder.setContext(contexto);
    sesion.limpiarCache();
    var token = jwt.emitir(usuario.principal(), politica.minutosInactividad());
    return new AutenticacionResponseDTO(token.token(), "Bearer", token.expiraEnSegundos(), sesion.actual());
  }
}
