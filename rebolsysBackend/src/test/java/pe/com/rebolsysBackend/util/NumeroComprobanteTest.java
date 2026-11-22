package pe.com.rebolsysBackend.util;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class NumeroComprobanteTest {
  @Test void muestraSerieYCorrelativoDeOchoDigitos() {
    assertThat(NumeroComprobante.formatear("F002", "3848")).isEqualTo("F002-00003848");
    assertThat(NumeroComprobante.formatear("B002", "1")).isEqualTo("B002-00000001");
  }
  @Test void conservaIdentificadoresHistoricosSinTruncarlos() {
    assertThat(NumeroComprobante.formatear("FINT", "12")).isEqualTo("FINT-00000012");
    assertThat(NumeroComprobante.formatear("BINT", "123456789")).isEqualTo("BINT-123456789");
    assertThat(NumeroComprobante.formatear("F002", "00003848")).isEqualTo("F002-00003848");
  }
}
