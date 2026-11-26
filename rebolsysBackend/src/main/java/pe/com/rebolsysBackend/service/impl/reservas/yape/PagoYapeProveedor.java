package pe.com.rebolsysBackend.service.impl.reservas.yape;

import static pe.com.rebolsysBackend.service.impl.reservas.yape.PagoYapeDatos.*;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import pe.com.rebolsysBackend.exception.ReglaNegocioException;
import tools.jackson.databind.JsonNode;

/** Conector de pagos Yape mediante Mercado Pago Peru Checkout API (Orders) o simulador local. */
@Component
class PagoYapeProveedor {
  private static final String MP = "https://api.mercadopago.com";
  private static final String CUENTA = "https://api.mercadolibre.com/users/me";
  private static final String ID_ORDEN = "ORD[A-Z0-9]{5,61}";
  private final RestClient cliente;
  private final boolean habilitado;
  private final Modo modo;
  private final Ambiente ambiente;
  private final String publica;
  private final String privada;
  private final String vendedor;
  private final String secreto;
  private final String correoPagador;

  @Autowired
  PagoYapeProveedor(
      @Value("${app.yape.habilitado:true}") boolean habilitado,
      @Value("${app.yape.modo:SIMULADO}") Modo modo,
      @Value("${app.yape.ambiente:PRUEBA}") Ambiente ambiente,
      @Value("${app.yape.public-key:}") String publica,
      @Value("${app.yape.access-token:}") String privada,
      @Value("${app.yape.seller-id:}") String vendedor,
      @Value("${app.yape.webhook-secret:}") String secreto,
      @Value("${app.yape.payer-email:pagos-yape@rebolsys.local}") String correoPagador) {
    this(
        crearCliente(),
        habilitado,
        modo,
        ambiente,
        publica,
        privada,
        vendedor,
        secreto,
        correoPagador);
  }

  PagoYapeProveedor(
      RestClient cliente,
      boolean habilitado,
      Modo modo,
      Ambiente ambiente,
      String publica,
      String privada,
      String vendedor,
      String secreto,
      String correoPagador) {
    this.cliente = cliente;
    this.habilitado = habilitado;
    this.modo = modo;
    this.ambiente = ambiente;
    this.publica = publica == null ? "" : publica.trim();
    this.privada = privada == null ? "" : privada.trim();
    this.vendedor = vendedor == null ? "" : vendedor.trim();
    this.secreto = secreto == null ? "" : secreto.trim();
    this.correoPagador =
        correoPagador == null || correoPagador.isBlank()
            ? "pagos-yape@rebolsys.local"
            : correoPagador.trim();
    if (this.habilitado && this.modo == Modo.MERCADO_PAGO) {
      validarCredencialesConfiguradas();
    }
  }

  private static RestClient crearCliente() {
    var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    var fabrica = new JdkClientHttpRequestFactory(http);
    fabrica.setReadTimeout(Duration.ofSeconds(8));
    return RestClient.builder().requestFactory(fabrica).build();
  }

  private void validarCredencialesConfiguradas() {
    if (!publica.matches("[A-Za-z0-9_-]{12,160}")
        || !privada.matches("APP_USR-[A-Za-z0-9_-]{12,250}")) {
      throw new IllegalStateException(
          "Configura app.yape.public-key y app.yape.access-token para usar Mercado Pago");
    }
    if (ambiente == Ambiente.PRUEBA && !vendedor.matches("[0-9]{1,24}")) {
      throw new IllegalStateException(
          "Configura app.yape.seller-id del vendedor de prueba de Mercado Pago");
    }
    if (!vendedor.isBlank() && !vendedor.matches("[0-9]{1,24}")) {
      throw new IllegalStateException("app.yape.seller-id debe ser numérico");
    }
  }

  boolean habilitado() {
    return habilitado;
  }

  Modo modo() {
    return modo;
  }

  Ambiente ambiente() {
    return ambiente;
  }

  Configuracion configuracion() {
    return new Configuracion(habilitado, modo, ambiente);
  }

  void validarVendedor() {
    if (modo == Modo.SIMULADO) return;
    try {
      var usuario = get(URI.create(CUENTA));
      boolean esPrueba = false;
      for (var tag : usuario.path("tags")) {
        if ("test_user".equals(tag.asText())) esPrueba = true;
      }
      boolean paisPeru = "MPE".equals(usuario.path("site_id").asText());
      boolean vendedorCoincide = vendedor.isBlank() || vendedor.equals(usuario.path("id").asText());
      boolean ambienteCoincide = ambiente == Ambiente.PRUEBA ? esPrueba : !esPrueba;
      if (paisPeru && vendedorCoincide && ambienteCoincide) return;
    } catch (RestClientException ex) {
      throw new ReglaNegocioException("No se pudo verificar la cuenta de Mercado Pago");
    }
    throw new ReglaNegocioException(
        ambiente == Ambiente.PRUEBA
            ? "Las credenciales deben corresponder a una cuenta de prueba de Mercado Pago Perú"
            : "Las credenciales deben corresponder a una cuenta productiva de Mercado Pago Perú");
  }

  Resultado enviar(Intento i, String telefono, String otp) {
    if (i.modo() == Modo.SIMULADO) {
      return simularEnvio(i, telefono, otp);
    }
    String token;
    try {
      var generado =
          cliente
              .post()
              .uri(URI.create(MP + "/platforms/pci/yape/v1/payment?public_key=" + publica))
              .contentType(MediaType.APPLICATION_JSON)
              .body(
                  Map.of(
                      "phoneNumber",
                      telefono,
                      "otp",
                      otp,
                      "requestId",
                      i.claveOperacion().toString()))
              .retrieve()
              .body(JsonNode.class);
      token = generado == null ? "" : generado.path("id").asText("");
    } catch (RestClientResponseException ex) {
      return new Resultado(
          i.claveOperacion(),
          null,
          Estado.RECHAZADO,
          i.importe(),
          null,
          extraerMensajeError(ex, "Token Yape rechazado por Mercado Pago"));
    } catch (RestClientException ex) {
      return new Resultado(
          i.claveOperacion(),
          null,
          Estado.RECHAZADO,
          i.importe(),
          null,
          "No se pudo generar el token Yape en Mercado Pago");
    }
    if (token.isBlank()) {
      return new Resultado(
          i.claveOperacion(),
          null,
          Estado.RECHAZADO,
          i.importe(),
          null,
          "Mercado Pago no devolvió un token Yape válido");
    }
    String importe = i.importe().toPlainString();
    try {
      var respuesta =
          cliente
              .post()
              .uri(MP + "/v1/orders")
              .headers(h -> h.setBearerAuth(privada))
              .header("X-Idempotency-Key", i.claveOperacion().toString())
              .contentType(MediaType.APPLICATION_JSON)
              .body(
                  Map.of(
                      "type",
                      "online",
                      "processing_mode",
                      "automatic",
                      "external_reference",
                      i.claveOperacion().toString(),
                      "total_amount",
                      importe,
                      "payer",
                      Map.of(
                          "email",
                          correoPagador,
                          "entity_type",
                          "individual",
                          "phone",
                          Map.of("area_code", "51", "number", telefono)),
                      "config",
                      Map.of("location", Map.of("state_id", "PE-LIM")),
                      "transactions",
                      Map.of(
                          "payments",
                          List.of(
                              Map.of(
                                  "amount",
                                  importe,
                                  "payment_method",
                                  Map.of("id", "yape", "type", "debit_card", "token", token))))))
              .retrieve()
              .body(JsonNode.class);
      return resultado(respuesta, i.ambiente());
    } catch (RestClientResponseException ex) {
      return manejarRespuestaFallida(i, ex);
    } catch (RestClientException ex) {
      // Un fallo de transporte tras enviar la orden deja el resultado incierto para conciliar.
      return null;
    }
  }

  private Resultado simularEnvio(Intento i, String telefono, String otp) {
    if ("000000".equals(otp) || telefono.endsWith("2")) {
      return new Resultado(
          i.claveOperacion(),
          "SIM-" + i.claveOperacion(),
          Estado.RECHAZADO,
          i.importe(),
          null,
          "Pago rechazado en simulador local");
    }
    if ("999999".equals(otp) || telefono.endsWith("3")) {
      return new Resultado(
          i.claveOperacion(),
          "SIM-" + i.claveOperacion(),
          Estado.PENDIENTE,
          i.importe(),
          null,
          "Confirmación pendiente; consulta el estado para conciliar");
    }
    return new Resultado(
        i.claveOperacion(),
        "SIM-" + i.claveOperacion(),
        Estado.APLICADO,
        i.importe(),
        "SIM-" + i.claveOperacion(),
        "Pago aprobado en simulador local");
  }

  private Resultado manejarRespuestaFallida(Intento i, RestClientResponseException ex) {
    return switch (ex.getStatusCode().value()) {
      case 400, 401, 403, 422 ->
          new Resultado(
              i.claveOperacion(),
              null,
              Estado.RECHAZADO,
              i.importe(),
              null,
              extraerMensajeError(ex, "Orden rechazada por Mercado Pago"));
      case 402 -> consultar(i);
      default -> null;
    };
  }

  private static String extraerMensajeError(
      RestClientResponseException ex, String predeterminado) {
    String cuerpo = ex.getResponseBodyAsString();
    if (cuerpo.isBlank()) return predeterminado;
    return cuerpo.length() > 180 ? cuerpo.substring(0, 180) : cuerpo;
  }

  Resultado consultar(Intento i) {
    if (i.modo() == Modo.SIMULADO) {
      return new Resultado(
          i.claveOperacion(),
          "SIM-" + i.claveOperacion(),
          Estado.APLICADO,
          i.importe(),
          "SIM-" + i.claveOperacion(),
          "Pago conciliado en simulador local");
    }
    if (i.ordenProveedor() != null) return consultarOrden(i.ordenProveedor(), i.ambiente());
    Instant ahora = Instant.now();
    Instant inicio = i.creado().minus(Duration.ofMinutes(5));
    if (inicio.isBefore(ahora.minus(Duration.ofDays(29)))) return null;
    try {
      String filtro =
          "?begin_date="
              + inicio
              + "&end_date="
              + ahora
              + "&external_reference="
              + i.claveOperacion()
              + "&type=online";
      var lista = buscarOrdenes(filtro);
      var datos = lista.path("data");
      if (!datos.isArray() || datos.size() != 1) return null;
      return consultarOrden(datos.get(0).path("id").asText(""), i.ambiente());
    } catch (RestClientException ex) {
      return null;
    }
  }

  private JsonNode buscarOrdenes(String filtro) {
    try {
      return get(URI.create(MP + "/v1/orders/search" + filtro));
    } catch (RestClientResponseException ex) {
      if (ex.getStatusCode().value() != 404 && ex.getStatusCode().value() != 405) throw ex;
      return get(URI.create(MP + "/v1/orders" + filtro));
    }
  }

  Resultado consultarOrden(String id, Ambiente ambienteEsperado) {
    if (!id.matches(ID_ORDEN)) return null;
    try {
      var r = resultado(get(URI.create(MP + "/v1/orders/" + id)), ambienteEsperado);
      return r != null && id.equals(r.orden()) ? r : null;
    } catch (RestClientException ex) {
      return null;
    }
  }

  void validarNotificacion(String orden, String requestId, String firma) {
    if (!habilitado
        || modo != Modo.MERCADO_PAGO
        || secreto.isBlank()
        || !orden.matches(ID_ORDEN)
        || !requestId.matches("[A-Za-z0-9_-]{1,128}")
        || firma.length() > 256) {
      throw new AccessDeniedException("Notificación de Yape inválida");
    }
    String ts = null;
    String v1 = null;
    for (String campo : firma.split(",")) {
      String[] par = campo.trim().split("=", 2);
      if (par.length != 2) continue;
      if (par[0].equals("ts") && ts == null) ts = par[1];
      else if (par[0].equals("v1") && v1 == null) v1 = par[1];
      else throw new AccessDeniedException("Notificación de Yape inválida");
    }
    if (ts == null
        || !ts.matches("[0-9]{10}|[0-9]{13}")
        || v1 == null
        || !v1.matches("[0-9a-fA-F]{64}")) {
      throw new AccessDeniedException("Firma inválida");
    }
    long tiempo = Long.parseLong(ts);
    if (ts.length() == 10) tiempo *= 1000;
    if (Math.abs(Instant.now().toEpochMilli() - tiempo) > Duration.ofMinutes(5).toMillis()) {
      throw new AccessDeniedException("Firma vencida");
    }
    String mensaje =
        "id:" + orden.toLowerCase(Locale.ROOT) + ";request-id:" + requestId + ";ts:" + ts + ";";
    try {
      var mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      if (MessageDigest.isEqual(
          mac.doFinal(mensaje.getBytes(StandardCharsets.UTF_8)), HexFormat.of().parseHex(v1))) {
        return;
      }
    } catch (java.security.GeneralSecurityException ex) {
      throw new IllegalStateException("No se pudo validar la notificación", ex);
    }
    throw new AccessDeniedException("Firma inválida");
  }

  private JsonNode get(URI uri) {
    var cuerpo =
        cliente
            .get()
            .uri(uri)
            .headers(h -> h.setBearerAuth(privada))
            .retrieve()
            .body(JsonNode.class);
    if (cuerpo == null) throw new RestClientException("Respuesta vacía");
    return cuerpo;
  }

  Resultado resultado(JsonNode r, Ambiente ambienteEsperado) {
    if (r == null) return null;
    boolean liveMode = r.path("live_mode").asBoolean(false);
    if ((ambienteEsperado == Ambiente.PRUEBA && liveMode)
        || (ambienteEsperado == Ambiente.PRODUCCION && !liveMode)) {
      return null;
    }
    var orden = r.path("id").asText("");
    if (!orden.matches(ID_ORDEN)
        || (!vendedor.isBlank() && !vendedor.equals(r.path("user_id").asText()))
        || !"PEN".equals(r.path("currency").asText())) {
      return null;
    }
    try {
      var ref = UUID.fromString(r.path("external_reference").asText());
      var importe = new BigDecimal(r.path("total_amount").asText());
      var pagos = r.path("transactions").path("payments");
      String estadoOrden = r.path("status").asText();
      String detalleOrden = r.path("status_detail").asText("");
      if ("failed".equals(estadoOrden) || "canceled".equals(estadoOrden)) {
        return new Resultado(
            ref, orden, Estado.RECHAZADO, importe, null, detalleOrden.isBlank() ? estadoOrden : detalleOrden);
      }
      if (!pagos.isArray() || pagos.size() != 1) {
        return new Resultado(ref, orden, Estado.PENDIENTE, importe, null, detalleOrden);
      }
      var pago = pagos.get(0);
      if (!"yape".equals(pago.path("payment_method").path("id").asText())) return null;
      String estado = pago.path("status").asText();
      String detallePago = pago.path("status_detail").asText("");
      if ("failed".equals(estado) && !"processed".equals(estadoOrden)) {
        return new Resultado(
            ref, orden, Estado.RECHAZADO, importe, null, detallePago.isBlank() ? estado : detallePago);
      }
      if (!"processed".equals(estadoOrden)
          || !"accredited".equals(detalleOrden)
          || !"processed".equals(estado)
          || !"accredited".equals(detallePago)) {
        return new Resultado(
            ref, orden, Estado.PENDIENTE, importe, null, detallePago.isBlank() ? estado : detallePago);
      }
      var id = pago.path("id").asText("");
      if (!id.matches("PAY[A-Z0-9]{5,47}")
          || importe.compareTo(new BigDecimal(pago.path("paid_amount").asText())) != 0
          || importe.compareTo(new BigDecimal(r.path("total_paid_amount").asText())) != 0) {
        return null;
      }
      return new Resultado(ref, orden, Estado.APLICADO, importe, id, "Pago acreditado en Mercado Pago");
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }
}
