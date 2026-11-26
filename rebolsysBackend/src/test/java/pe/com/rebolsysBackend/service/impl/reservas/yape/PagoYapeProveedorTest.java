package pe.com.rebolsysBackend.service.impl.reservas.yape;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static pe.com.rebolsysBackend.service.impl.reservas.yape.PagoYapeDatos.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class PagoYapeProveedorTest {
  private static final UUID CLAVE = UUID.fromString("eb862316-37f3-4b78-ae00-acee95b2602a");
  private static final String ORDEN = "ORDPE01PRUEBA";
  private static final String SECRETO = "webhook-ficticio";
  private final ObjectMapper json = new ObjectMapper();
  private final PagoYapeProveedor proveedorPrueba =
      new PagoYapeProveedor(
          RestClient.builder().build(),
          true,
          Modo.MERCADO_PAGO,
          Ambiente.PRUEBA,
          "publica-ficticia",
          "APP_USR-privada-ficticia",
          "101",
          SECRETO,
          "pagos@rebolsys.local");

  @Test
  void acreditaSoloElTotalConfirmadoEnElAmbienteEsperado() {
    var r = proveedorPrueba.resultado(json.readTree(ordenConfirmada(false)), Ambiente.PRUEBA);
    assertThat(r.estado()).isEqualTo(Estado.APLICADO);
    assertThat(r.referencia()).isEqualTo(CLAVE);
    assertThat(r.importe()).isEqualByComparingTo("80.00");
    assertThat(r.pagoProveedor()).isEqualTo("PAYPE01PRUEBA");

    assertThat(
            proveedorPrueba.resultado(
                json.readTree(ordenConfirmada(false).replace("PEN", "USD")), Ambiente.PRUEBA))
        .isNull();
    assertThat(proveedorPrueba.resultado(json.readTree(ordenConfirmada(true)), Ambiente.PRUEBA))
        .isNull();
    assertThat(
            proveedorPrueba.resultado(
                json.readTree(
                    ordenConfirmada(false)
                        .replace("\"paid_amount\":\"80.00\"", "\"paid_amount\":\"0.00\"")),
                Ambiente.PRUEBA))
        .isNull();

    var prod = proveedorPrueba.resultado(json.readTree(ordenConfirmada(true)), Ambiente.PRODUCCION);
    assertThat(prod).isNotNull();
    assertThat(prod.estado()).isEqualTo(Estado.APLICADO);
  }

  @Test
  void enviaTokenYOrdenConTelefonoYOtpDelUsuarioAMercadoPago() {
    var builder = RestClient.builder();
    var servidor = MockRestServiceServer.bindTo(builder).build();
    var proveedor =
        new PagoYapeProveedor(
            builder.build(),
            true,
            Modo.MERCADO_PAGO,
            Ambiente.PRUEBA,
            "TEST-publica-123456",
            "APP_USR-privada-123456",
            "101",
            SECRETO,
            "pagos@rebolsys.local");

    servidor
        .expect(
            requestTo(
                "https://api.mercadopago.com/platforms/pci/yape/v1/payment?public_key=TEST-publica-123456"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(jsonPath("$.phoneNumber").value("987654321"))
        .andExpect(jsonPath("$.otp").value("654321"))
        .andExpect(jsonPath("$.requestId").value(CLAVE.toString()))
        .andRespond(withSuccess("{\"id\":\"yape-tok-001\"}", MediaType.APPLICATION_JSON));

    servidor
        .expect(requestTo("https://api.mercadopago.com/v1/orders"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", "Bearer APP_USR-privada-123456"))
        .andExpect(header("X-Idempotency-Key", CLAVE.toString()))
        .andExpect(jsonPath("$.total_amount").value("80.00"))
        .andExpect(jsonPath("$.payer.phone.number").value("987654321"))
        .andExpect(jsonPath("$.transactions.payments[0].payment_method.id").value("yape"))
        .andExpect(jsonPath("$.transactions.payments[0].payment_method.token").value("yape-tok-001"))
        .andRespond(withSuccess(ordenConfirmada(false), MediaType.APPLICATION_JSON));

    var intento =
        new Intento(
            CLAVE,
            1L,
            1L,
            1L,
            new BigDecimal("80.00"),
            "*** *** 321",
            Modo.MERCADO_PAGO,
            Ambiente.PRUEBA,
            Estado.ENVIANDO,
            null,
            null,
            null,
            Instant.now());
    var resultado = proveedor.enviar(intento, "987654321", "654321");
    assertThat(resultado).isNotNull();
    assertThat(resultado.estado()).isEqualTo(Estado.APLICADO);
    assertThat(resultado.orden()).isEqualTo(ORDEN);
    assertThat(resultado.pagoProveedor()).isEqualTo("PAYPE01PRUEBA");
    servidor.verify();
  }

  @Test
  void simuladorRespondeSegunOtpYTelefono() {
    var simulador =
        new PagoYapeProveedor(
            RestClient.builder().build(),
            true,
            Modo.SIMULADO,
            Ambiente.PRUEBA,
            "",
            "",
            "",
            "",
            "");
    var intento =
        new Intento(
            CLAVE,
            1L,
            1L,
            1L,
            new BigDecimal("40.00"),
            "*** *** 321",
            Modo.SIMULADO,
            Ambiente.PRUEBA,
            Estado.ENVIANDO,
            null,
            null,
            null,
            Instant.now());

    assertThat(simulador.enviar(intento, "987654321", "123456").estado())
        .isEqualTo(Estado.APLICADO);
    assertThat(simulador.enviar(intento, "987654321", "000000").estado())
        .isEqualTo(Estado.RECHAZADO);
    assertThat(simulador.enviar(intento, "987654321", "999999").estado())
        .isEqualTo(Estado.PENDIENTE);
    assertThat(simulador.consultar(intento).estado()).isEqualTo(Estado.APLICADO);
  }

  @Test
  void ordenFallidaSinTransaccionQuedaRechazada() {
    var r =
        proveedorPrueba.resultado(
            json.readTree(
                """
                {"id":"%s","user_id":"101","external_reference":"%s",
                 "currency":"PEN","total_amount":"80.00","status":"failed"}
                """
                    .formatted(ORDEN, CLAVE)),
            Ambiente.PRUEBA);
    assertThat(r.estado()).isEqualTo(Estado.RECHAZADO);
    assertThat(r.pagoProveedor()).isNull();
  }

  @Test
  void firmaDeWebhookVinculaOrdenYRequestId() throws Exception {
    String ts = Long.toString(Instant.now().getEpochSecond());
    String requestId = "peticion-prueba";
    String mensaje =
        "id:" + ORDEN.toLowerCase(Locale.ROOT) + ";request-id:" + requestId + ";ts:" + ts + ";";
    var mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(SECRETO.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    String firma =
        "ts="
            + ts
            + ",v1="
            + HexFormat.of().formatHex(mac.doFinal(mensaje.getBytes(StandardCharsets.UTF_8)));
    proveedorPrueba.validarNotificacion(ORDEN, requestId, firma);
    assertThatThrownBy(() -> proveedorPrueba.validarNotificacion(ORDEN, "otra-peticion", firma))
        .isInstanceOf(AccessDeniedException.class);
  }

  private static String ordenConfirmada(boolean liveMode) {
    return """
        {"id":"%s","user_id":"101","external_reference":"%s","live_mode":%s,
         "currency":"PEN","total_amount":"80.00","total_paid_amount":"80.00",
         "status":"processed","status_detail":"accredited",
         "transactions":{"payments":[{"id":"PAYPE01PRUEBA","status":"processed",
           "status_detail":"accredited","paid_amount":"80.00","payment_method":{"id":"yape"}}]}}
        """
        .formatted(ORDEN, CLAVE, liveMode);
  }
}
