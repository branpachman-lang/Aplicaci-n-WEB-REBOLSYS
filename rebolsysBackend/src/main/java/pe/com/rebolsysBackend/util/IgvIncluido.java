package pe.com.rebolsysBackend.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.stream.Stream;

/** Desglosa precios finales sin modificar el importe cobrado. Redondea por línea. */
public final class IgvIncluido {
  public static final BigDecimal TASA = new BigDecimal("18");
  private static final BigDecimal FACTOR = new BigDecimal("1.18");

  private IgvIncluido() {}

  public static BigDecimal base(BigDecimal total) {
    return total.divide(FACTOR, 2, RoundingMode.HALF_UP);
  }

  public record Importes(BigDecimal subtotal, BigDecimal igv, BigDecimal total) {}

  public static Importes resumir(Stream<BigDecimal> lineas) {
    return lineas.map(total -> {
      var bruto = total.setScale(2, RoundingMode.HALF_UP);
      var subtotal = base(bruto);
      return new Importes(subtotal, bruto.subtract(subtotal), bruto);
    }).reduce(new Importes(new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.00")),
        (a, b) -> new Importes(a.subtotal().add(b.subtotal()), a.igv().add(b.igv()), a.total().add(b.total())));
  }
}
