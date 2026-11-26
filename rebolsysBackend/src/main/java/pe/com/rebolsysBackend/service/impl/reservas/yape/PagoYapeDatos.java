package pe.com.rebolsysBackend.service.impl.reservas.yape;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Modelos y tipos compartidos para el flujo de cobro con Yape vía Mercado Pago o simulador. */
public final class PagoYapeDatos {
  private PagoYapeDatos() {}

  public enum Modo { SIMULADO, MERCADO_PAGO }

  public enum Ambiente { PRUEBA, PRODUCCION }

  public enum Estado { ENVIANDO, PENDIENTE, APLICADO, RECHAZADO, REVISION }

  public record Configuracion(boolean habilitado, Modo modo, Ambiente ambiente) {}

  public record Intento(
      UUID claveOperacion,
      long idReserva,
      long idUsuario,
      long idTurno,
      BigDecimal importe,
      String telefonoEnmascarado,
      Modo modo,
      Ambiente ambiente,
      Estado estado,
      String ordenProveedor,
      Long idPago,
      String mensajeProveedor,
      Instant creado) {
    public Respuesta respuesta() {
      return new Respuesta(
          claveOperacion,
          importe,
          telefonoEnmascarado,
          modo,
          ambiente,
          estado,
          ordenProveedor,
          idPago,
          mensajeProveedor,
          creado);
    }
  }

  public record Respuesta(
      UUID claveOperacion,
      BigDecimal importe,
      String telefonoEnmascarado,
      Modo modo,
      Ambiente ambiente,
      Estado estado,
      String ordenProveedor,
      Long idPago,
      String mensajeProveedor,
      Instant creado) {}

  record Resultado(
      UUID referencia,
      String orden,
      Estado estado,
      BigDecimal importe,
      String pagoProveedor,
      String mensaje) {}

  static String enmascararTelefono(String telefono) {
    return "*** *** " + telefono.substring(telefono.length() - 3);
  }
}
