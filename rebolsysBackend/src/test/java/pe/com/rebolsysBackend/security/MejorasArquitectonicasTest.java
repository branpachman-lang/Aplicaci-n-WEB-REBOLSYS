package pe.com.rebolsysBackend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.FilterChain;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.com.rebolsysBackend.config.RequestIdFilter;
import pe.com.rebolsysBackend.exception.GlobalExceptionHandler;
import pe.com.rebolsysBackend.exception.ReglaNegocioException;
import tools.jackson.databind.ObjectMapper;

class MejorasArquitectonicasTest {
  private static final String CLAVE = Base64.getEncoder().encodeToString(new byte[32]);

  @Test
  void requestIdFilterCorrelacionaPeticionYLimpiaMdc() throws Exception {
    var filtro = new RequestIdFilter();
    var mvc = MockMvcBuilders.standaloneSetup(new ControladorPrueba())
        .setControllerAdvice(new GlobalExceptionHandler())
        .addFilters(filtro)
        .build();

    var res = mvc.perform(get("/prueba-error"))
        .andExpect(status().isConflict())
        .andExpect(header().exists(RequestIdFilter.CABECERA))
        .andExpect(jsonPath("$.requestId").isString())
        .andReturn();

    String headerId = res.getResponse().getHeader(RequestIdFilter.CABECERA);
    assertThat(headerId).hasSize(8);
    assertThat(res.getResponse().getContentAsString()).contains("\"requestId\":\"" + headerId + "\"");
    assertThat(MDC.get(RequestIdFilter.CLAVE_MDC)).isNull();
  }

  @Test
  void loginRateLimitFilterBloqueaExcesoDeIntentosPorIp() throws Exception {
    var reloj = new RelojMutable();
    var errores = new RespuestaErrorSeguridad(new ObjectMapper());
    var filtro = new LoginRateLimitFilter(errores, true, 2, Duration.ofMinutes(1), reloj);
    FilterChain pasa = (req, res) -> ((MockHttpServletResponse) res).setStatus(200);

    for (int i = 0; i < 2; i++) {
      var req = new MockHttpServletRequest("POST", "/api/v1/sesion/iniciar");
      req.setRemoteAddr("10.0.0.5");
      var res = new MockHttpServletResponse();
      filtro.doFilter(req, res, pasa);
      assertThat(res.getStatus()).isEqualTo(200);
    }

    var reqBloqueado = new MockHttpServletRequest("POST", "/api/v1/sesion/iniciar");
    reqBloqueado.setRemoteAddr("10.0.0.5");
    var resBloqueado = new MockHttpServletResponse();
    filtro.doFilter(reqBloqueado, resBloqueado, pasa);
    assertThat(resBloqueado.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    assertThat(resBloqueado.getHeader("Retry-After")).isNotBlank();
    assertThat(resBloqueado.getHeader("Cache-Control")).isEqualTo("no-store");

    reloj.avanzar(Duration.ofSeconds(61));
    var reqDespues = new MockHttpServletRequest("POST", "/api/v1/sesion/iniciar");
    reqDespues.setRemoteAddr("10.0.0.5");
    var resDespues = new MockHttpServletResponse();
    filtro.doFilter(reqDespues, resDespues, pasa);
    assertThat(resDespues.getStatus()).isEqualTo(200);
  }

  @Test
  void loginRateLimitConservaLosContadoresAlLlenarseYAdmiteIpsAlVencer() throws Exception {
    var reloj = new RelojMutable();
    var errores = new RespuestaErrorSeguridad(new ObjectMapper());
    var filtro = new LoginRateLimitFilter(errores, true, 1, Duration.ofMillis(1500), reloj);

    for (int i = 0; i < 2048; i++) {
      assertThat(intentarLogin(filtro, "2001:db8::" + Integer.toHexString(i)).getStatus())
          .isEqualTo(200);
    }

    var saturada = intentarLogin(filtro, "2001:db8::ffff");
    assertThat(saturada.getStatus()).isEqualTo(429);
    assertThat(saturada.getHeader("Retry-After")).isEqualTo("2");
    assertThat(intentarLogin(filtro, "2001:db8::1").getStatus()).isEqualTo(429);

    reloj.avanzar(Duration.ofMillis(1500));
    assertThat(intentarLogin(filtro, "2001:db8::ffff").getStatus()).isEqualTo(200);
    assertThat(intentarLogin(filtro, "2001:db8::ffff").getStatus()).isEqualTo(429);
  }

  private MockHttpServletResponse intentarLogin(LoginRateLimitFilter filtro, String ip) throws Exception {
    var request = new MockHttpServletRequest("POST", "/hotel/api/v1/sesion/iniciar");
    request.setContextPath("/hotel");
    request.setRemoteAddr(ip);
    var response = new MockHttpServletResponse();
    filtro.doFilter(request, response, (entrada, salida) -> response.setStatus(200));
    return response;
  }

  @Test
  @SuppressWarnings("unchecked")
  void jwtPermiteMultiplesInstanciasConRevocacionCompartidaEnRedis() {
    var reloj = new RelojMutable();
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    ValueOperations<String, String> ops = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(ops);

    var instanciaA = new JwtService(CLAVE, Duration.ofHours(8), reloj, false, false, true, "rebolsys:test:", redis);
    var instanciaB = new JwtService(CLAVE, Duration.ofHours(8), reloj, false, false, true, "rebolsys:test:", redis);
    var usuario = new CredencialesService.Principal(11L, "huella-11");

    var emitido = instanciaA.emitir(usuario, 30);
    var verificadoEnB = instanciaB.verificar(emitido.token());
    assertThat(verificadoEnB.idUsuario()).isEqualTo(11L);

    instanciaA.revocar(verificadoEnB);
    verify(ops).set(eq("rebolsys:test:jwt:revocado:" + verificadoEnB.id()), eq("1"), any(Duration.class));
    when(redis.hasKey("rebolsys:test:jwt:revocado:" + verificadoEnB.id())).thenReturn(true);

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> instanciaB.verificar(emitido.token()))
        .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
  }

  @RestController
  static class ControladorPrueba {
    @GetMapping("/prueba-error")
    public void fallar() {
      throw new ReglaNegocioException("Error controlado de prueba");
    }
  }

  static class RelojMutable extends Clock {
    private Instant instante = Instant.parse("2026-11-25T15:00:00Z");

    void avanzar(Duration duracion) {
      instante = instante.plus(duracion);
    }

    @Override
    public Instant instant() {
      return instante;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }
  }
}
