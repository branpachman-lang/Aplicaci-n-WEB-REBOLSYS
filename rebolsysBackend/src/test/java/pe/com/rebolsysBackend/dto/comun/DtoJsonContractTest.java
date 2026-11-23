package pe.com.rebolsysBackend.dto.comun;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.json.JsonMapper;

class DtoJsonContractTest {
  private final JsonMapper mapper = JsonMapper.builder().build();

  @TestFactory
  Stream<DynamicTest> conservaElJsonAnteriorALaMigracion() throws IOException {
    // Ejemplos capturados antes de convertir las clases a records.
    var recurso = getClass().getResourceAsStream("/dto/contratos-json.json");
    assertThat(recurso).isNotNull();
    try (recurso) {
      var casos = mapper.readTree(recurso);
      return StreamSupport.stream(casos.spliterator(), false)
          .map(caso -> DynamicTest.dynamicTest(caso.get("nombre").asString(), () -> {
            var tipo = Class.forName(caso.get("tipo").asString());
            var dto = mapper.readValue(caso.get("entrada").toString(), tipo);
            // Se preserva también el orden para las firmas de solicitudes existentes.
            assertThat(mapper.writeValueAsString(dto)).isEqualTo(caso.get("salida").asString());
          }));
    }
  }
}
