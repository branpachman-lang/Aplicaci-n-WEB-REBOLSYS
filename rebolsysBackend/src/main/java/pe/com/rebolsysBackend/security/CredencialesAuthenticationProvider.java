package pe.com.rebolsysBackend.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** Conserva el bloqueo de intentos y la apertura transaccional del turno al autenticar. */
@Component
@RequiredArgsConstructor
public class CredencialesAuthenticationProvider implements AuthenticationProvider {
  private final CredencialesService credenciales;
  private final UsuarioDetailsService usuarios;

  @Override
  public Authentication authenticate(Authentication solicitud) {
    var principal = credenciales.iniciar(solicitud.getName(),
        java.util.Objects.toString(solicitud.getCredentials(), ""));
    var usuario = usuarios.cargar(principal.idUsuario());
    return UsernamePasswordAuthenticationToken.authenticated(usuario.principal(), null, usuario.getAuthorities());
  }

  @Override
  public boolean supports(Class<?> tipo) {
    return UsernamePasswordAuthenticationToken.class.isAssignableFrom(tipo);
  }
}
