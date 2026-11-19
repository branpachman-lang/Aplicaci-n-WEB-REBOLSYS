package pe.com.rebolsysBackend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.com.rebolsysBackend.dto.AutenticacionResponseDTO;
import pe.com.rebolsysBackend.dto.SesionActualDTO;
import pe.com.rebolsysBackend.dto.auth.CambioPasswordRequestDTO;
import pe.com.rebolsysBackend.dto.auth.LoginRequestDTO;
import pe.com.rebolsysBackend.security.JwtService;
import pe.com.rebolsysBackend.service.service.AuthService;

@RestController
@RequestMapping("/api/v1/sesion")
@Tag(name = "sesion-operacion-controller")
@RequiredArgsConstructor
@PreAuthorize("@sesionOperacionService.esPrueba() or isAuthenticated()")
public class AuthController {
  private final AuthService auth;

  @PostMapping("/iniciar")
  @PreAuthorize("permitAll()")
  @Operation(operationId = "iniciarSesion", summary = "Autenticar credenciales y emitir un JWT Bearer")
  public AutenticacionResponseDTO iniciar(@Valid @RequestBody LoginRequestDTO datos, HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store");
    return auth.iniciar(datos);
  }

  @GetMapping("/actual")
  @Operation(summary = "Consultar el usuario, turno y permisos vigentes del JWT")
  public SesionActualDTO actual() {
    return auth.actual();
  }

  @PostMapping("/contrasena")
  public AutenticacionResponseDTO cambiar(@Valid @RequestBody CambioPasswordRequestDTO datos,
      HttpServletRequest request, HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store");
    return auth.cambiar(datos, tokenActual(request));
  }

  @PostMapping("/cerrar")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void cerrar(HttpServletRequest request, HttpServletResponse response) {
    auth.cerrar(tokenActual(request));
    if (request.getSession(false) != null) request.getSession(false).invalidate();
    response.setHeader("Cache-Control", "no-store");
  }

  private JwtService.Verificado tokenActual(HttpServletRequest request) {
    return request.getAttribute(JwtService.ATRIBUTO) instanceof JwtService.Verificado token ? token : null;
  }
}
