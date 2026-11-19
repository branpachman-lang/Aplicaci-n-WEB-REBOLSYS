package pe.com.rebolsysBackend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pe.com.rebolsysBackend.repository.FormaPagoRepository;
import pe.com.rebolsysBackend.repository.TurnoCajaRepository;

class JwtContextoPeticionTest {
  @AfterEach void limpiar() {
    SecurityContextHolder.clearContext();
    RequestContextHolder.resetRequestAttributes();
  }

  @Test void validaCadaPeticionYComparteSuPerfilSinReutilizarLosPermisosAnteriores() throws Exception {
    var jwt = mock(JwtService.class);
    var usuarios = mock(UsuarioDetailsService.class);
    var politica = mock(PoliticaSeguridadService.class);
    var errores = mock(RestAuthenticationEntryPoint.class);
    var credenciales = mock(CredencialesService.class);
    var accesos = mock(AccesosService.class);
    var sesion = new SesionOperacionService(mock(TurnoCajaRepository.class),
        mock(FormaPagoRepository.class), credenciales, accesos);
    var filtro = new JwtAuthenticationFilter(jwt, usuarios, politica, errores);
    var principal = new CredencialesService.Principal(7L, "huella");
    var token = new JwtService.Verificado(7L, "cadena", "version", Instant.now(), Instant.now().plusSeconds(1800));
    var anterior = new AccesosService.PerfilAcceso(List.of(), List.of("RECEPCIONISTA"),
        List.of(), List.of("VENTAS:VER"));
    var actualizado = new AccesosService.PerfilAcceso(List.of(), List.of("RECEPCIONISTA"), List.of(), List.of());
    when(jwt.verificar("token")).thenReturn(token);
    when(jwt.vigente(token, principal)).thenReturn(true);
    when(usuarios.cargar(7L)).thenReturn(
        new UsuarioDetailsService.Detalle(principal, "recepcion", List.of(), false, anterior),
        new UsuarioDetailsService.Detalle(principal, "recepcion", List.of(), false, actualizado));

    for (boolean permitido : List.of(true, false)) {
      var request = new MockHttpServletRequest("GET", "/api/v1/sesion/actual");
      request.addHeader("Authorization", "Bearer token");
      RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
      filtro.doFilter(request, new MockHttpServletResponse(), (entrada, salida) -> {
        var actual = sesion.actual();
        assertThat(actual.tiene("VENTAS:VER")).isEqualTo(permitido);
        assertThat(sesion.actual()).isSameAs(actual);
      });
    }
    verify(jwt, times(2)).verificar("token");
    verify(jwt, times(2)).vigente(token, principal);
    verify(usuarios, times(2)).cargar(7L);
    verifyNoInteractions(credenciales, accesos, errores);
  }
}
