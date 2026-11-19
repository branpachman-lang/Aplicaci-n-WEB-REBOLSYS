package pe.com.rebolsysBackend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;

class JwtServiceTest {
  private static final String CLAVE = Base64.getEncoder().encodeToString(new byte[32]);
  private final CredencialesService.Principal usuario = new CredencialesService.Principal(7L, "huella-privada-de-prueba");
  private Reloj reloj;
  private JwtService jwt;

  @BeforeEach
  void preparar() {
    reloj = new Reloj();
    jwt = new JwtService(CLAVE, Duration.ofHours(8), reloj, false);
  }

  @Test
  void emiteUnJwtFirmadoSinDatosPersonalesNiHashDeCredencial() {
    var token = jwt.emitir(usuario, 30);
    var payload = new String(Base64.getUrlDecoder().decode(token.token().split("\\.")[1]), StandardCharsets.UTF_8);
    assertThat(payload).contains("\"sub\":\"7\"").doesNotContain(usuario.firmaCredencial(), "contrasena", "correo", "roles", "permisos");
    assertThat(jwt.vigente(jwt.verificar(token.token()), usuario)).isTrue();
    assertThat(token.expiraEnSegundos()).isEqualTo(1800);
    assertThat(token.toString()).doesNotContain(token.token());
    assertThat(usuario.toString()).doesNotContain(usuario.firmaCredencial());
  }

  @Test
  void rechazaUnaFirmaModificada() {
    var token = jwt.emitir(usuario, 30).token();
    var partes = token.split("\\.");
    partes[2] = (partes[2].startsWith("A") ? "B" : "A") + partes[2].substring(1);
    assertThatThrownBy(() -> jwt.verificar(String.join(".", partes))).isInstanceOf(BadCredentialsException.class);
  }

  @Test
  void rechazaTokensVencidosYMalformados() {
    var token = jwt.emitir(usuario, 1).token();
    reloj.avanzar(Duration.ofMinutes(1));
    assertThatThrownBy(() -> jwt.verificar(token)).isInstanceOf(BadCredentialsException.class);
    assertThatThrownBy(() -> jwt.verificar("sin-firma")).isInstanceOf(BadCredentialsException.class);
  }

  @Test
  void cambiarCredencialInvalidaLaPruebaDeVigencia() {
    var token = jwt.verificar(jwt.emitir(usuario, 30).token());
    assertThat(jwt.vigente(token, new CredencialesService.Principal(7L, "otra-huella"))).isFalse();
    assertThat(jwt.vigente(token, new CredencialesService.Principal(8L, usuario.firmaCredencial()))).isFalse();
  }

  @Test
  void renuevaPorActividadYRevocaTodaLaCadenaAlCerrar() {
    var inicial = jwt.emitir(usuario, 30).token();
    var verificado = jwt.verificar(inicial);
    assertThat(jwt.renovar(verificado, usuario, 30)).isNull();
    reloj.avanzar(Duration.ofMinutes(16));
    var renovado = jwt.renovar(verificado, usuario, 30);
    assertThat(renovado).isNotNull();
    assertThat(jwt.verificar(renovado.token()).id()).isEqualTo(verificado.id());
    jwt.revocar(jwt.verificar(renovado.token()));
    assertThatThrownBy(() -> jwt.verificar(inicial)).isInstanceOf(BadCredentialsException.class);
    assertThatThrownBy(() -> jwt.verificar(renovado.token())).isInstanceOf(BadCredentialsException.class);
  }

  @Test
  void laActividadNoSuperaLaDuracionMaxima() {
    jwt = new JwtService(CLAVE, Duration.ofMinutes(2), reloj, false);
    var token = jwt.verificar(jwt.emitir(usuario, 1).token());
    reloj.avanzar(Duration.ofSeconds(40));
    var renovado = jwt.verificar(jwt.renovar(token, usuario, 1).token());
    reloj.avanzar(Duration.ofSeconds(40));
    var ultimo = jwt.renovar(renovado, usuario, 1);
    assertThat(jwt.verificar(ultimo.token()).expira()).isEqualTo(token.inicio().plus(Duration.ofMinutes(2)));
    reloj.avanzar(Duration.ofSeconds(41));
    assertThatThrownBy(() -> jwt.verificar(ultimo.token())).isInstanceOf(BadCredentialsException.class);
  }

  @Test
  void cadaPeticionPuedeRestablecerElPlazoDeInactividad() {
    var token = jwt.verificar(jwt.emitir(usuario, 30).token());
    reloj.avanzar(Duration.ofMinutes(5));
    var renovado = jwt.renovar(token, usuario, 30);
    assertThat(jwt.verificar(renovado.token()).expira()).isEqualTo(reloj.instant().plus(Duration.ofMinutes(30)));
  }

  @Test
  void reiniciarLaInstanciaInvalidaTokensAnteriores() {
    var token = jwt.emitir(usuario, 30).token();
    var reiniciado = new JwtService(CLAVE, Duration.ofHours(8), reloj, false);
    assertThatThrownBy(() -> reiniciado.verificar(token)).isInstanceOf(BadCredentialsException.class);
  }

  @Test
  void produccionExigeUnaClaveFuerteYNoLaIncluyeEnElError() {
    assertThatThrownBy(() -> new JwtService("", Duration.ofHours(8), reloj, true)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> new JwtService("secreto-invalido", Duration.ofHours(8), reloj, true))
        .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("secreto-invalido");
  }

  static class Reloj extends Clock {
    private Instant instante = Instant.parse("2026-11-19T12:00:00Z");
    void avanzar(Duration tiempo) { instante = instante.plus(tiempo); }
    @Override public Instant instant() { return instante; }
    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }
  }
}
