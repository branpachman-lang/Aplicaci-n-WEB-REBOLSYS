package pe.com.rebolsysBackend.util;

/** Formato de presentación; no cambia la serie ni el correlativo persistidos. */
public final class NumeroComprobante {
  private NumeroComprobante() {}

  public static String formatear(String serie, String numero) {
    String correlativo = numero == null ? "" : numero;
    if (correlativo.matches("[0-9]{1,8}")) correlativo = "0".repeat(8 - correlativo.length()) + correlativo;
    return (serie == null ? "" : serie) + "-" + correlativo;
  }
}
