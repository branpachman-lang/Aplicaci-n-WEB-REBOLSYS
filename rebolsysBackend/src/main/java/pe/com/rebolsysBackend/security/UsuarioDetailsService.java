package pe.com.rebolsysBackend.security;

import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Carga el estado y los permisos actuales, sin confiar en roles enviados por el cliente. */
@Service
@RequiredArgsConstructor
public class UsuarioDetailsService {
  private final CredencialesService credenciales;
  private final AccesosService accesos;

  @Transactional(readOnly = true)
  public Detalle cargar(Long idUsuario) {
    var principal = credenciales.principalVigente(idUsuario);
    var cuenta = credenciales.vigente(principal);
    var perfil = cuenta.requiereCambio() ? null : accesos.perfil(idUsuario);
    List<GrantedAuthority> authorities = perfil == null ? List.of()
        : Stream.concat(perfil.roles().stream().map(r -> "ROLE_" + r), perfil.permisos().stream())
            .distinct().<GrantedAuthority>map(SimpleGrantedAuthority::new).toList();
    return new Detalle(principal, cuenta.usuario().getUsername(), authorities);
  }

  public record Detalle(CredencialesService.Principal principal, String nombre,
      List<GrantedAuthority> authorities) implements UserDetails {
    @Override public String getUsername() { return nombre; }
    @Override public String getPassword() { return null; }
    @Override public List<GrantedAuthority> getAuthorities() { return authorities; }
  }
}
