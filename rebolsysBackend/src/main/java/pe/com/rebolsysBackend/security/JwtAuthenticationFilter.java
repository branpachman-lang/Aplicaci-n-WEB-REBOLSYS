package pe.com.rebolsysBackend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Solo SecurityConfig registra este filtro, evitando ejecutarlo dos veces. */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
  private final JwtService jwt;
  private final UsuarioDetailsService usuarios;
  private final PoliticaSeguridadService politica;
  private final RestAuthenticationEntryPoint errores;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/v1/")
        || request.getRequestURI().equals("/api/v1/sesion/iniciar")
        || "OPTIONS".equals(request.getMethod());
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {
    var encabezado = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (encabezado == null) {
      chain.doFilter(request, response);
      return;
    }
    try {
      if (!encabezado.regionMatches(true, 0, "Bearer ", 0, 7) || encabezado.length() > 8192) {
        throw new BadCredentialsException("Credencial inválida");
      }
      var token = jwt.verificar(encabezado.substring(7).trim());
      var usuario = usuarios.cargar(token.idUsuario());
      if (!jwt.vigente(token, usuario.principal())) throw new BadCredentialsException("Credencial revocada");
      var contexto = SecurityContextHolder.createEmptyContext();
      contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
          usuario.principal(), null, usuario.getAuthorities()));
      SecurityContextHolder.setContext(contexto);
      request.setAttribute(JwtService.ATRIBUTO, token);
      response.setHeader("Cache-Control", "no-store");
      if (!request.getRequestURI().equals("/api/v1/sesion/cerrar")
          && !request.getRequestURI().equals("/api/v1/sesion/contrasena")) {
        var renovado = jwt.renovar(token, usuario.principal(), politica.minutosInactividad());
        if (renovado != null) {
          response.setHeader(JwtService.CABECERA_TOKEN, renovado.token());
          response.setHeader(JwtService.CABECERA_VIGENCIA, Long.toString(renovado.expiraEnSegundos()));
        }
      }
    } catch (AuthenticationException ex) {
      SecurityContextHolder.clearContext();
      errores.commence(request, response, ex);
      return;
    }
    chain.doFilter(request, response);
  }
}
