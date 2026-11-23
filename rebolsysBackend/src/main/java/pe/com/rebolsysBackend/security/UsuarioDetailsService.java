package pe.com.rebolsysBackend.security;

import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.rebolsysBackend.repository.accesos.UsuarioRepository;

/** Carga el estado y los permisos actuales, sin confiar en roles enviados por el cliente. */
@Service
@RequiredArgsConstructor
public class UsuarioDetailsService implements UserDetailsService {
  private final CredencialesService credenciales;
  private final AccesosService accesos;
  private final UsuarioRepository usuarios;

  @Override
  @Transactional(readOnly = true)
  public Detalle loadUserByUsername(String username) {
    var usuario = usuarios.findByUsernameIgnoreCase(username.strip())
        .orElseThrow(() -> new UsernameNotFoundException("Credenciales inválidas o vencidas"));
    return cargar(usuario.getIdUsuario());
  }

  @Transactional(readOnly = true)
  public Detalle cargar(Long idUsuario) {
    var principal = credenciales.principalVigente(idUsuario);
    var cuenta = credenciales.vigente(principal);
    var perfil = cuenta.requiereCambio() ? null : accesos.perfil(idUsuario);
    List<GrantedAuthority> authorities = perfil == null ? List.of()
        : Stream.concat(Stream.of("ACCESO_OPERATIVO"),
            Stream.concat(perfil.roles().stream().map(r -> "ROLE_" + r), perfil.permisos().stream()))
            .distinct().<GrantedAuthority>map(SimpleGrantedAuthority::new).toList();
    return new Detalle(principal, cuenta.usuario().getUsername(), authorities, cuenta.requiereCambio(), perfil);
  }

  public record Detalle(CredencialesService.Principal principal, String nombre,
      List<GrantedAuthority> authorities, boolean requiereCambio,
      AccesosService.PerfilAcceso perfil) implements UserDetails {
    @Override public String getUsername() { return nombre; }
    // El proveedor verifica BCrypt dentro de la transacción de CredencialesService; no transportamos el hash.
    @Override public String getPassword() { return null; }
    @Override public List<GrantedAuthority> getAuthorities() { return authorities; }
  }
}
