package pe.com.rebolsysBackend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

/** Firma y verifica JWT, con revocación en memoria y respaldo opcional en Redis para múltiples réplicas. */
@Service
public class JwtService {
  private static final Logger log = LoggerFactory.getLogger(JwtService.class);
  public static final String ATRIBUTO = "rebolsys.jwt";
  public static final String CABECERA_TOKEN = "X-Auth-Token";
  public static final String CABECERA_VIGENCIA = "X-Auth-Expires-In";
  private static final String EMISOR = "rebolsys";
  private final SecretKey clave;
  private final Duration duracionMaxima;
  private final Clock reloj;
  private final String instancia = UUID.randomUUID().toString();
  private final boolean validarInstancia;
  private final boolean revocarEnRedis;
  private final String prefijoRedis;
  private final StringRedisTemplate redis;
  private final Map<String, Instant> revocados = new ConcurrentHashMap<>();

  @Autowired
  public JwtService(@Value("${app.jwt.secret:}") String secreto,
      @Value("${app.jwt.duracion-maxima:8h}") Duration duracionMaxima,
      @Value("${app.jwt.validar-instancia:true}") boolean validarInstancia,
      @Value("${app.jwt.revocar-en-redis:false}") boolean revocarEnRedis,
      @Value("${app.cache.prefijo:rebolsys:dev:v1:}") String prefijoRedis,
      ObjectProvider<StringRedisTemplate> redisProvider,
      Environment entorno) {
    this(secreto, duracionMaxima, Clock.systemUTC(), entorno.matchesProfiles("prod"),
        validarInstancia, revocarEnRedis, prefijoRedis, redisProvider.getIfAvailable());
  }

  JwtService(String secreto, Duration duracionMaxima, Clock reloj, boolean produccion) {
    this(secreto, duracionMaxima, reloj, produccion, true, false, "rebolsys:test:", null);
  }

  JwtService(String secreto, Duration duracionMaxima, Clock reloj, boolean produccion,
      boolean validarInstancia, boolean revocarEnRedis, String prefijoRedis, StringRedisTemplate redis) {
    if (duracionMaxima.compareTo(Duration.ofSeconds(1)) < 0 || duracionMaxima.compareTo(Duration.ofHours(24)) > 0) {
      throw new IllegalArgumentException("La duración máxima JWT debe estar entre un segundo y 24 horas");
    }
    if (secreto == null || secreto.isBlank()) {
      if (produccion) throw new IllegalStateException("Configura JWT_SECRET en Base64 con al menos 32 bytes");
      this.clave = Jwts.SIG.HS256.key().build();
    } else {
      try {
        this.clave = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secreto));
      } catch (RuntimeException ex) {
        throw new IllegalStateException("JWT_SECRET debe ser Base64 válido con al menos 32 bytes");
      }
    }
    this.duracionMaxima = duracionMaxima;
    this.reloj = reloj;
    this.validarInstancia = validarInstancia;
    this.revocarEnRedis = revocarEnRedis;
    this.prefijoRedis = (prefijoRedis == null ? "" : prefijoRedis) + "jwt:revocado:";
    this.redis = redis;
  }

  public record Emitido(String token, long expiraEnSegundos) {
    @Override public String toString() { return "JWT emitido"; }
  }

  public record Verificado(Long idUsuario, String id, String version, Instant inicio, Instant expira) {}

  public Emitido emitir(CredencialesService.Principal principal, int minutosInactividad) {
    return emitir(principal, UUID.randomUUID().toString(), reloj.instant(), minutosInactividad);
  }

  private Emitido emitir(CredencialesService.Principal principal, String id, Instant inicio, int minutos) {
    limpiarRevocados();
    var ahora = reloj.instant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    inicio = inicio.truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    var limite = inicio.plus(duracionMaxima);
    var expira = ahora.plus(Duration.ofMinutes(Math.max(1, minutos)));
    if (expira.isAfter(limite)) expira = limite;
    if (!expira.isAfter(ahora)) throw invalido();
    var token = Jwts.builder().issuer(EMISOR).subject(principal.idUsuario().toString()).id(id)
        .issuedAt(Date.from(ahora)).expiration(Date.from(expira))
        .claim("inicio", inicio.getEpochSecond()).claim("instancia", instancia)
        .claim("cv", version(principal)).signWith(clave, Jwts.SIG.HS256).compact();
    return new Emitido(token, Math.max(1, Duration.between(ahora, expira).toSeconds()));
  }

  public Verificado verificar(String token) {
    try {
      Claims datos = Jwts.parser().verifyWith(clave).requireIssuer(EMISOR)
          .clock(() -> Date.from(reloj.instant())).build().parseSignedClaims(token).getPayload();
      var id = datos.getId();
      var inicio = Instant.ofEpochSecond(datos.get("inicio", Long.class));
      var expira = datos.getExpiration().toInstant();
      var usuario = Long.valueOf(datos.getSubject());
      if (usuario <= 0 || id == null || id.length() > 64
          || (validarInstancia && !instancia.equals(datos.get("instancia", String.class)))
          || !expira.isAfter(reloj.instant())
          || datos.getIssuedAt() == null || datos.getIssuedAt().toInstant().isAfter(reloj.instant())
          || inicio.isAfter(reloj.instant()) || !inicio.plus(duracionMaxima).isAfter(reloj.instant())
          || expira.isAfter(inicio.plus(duracionMaxima)) || estaRevocado(id)) throw invalido();
      var version = datos.get("cv", String.class);
      if (version == null) throw invalido();
      return new Verificado(usuario, id, version, inicio, expira);
    } catch (JwtException | IllegalArgumentException | NullPointerException ex) {
      throw invalido();
    }
  }

  public boolean vigente(Verificado token, CredencialesService.Principal principal) {
    return token.idUsuario().equals(principal.idUsuario()) && MessageDigest.isEqual(
        token.version().getBytes(StandardCharsets.UTF_8), version(principal).getBytes(StandardCharsets.UTF_8));
  }

  public Emitido renovar(Verificado token, CredencialesService.Principal principal, int minutos) {
    if (!vigente(token, principal) || estaRevocado(token.id())) throw invalido();
    var expira = reloj.instant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
        .plus(Duration.ofMinutes(Math.max(1, minutos)));
    if (expira.isAfter(token.inicio().plus(duracionMaxima))) expira = token.inicio().plus(duracionMaxima);
    return expira.isAfter(token.expira()) ? emitir(principal, token.id(), token.inicio(), minutos) : null;
  }

  public void revocar(Verificado token) {
    var hasta = token.inicio().plus(duracionMaxima);
    revocados.put(token.id(), hasta);
    if (revocarEnRedis && redis != null) {
      var ttl = Duration.between(reloj.instant(), hasta);
      if (!ttl.isNegative() && !ttl.isZero()) {
        try {
          redis.opsForValue().set(prefijoRedis + token.id(), "1", ttl);
        } catch (RuntimeException ex) {
          log.warn("No se pudo registrar la revocación JWT en Redis ({}); se mantiene en memoria local", ex.getClass().getSimpleName());
        }
      }
    }
    limpiarRevocados();
  }

  private boolean estaRevocado(String id) {
    if (revocados.containsKey(id)) return true;
    if (revocarEnRedis && redis != null) {
      try {
        return Boolean.TRUE.equals(redis.hasKey(prefijoRedis + id));
      } catch (RuntimeException ex) {
        log.warn("No se pudo consultar la revocación JWT en Redis ({}); se continúa con caché local", ex.getClass().getSimpleName());
      }
    }
    return false;
  }

  private String version(CredencialesService.Principal principal) {
    try {
      var mac = Mac.getInstance("HmacSHA256");
      mac.init(clave);
      return Base64.getUrlEncoder().withoutPadding().encodeToString(
          mac.doFinal(("credencial:" + principal.idUsuario() + ":" + principal.firmaCredencial())
              .getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.GeneralSecurityException ex) {
      throw new IllegalStateException("No se pudo verificar la vigencia de la credencial");
    }
  }

  private void limpiarRevocados() {
    var ahora = reloj.instant();
    revocados.entrySet().removeIf(e -> !e.getValue().isAfter(ahora));
  }

  private static BadCredentialsException invalido() {
    return new BadCredentialsException("El acceso venció o no es válido. Inicia sesión nuevamente");
  }
}
