package pe.com.rebolsysBackend.config.json;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

/** Usa cero al omitir el campo y conserva un null explícito para su validación con @NotNull. */
public class EnteroCeroSiAusenteDeserializer extends StdDeserializer<Integer> {
  public EnteroCeroSiAusenteDeserializer() {
    super(Integer.class);
  }

  @Override
  public Integer deserialize(JsonParser parser, DeserializationContext context) {
    return _parseInteger(parser, context, Integer.class);
  }

  @Override
  public Object getAbsentValue(DeserializationContext context) {
    return 0;
  }
}
