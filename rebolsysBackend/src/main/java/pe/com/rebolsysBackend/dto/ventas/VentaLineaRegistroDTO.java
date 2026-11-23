package pe.com.rebolsysBackend.dto.ventas;

import jakarta.validation.constraints.*;
import java.util.*;

public record VentaLineaRegistroDTO(
    @NotNull @Positive Long idProducto, @NotNull @Min(1) @Max(999999999) Integer cantidad,
    @PositiveOrZero @Digits(integer=8,fraction=2) java.math.BigDecimal precioEsperado) {
  public VentaLineaRegistroDTO(Long idProducto,Integer cantidad){this(idProducto,cantidad,null);}
}
