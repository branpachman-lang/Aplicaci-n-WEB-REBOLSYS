package pe.com.rebolsysBackend.entity.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import pe.com.rebolsysBackend.enums.EstadoHabitacion;

@Converter
public class EstadoHabitacionConverter implements AttributeConverter<EstadoHabitacion, String> {
  @Override
  public String convertToDatabaseColumn(EstadoHabitacion estado) {
    if (estado == null) return null;
    return estado.name();
  }

  @Override
  public EstadoHabitacion convertToEntityAttribute(String valor) {
    if (valor == null) return null;
    return EstadoHabitacion.valueOf(valor);
  }
}
