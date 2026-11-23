package pe.com.rebolsysBackend.service.impl.comun.comprobantes;

import static org.assertj.core.api.Assertions.*;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import pe.com.rebolsysBackend.config.FacturacionApisPeruProperties;
import tools.jackson.databind.ObjectMapper;

/** Prueba optativa autorizada: crea dos documentos ficticios únicamente en la empresa beta. */
@EnabledIfEnvironmentVariable(named = "RUN_FACTURACION_BETA_REAL", matches = "true")
class FacturacionBetaVivaTest {
  @Test void emiteBoletaYFacturaBetaConIgvIncluido() throws Exception {
    var propiedades = new Properties();
    try (Reader reader = Files.newBufferedReader(Path.of(".local/facturacion-beta-private.properties"), StandardCharsets.UTF_8)) {
      propiedades.load(reader);
    }
    var config = new FacturacionApisPeruProperties();
    config.setHabilitada(true); config.setToken(valor(propiedades, "token"));
    config.setIdEmpresa(Long.valueOf(valor(propiedades, "id-empresa")));
    config.setRuc(valor(propiedades, "ruc")); config.setRazonSocial(valor(propiedades, "razon-social"));
    config.setDireccion(valor(propiedades, "direccion")); config.setUbigeo(valor(propiedades, "ubigeo"));
    config.setDepartamento(valor(propiedades, "departamento")); config.setProvincia(valor(propiedades, "provincia"));
    config.setDistrito(valor(propiedades, "distrito")); config.setTimeoutMillis(30000);
    assertThat(config.pendientes()).isEmpty();
    var json = new ObjectMapper(); var cliente = new ApisPeruFacturacionClient(config, json);
    var destino = Path.of(".local/facturacion-beta-demostracion"); Files.createDirectories(destino);
    var resumen = new ArrayList<Map<String, Object>>();
    for (String tipo : List.of("BOLETA", "FACTURA")) {
      long numero = FacturacionProveedorHttpTest.numero();
      String serie = tipo.equals("FACTURA") ? "FPRU" : "BPRU";
      var documento = FacturacionProveedorHttpTest.documento(config, tipo,
          tipo.equals("FACTURA") ? "Comercial Andina de Prueba SAC" : "María Elena Quispe Ramos", numero);
      assertThat(documento.subtotal()).isEqualByComparingTo("100.00");
      assertThat(documento.igv()).isEqualByComparingTo("18.00");
      var respuesta = cliente.enviar(documento.solicitud(), config.getRuc(), tipo.equals("FACTURA") ? "01" : "03", serie, numero);
      // Se registra la respuesta antes de afirmar aceptación para poder consultar un resultado incierto.
      Files.writeString(destino.resolve(serie + "-" + numero + "-respuesta.json"), json.writeValueAsString(respuesta));
      assertThat(respuesta.aceptado()).as("CDR de %s: %s", tipo, respuesta.mensaje()).isTrue();
      assertThat(respuesta.codigo()).isEqualTo("0"); assertThat(respuesta.xml()).isNotBlank();
      assertThat(respuesta.cdrZip()).isNotBlank();
      Files.writeString(destino.resolve(serie + "-" + numero + ".xml"), respuesta.xml());
      Files.write(destino.resolve(serie + "-" + numero + ".zip"), Base64.getDecoder().decode(respuesta.cdrZip()));
      byte[] pdf = cliente.pdf(documento.solicitud(), config.getRuc());
      try (var archivo = Loader.loadPDF(pdf)) { assertThat(archivo.getNumberOfPages()).isPositive(); }
      Files.write(destino.resolve(serie + "-" + numero + ".pdf"), pdf);
      resumen.add(Map.of("tipo", tipo, "serie", serie, "numero", numero, "ambiente", "BETA", "estado", "ACEPTADO",
          "subtotal", documento.subtotal(), "igv", documento.igv(), "total", documento.total()));
      Files.writeString(destino.resolve("resumen.json"), json.writeValueAsString(resumen));
    }
  }

  private static String valor(Properties p, String clave) {
    return p.getProperty("facturacion.apisperu." + clave, "").trim();
  }
}
