package pe.com.rebolsysBackend.service.impl;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import pe.com.rebolsysBackend.config.FacturacionApisPeruProperties;
import pe.com.rebolsysBackend.dto.EmisionElectronicaRequestDTO;
import pe.com.rebolsysBackend.entity.*;
import pe.com.rebolsysBackend.exception.FacturacionProveedorException;
import tools.jackson.databind.ObjectMapper;

/** Ejecutar contra api/simulations/facturacion-beta.sim.yaml, sin Oracle ni credenciales reales. */
@EnabledIfEnvironmentVariable(named = "RUN_FACTURACION_HTTP_SIM", matches = "true")
class FacturacionProveedorHttpTest {
  private final ObjectMapper json = new ObjectMapper();

  @Test void boletaYFacturaObtienenCdrYPdfSinReenviarAlConsultar() {
    var config = configuracion();
    var cliente = new ApisPeruFacturacionClient(config, json);
    for (String tipo : List.of("BOLETA", "FACTURA")) {
      var documento = documento(config, tipo, "SIM_ACEPTADO", numero());
      String codigo = tipo.equals("FACTURA") ? "01" : "03";
      String serie = tipo.equals("FACTURA") ? "FPRU" : "BPRU";
      long numero = json.readTree(documento.solicitud()).path("correlativo").asLong();
      var respuesta = cliente.enviar(documento.solicitud(), config.getRuc(), codigo, serie, numero);
      assertThat(respuesta.aceptado()).isTrue(); assertThat(respuesta.codigo()).isEqualTo("0");
      assertThat(respuesta.xml()).contains(serie + "-" + numero); assertThat(respuesta.cdrZip()).isNotBlank();
      assertThat(cliente.consultar(config.getRuc(), codigo, serie, numero).aceptado()).isTrue();
      assertThat(new String(cliente.pdf(documento.solicitud(), config.getRuc()), java.nio.charset.StandardCharsets.US_ASCII)).startsWith("%PDF-");
    }
  }

  @Test void timeoutDespuesDeGuardarSeResuelveConsultandoSinReenvio() {
    var config = configuracion(); config.setTimeoutMillis(500);
    var cliente = new ApisPeruFacturacionClient(config, json);
    long numero = numero();
    var doc = documento(config, "BOLETA", "SIM_TIMEOUT", numero);
    assertThatThrownBy(() -> cliente.enviar(doc.solicitud(), config.getRuc(), "03", "BPRU", numero))
        .isInstanceOfSatisfying(FacturacionProveedorException.class, e -> assertThat(e.isIncierto()).isTrue());
    assertThat(cliente.consultar(config.getRuc(), "03", "BPRU", numero).aceptado()).isTrue();
  }

  @Test void rechazoYAutenticacionNoSePresentanComoAceptacion() {
    var config = configuracion(); var cliente = new ApisPeruFacturacionClient(config, json);
    long numero = numero();
    var rechazo = documento(config, "BOLETA", "SIM_RECHAZO", numero);
    assertThat(cliente.enviar(rechazo.solicitud(), config.getRuc(), "03", "BPRU", numero).aceptado()).isFalse();
    var auth = documento(config, "BOLETA", "SIM_AUTH", numero + 1);
    assertThatThrownBy(() -> cliente.enviar(auth.solicitud(), config.getRuc(), "03", "BPRU", numero + 1))
        .isInstanceOfSatisfying(FacturacionProveedorException.class, e -> assertThat(e.isIncierto()).isFalse());
  }

  static long numero() { return System.currentTimeMillis() % 90_000_000 + 1; }

  static FacturacionApisPeruProperties configuracion() {
    var p = DocumentoFiscalBuilderTest.config();
    p.setHabilitada(true); p.setIdEmpresa(1L); p.setToken("token-facturacion-local");
    p.setUrl("http://127.0.0.1:4920/facturacion/api/v1");
    return p;
  }

  static DocumentoFiscalBuilder.Documento documento(FacturacionApisPeruProperties config, String tipo, String nombre, long numero) {
    var c = new Comprobante(); var tc = new TipoComprobante(); tc.setCodigo(tipo); c.setTipoComprobante(tc);
    c.setSerie(tipo.equals("FACTURA") ? "FPRU" : "BPRU"); c.setNumeroComprobante(Long.toString(numero));
    c.setFechaEmision(LocalDateTime.now(java.time.ZoneId.of("America/Lima")));
    c.setReceptorTipoDoc(tipo.equals("FACTURA") ? "6" : "1");
    c.setReceptorNumeroDoc(tipo.equals("FACTURA") ? "20601234567" : "45678912"); c.setReceptorNombre(nombre);
    c.setTotalPagar(new BigDecimal("118.00"));
    var l = new ComprobanteDetalle(); l.setNumeroLinea(1); l.setDescripcion("Alojamiento de prueba REBOLSYS");
    l.setCantidad(BigDecimal.ONE); l.setValorUnitario(new BigDecimal("118.00")); l.setIgv(BigDecimal.ZERO);
    var r = new EmisionElectronicaRequestDTO("beta-prueba-" + numero, 1L, new BigDecimal("18"),
        "Av. Bolognesi 123, Huaraz", List.of(), List.of());
    return new DocumentoFiscalBuilder(config, new ObjectMapper()).construir(c, List.of(l), BigDecimal.ZERO, r);
  }
}
