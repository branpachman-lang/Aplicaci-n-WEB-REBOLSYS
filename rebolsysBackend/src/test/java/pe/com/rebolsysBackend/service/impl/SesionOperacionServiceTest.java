package pe.com.rebolsysBackend.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.mock.web.MockHttpServletRequest;
import pe.com.rebolsysBackend.dto.SesionActualDTO;
import pe.com.rebolsysBackend.entity.*;
import pe.com.rebolsysBackend.repository.*;
import pe.com.rebolsysBackend.security.*;

class SesionOperacionServiceTest {
  TurnoCajaRepository turnos;
  CredencialesService credenciales;
  AccesosService accesos;
  SesionOperacionService servicio;
  Usuario usuario;
  CredencialesService.Principal principal;

  @BeforeEach void preparar() {
    turnos = mock(TurnoCajaRepository.class);
    credenciales = mock(CredencialesService.class);
    accesos = mock(AccesosService.class);
    var pagos = mock(FormaPagoRepository.class);
    servicio = new SesionOperacionService(turnos, pagos, credenciales, accesos);
    usuario = new Usuario(); usuario.setIdUsuario(130L); usuario.setUsername("recepcion.flujo");
    principal = new CredencialesService.Principal(130L, "huella-de-credencial");
    when(credenciales.vigente(principal)).thenReturn(new CredencialesService.Cuenta(usuario, false));
    when(accesos.perfil(130L)).thenReturn(new AccesosService.PerfilAcceso(List.of(1L),
        List.of(AccesosService.RECEPCIONISTA), List.of(), List.of("RESERVAS:VER")));
    var turno = new TurnoCaja(); turno.setIdTurno(101L);
    when(turnos.findByUsuarioIdUsuarioAndEstadoSesionAndFechaCierreIsNullOrderByIdTurno(130L, 1))
        .thenReturn(List.of(turno));
    var pago = new FormaPago(); pago.setIdFormaPago(1L); pago.setNombrePago("EFECTIVO");
    when(pagos.findAll()).thenReturn(List.of(pago));
    SecurityContextHolder.getContext().setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
  }

  @AfterEach void limpiar() {
    SecurityContextHolder.clearContext();
    RequestContextHolder.resetRequestAttributes();
  }

  @Test void devuelveUnicamenteElUsuarioAutenticadoYSuTurno() {
    var sesion = servicio.actual();
    assertThat(sesion.idUsuario()).isEqualTo(130L);
    assertThat(sesion.usuario()).isEqualTo("recepcion.flujo");
    assertThat(sesion.modo()).isEqualTo("ACTIVA");
    assertThat(sesion.idTurno()).isEqualTo(101L);
    assertThat(sesion.idFormaPagoEfectivo()).isEqualTo(1L);
    assertThat(sesion.tiene("RESERVAS:VER")).isTrue();
    assertThat(sesion.tiene("RESERVAS:CREAR")).isFalse();
  }

  @Test void rechazaLaSesionSinAutenticacion() {
    SecurityContextHolder.clearContext();
    assertThatThrownBy(servicio::actual).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    verifyNoInteractions(credenciales, accesos, turnos);
  }

  @Test void noSustituyeUnaCredencialRevocadaPorOtraCuenta() {
    when(credenciales.vigente(principal)).thenThrow(new BadCredentialsException("Credencial revocada"));
    assertThatThrownBy(servicio::actual).isInstanceOf(BadCredentialsException.class);
    verifyNoInteractions(accesos, turnos);
  }

  @Test void laClaveTemporalNoHabilitaPermisosNiTurno() {
    when(credenciales.vigente(principal)).thenReturn(new CredencialesService.Cuenta(usuario, true));
    var sesion = servicio.actual();
    assertThat(sesion.requiereCambio()).isTrue();
    assertThat(sesion.idTurno()).isNull();
    assertThat(sesion.tiene("RESERVAS:VER")).isFalse();
    verifyNoInteractions(accesos, turnos);
  }

  @Test void noEscogeUnTurnoArbitrarioSiHayMasDeUnoAbierto() {
    when(turnos.findByUsuarioIdUsuarioAndEstadoSesionAndFechaCierreIsNullOrderByIdTurno(130L, 1))
        .thenReturn(List.of(new TurnoCaja(), new TurnoCaja()));
    assertThat(servicio.actual().idTurno()).isNull();
  }

  @Test void unaEtiquetaDeSesionNoConcedePermisos() {
    var sesion = new SesionActualDTO(130L, "recepcion.flujo", "LEGADO", 101L, 1L);
    assertThat(sesion.tiene("RESERVAS:CREAR")).isFalse();
  }

  @Test void reutilizaLosDatosValidadosYElTurnoSoloDuranteLaPeticion() {
    var perfil = new AccesosService.PerfilAcceso(List.of(1L), List.of(AccesosService.RECEPCIONISTA),
        List.of(), List.of("RESERVAS:VER"));
    var validado = new UsuarioDetailsService.Detalle(principal, usuario.getUsername(), List.of(), false, perfil);
    var autenticacion = UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
    autenticacion.setDetails(validado);
    SecurityContextHolder.getContext().setAuthentication(autenticacion);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    var primera = servicio.actual();
    assertThat(servicio.actual()).isSameAs(primera);
    verifyNoInteractions(credenciales, accesos);
    verify(turnos).findByUsuarioIdUsuarioAndEstadoSesionAndFechaCierreIsNullOrderByIdTurno(130L, 1);
  }

  @Test void noReutilizaElDetalleDeOtraCredencial() {
    var otro = new CredencialesService.Principal(130L, "credencial-anterior");
    var autenticacion = UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
    autenticacion.setDetails(new UsuarioDetailsService.Detalle(otro, "cuenta-anterior", List.of(), true, null));
    SecurityContextHolder.getContext().setAuthentication(autenticacion);
    assertThat(servicio.actual().requiereCambio()).isFalse();
    verify(credenciales).vigente(principal);
    verify(accesos).perfil(130L);
  }
}
