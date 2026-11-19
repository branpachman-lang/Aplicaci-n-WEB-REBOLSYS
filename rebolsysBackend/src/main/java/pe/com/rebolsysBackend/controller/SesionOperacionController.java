package pe.com.rebolsysBackend.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import pe.com.rebolsysBackend.dto.AutenticacionResponseDTO;
import pe.com.rebolsysBackend.dto.SesionActualDTO;
import pe.com.rebolsysBackend.security.*;

@RestController
@RequestMapping("/api/v1/sesion")
@RequiredArgsConstructor
@Acceso(Acceso.SESION)
public class SesionOperacionController {
  private final SesionOperacionService sesion;
  private final CredencialesService credenciales;
  private final PoliticaSeguridadService politica;
  private final UsuarioDetailsService usuarios;
  private final JwtService jwt;
  private final org.springframework.security.authentication.AuthenticationManager authenticationManager;
  @Value("${app.sesion-prueba.habilitada:false}") private boolean prueba;

  public record Iniciar(@NotBlank @Size(max = 50) String usuario, @NotBlank @Size(max = 128) String contrasena) {
    @Override public String toString() { return "Solicitud de inicio de sesión"; }
  }
  public record Cambiar(@NotBlank @Size(max = 128) String actual, @NotBlank @Size(max = 128) String nueva) {
    @Override public String toString() { return "Solicitud de cambio de contraseña"; }
  }

  @PostMapping("/iniciar")
  @Acceso(Acceso.PUBLICO)
  @Operation(operationId = "iniciarSesion", summary = "Autenticar credenciales y emitir un JWT Bearer")
  public AutenticacionResponseDTO iniciar(@Valid @RequestBody Iniciar datos,
      HttpServletRequest request, HttpServletResponse response) {
    if (prueba) throw new AuthenticationCredentialsNotFoundException("Desactiva la sesión de pruebas para iniciar sesión");
    var autenticacion = authenticationManager.authenticate(
        UsernamePasswordAuthenticationToken.unauthenticated(datos.usuario(), datos.contrasena()));
    return autenticar((CredencialesService.Principal) autenticacion.getPrincipal(), request, response);
  }

  @GetMapping("/actual")
  @Operation(summary = "Consultar el usuario, turno y permisos vigentes del JWT")
  public SesionActualDTO actual() {
    return sesion.actual();
  }

  @PostMapping("/contrasena")
  public AutenticacionResponseDTO cambiar(@Valid @RequestBody Cambiar datos,
      HttpServletRequest request, HttpServletResponse response) {
    var autenticacion = SecurityContextHolder.getContext().getAuthentication();
    if (autenticacion == null || !(autenticacion.getPrincipal() instanceof CredencialesService.Principal principal)) {
      throw new AuthenticationCredentialsNotFoundException("Inicia sesión para continuar");
    }
    var nuevo = credenciales.cambiar(principal, datos.actual(), datos.nueva());
    if (request.getAttribute(JwtService.ATRIBUTO) instanceof JwtService.Verificado token) jwt.revocar(token);
    return autenticar(nuevo, request, response);
  }

  @PostMapping("/cerrar")
  @Acceso(Acceso.CIERRE)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void cerrar(HttpServletRequest request, HttpServletResponse response) {
    if (request.getAttribute(JwtService.ATRIBUTO) instanceof JwtService.Verificado token) jwt.revocar(token);
    if (request.getSession(false) != null) request.getSession(false).invalidate();
    SecurityContextHolder.clearContext();
    response.setHeader("Cache-Control", "no-store");
  }

  private AutenticacionResponseDTO autenticar(CredencialesService.Principal principal,
      HttpServletRequest request, HttpServletResponse response) {
    var usuario = usuarios.cargar(principal.idUsuario());
    var contexto = SecurityContextHolder.createEmptyContext();
    contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
        usuario.principal(), null, usuario.getAuthorities()));
    SecurityContextHolder.setContext(contexto);
    request.removeAttribute(SesionOperacionService.ATRIBUTO);
    var token = jwt.emitir(usuario.principal(), politica.minutosInactividad());
    response.setHeader("Cache-Control", "no-store");
    return new AutenticacionResponseDTO(token.token(), "Bearer", token.expiraEnSegundos(), sesion.actual());
  }
}
