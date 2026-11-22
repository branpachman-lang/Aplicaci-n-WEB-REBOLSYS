package pe.com.rebolsysBackend.service.impl;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.springframework.stereotype.Service;
import pe.com.rebolsysBackend.dto.TicketComprobanteDTO;

/** Representación interna en A4. No firma XML ni acredita aceptación de SUNAT. */
@Service
public class TicketPdfService {
  private static final float MARGEN = 42;
  private static final float ANCHO = PDRectangle.A4.getWidth() - 2 * MARGEN;
  private static final float PIE = 38;
  private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm");
  private static final java.nio.charset.Charset CARACTERES = java.nio.charset.Charset.forName("windows-1252");
  private static final DecimalFormat DINERO = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
  private static final java.awt.Color AZUL = new java.awt.Color(40, 32, 120);
  private static final java.awt.Color LILA = new java.awt.Color(239, 237, 250);
  private static final java.awt.Color GRIS = new java.awt.Color(225, 227, 233);
  private static final java.awt.Color TEXTO = new java.awt.Color(25, 25, 30);

  public byte[] generar(TicketComprobanteDTO datos) {
    try (var pdf = new PDDocument(); var salida = new ByteArrayOutputStream()) {
      PDFont normal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
      PDFont negrita = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
      var documento = datos.documento();
      var layout = new PaginaA4(pdf, normal, negrita, documento.tipo() + " INTERNA · " + documento.serie() + "-" + documento.numero());
      layout.encabezado(datos);
      layout.datosReceptor(datos);
      if (!documento.estadias().isEmpty()) layout.estadias(datos);
      layout.detalle(datos);
      layout.resumen(datos);
      layout.pagos(datos);
      layout.disclaimer();
      layout.cerrarPagina();
      pdf.getDocumentInformation().setTitle(documento.tipo() + " " + documento.serie() + "-" + documento.numero() + " - PRUEBA A4");
      pdf.save(salida);
      return salida.toByteArray();
    } catch (IOException ex) {
      throw new UncheckedIOException("No se pudo generar el comprobante PDF A4", ex);
    }
  }

  private static String dinero(BigDecimal valor) {
    synchronized (DINERO) {
      return "S/ " + DINERO.format(Objects.requireNonNullElse(valor, BigDecimal.ZERO));
    }
  }

  private static String fecha(java.time.LocalDateTime valor) {
    return valor == null ? "-" : valor.format(FECHA);
  }

  private static String textoSeguro(String texto) {
    if (texto == null) return "";
    var seguro = new StringBuilder();
    var codificador = CARACTERES.newEncoder();
    texto.codePoints().forEach(cp -> {
      String caracter = Character.isISOControl(cp) ? " " : new String(Character.toChars(cp));
      seguro.append(codificador.canEncode(caracter) ? caracter : "?");
    });
    return seguro.toString().replaceAll("\\s+", " ").trim();
  }

  private static final class PaginaA4 {
    private final PDDocument pdf;
    private final PDFont normal;
    private final PDFont negrita;
    private final String referencia;
    private final List<PDPage> paginas = new ArrayList<>();
    private PDPage pagina;
    private PDPageContentStream lienzo;
    private float y;

    PaginaA4(PDDocument pdf, PDFont normal, PDFont negrita, String referencia) throws IOException {
      this.pdf = pdf;
      this.normal = normal;
      this.negrita = negrita;
      this.referencia = textoSeguro(referencia);
      nuevaPagina(false);
    }

    private void nuevaPagina(boolean continuacion) throws IOException {
      if (lienzo != null) cerrarPagina();
      pagina = new PDPage(PDRectangle.A4);
      pdf.addPage(pagina);
      paginas.add(pagina);
      lienzo = new PDPageContentStream(pdf, pagina);
      y = pagina.getMediaBox().getHeight() - MARGEN;
      lienzo.setNonStrokingColor(AZUL);
      lienzo.addRect(MARGEN, y - 5, ANCHO, 5);
      lienzo.fill();
      y -= 24;
      if (continuacion) {
        escribir("Comprobante interno · " + referencia + " · continuación", MARGEN, y, normal, 9, new java.awt.Color(90, 90, 100));
        y -= 22;
      }
    }

    void encabezado(TicketComprobanteDTO datos) throws IOException {
      var marca = datos.hospedaje();
      escribir(ajustar(Objects.requireNonNullElse(marca.nombre(), "HOSPEDAJE"), negrita, 19, ANCHO - 220), MARGEN, y - 2, negrita, 19, AZUL);
      escribir("RUC: " + Objects.requireNonNullElse(marca.ruc(), "Sin configurar"), MARGEN, y - 19, normal, 9, TEXTO);
      if (marca.direccion() != null && !marca.direccion().isBlank())
        escribir(ajustar(marca.direccion(), normal, 9, ANCHO - 220), MARGEN, y - 33, normal, 9, TEXTO);

      float x = MARGEN + ANCHO - 190;
      lienzo.setStrokingColor(TEXTO);
      lienzo.addRect(x, y - 70, 190, 70);
      lienzo.stroke();
      centrar(datos.documento().tipo() + " INTERNA", x, y - 24, 190, negrita, 15, AZUL);
      centrar(datos.documento().serie() + "-" + datos.documento().numero(), x, y - 43, 190, negrita, 12, TEXTO);
      centrar("MONEDA: PEN", x, y - 59, 190, normal, 8, TEXTO);
      y -= 88;
    }

    void datosReceptor(TicketComprobanteDTO datos) throws IOException {
      var c = datos.documento();
      float alto = 75;
      caja(MARGEN, y - alto, ANCHO, alto, java.awt.Color.WHITE);
      escribir("DATOS DEL RECEPTOR", MARGEN + 12, y - 17, negrita, 9, AZUL);
      escribir(ajustar(c.receptorNombre(), normal, 10, ANCHO * .54f), MARGEN + 12, y - 34, negrita, 10, TEXTO);
      escribir("RUC / documento: " + Objects.requireNonNullElse(c.receptorNumeroDoc(), "No registrado"), MARGEN + 12, y - 51, normal, 9, TEXTO);

      float x = MARGEN + ANCHO * .61f;
      escribir("Fecha de emisión", x, y - 19, normal, 9, TEXTO);
      escribir(fecha(c.fechaEmision()), x + 93, y - 19, negrita, 9, TEXTO);
      escribir(c.numeroReserva() == null ? "Venta" : "Reserva", x, y - 38, normal, 9, TEXTO);
      escribir(Objects.requireNonNullElse(c.numeroReserva(), c.idVenta() == null ? "-" : "V" + c.idVenta()), x + 93, y - 38, negrita, 9, TEXTO);
      escribir("Estado", x, y - 57, normal, 9, TEXTO);
      escribir(c.saldo().signum() <= 0 ? "PAGADO" : "PENDIENTE DE PAGO", x + 93, y - 57, negrita, 9, TEXTO);
      y -= alto + 18;
    }

    void estadias(TicketComprobanteDTO datos) throws IOException {
      tituloSeccion("DATOS DE LA ESTADÍA");
      for (var estadia : datos.documento().estadias()) {
        asegurarEspacio(38, false);
        lienzo.setNonStrokingColor(LILA);
        lienzo.addRect(MARGEN, y - 31, ANCHO, 31);
        lienzo.fill();
        escribir("Habitación " + textoSeguro(estadia.habitacion()), MARGEN + 10, y - 13, negrita, 9, TEXTO);
        escribir("Check-in: " + fecha(estadia.fechaIngreso()), MARGEN + 210, y - 13, normal, 9, TEXTO);
        escribir("Check-out: " + fecha(estadia.fechaSalida()), MARGEN + 370, y - 13, normal, 9, TEXTO);
        y -= 37;
      }
      y -= 7;
    }

    void detalle(TicketComprobanteDTO datos) throws IOException {
      tituloSeccion("DETALLE DE " + ("FACTURA".equals(datos.documento().tipo()) ? "CONSUMO" : "SERVICIOS"));
      cabeceraTabla();
      for (var linea : datos.documento().lineas()) {
        BigDecimal cantidad = Objects.requireNonNullElse(linea.cantidad(), BigDecimal.ZERO);
        BigDecimal importe = Objects.requireNonNullElse(linea.total(), BigDecimal.ZERO);
        BigDecimal unitario = cantidad.signum() == 0 ? importe : importe.divide(cantidad, 2, RoundingMode.HALF_UP);
        String descripcion = textoSeguro(linea.descripcion());
        var fragmentos = envolver(descripcion, normal, 9, 238);
        float alto = Math.max(31, 12 * fragmentos.size() + 12);
        asegurarEspacio(alto, true);
        float parteSuperior = y;
        lienzo.setStrokingColor(GRIS);
        lienzo.addRect(MARGEN, y - alto, ANCHO, alto);
        lienzo.stroke();
        float yy = parteSuperior - 16;
        for (var fragmento : fragmentos) {
          escribir(fragmento, MARGEN + 8, yy, normal, 9, TEXTO);
          yy -= 12;
        }
        escribir(cantidad.stripTrailingZeros().toPlainString(), MARGEN + 280, parteSuperior - 16, normal, 9, TEXTO);
        escribirDerecha(dinero(unitario), MARGEN + 433, parteSuperior - 16, normal, 9, TEXTO);
        escribirDerecha(dinero(importe), MARGEN + ANCHO - 8, parteSuperior - 16, normal, 9, TEXTO);
        y -= alto;
      }
      y -= 12;
    }

    void resumen(TicketComprobanteDTO datos) throws IOException {
      float alto = datos.documento().saldo().signum() > 0 ? 102 : 66;
      asegurarEspacio(alto + 8, false);
      float ancho = 238;
      float x = MARGEN + ANCHO - ancho;
      caja(x, y - alto, ancho, alto, LILA);
      float yy = y - 17;
      filaTotal(x + 10, x + ancho - 10, yy, "Valor de venta", datos.subtotal(), false);
      filaTotal(x + 10, x + ancho - 10, yy - 16, "IGV", datos.igv(), false);
      lienzo.setStrokingColor(new java.awt.Color(170, 170, 180));
      lienzo.moveTo(x + 8, yy - 23);
      lienzo.lineTo(x + ancho - 8, yy - 23);
      lienzo.stroke();
      filaTotal(x + 10, x + ancho - 10, yy - 39, "TOTAL", datos.documento().total(), true);
      if (datos.documento().saldo().signum() > 0) {
        filaTotal(x + 10, x + ancho - 10, yy - 57, "Pagado", datos.documento().pagado(), false);
        filaTotal(x + 10, x + ancho - 10, yy - 73, "Saldo pendiente", datos.documento().saldo(), false);
      }
      y -= alto + 14;
    }

    void pagos(TicketComprobanteDTO datos) throws IOException {
      asegurarEspacio(54, false);
      tituloSeccion("CONDICIÓN Y MEDIOS DE PAGO");
      String estado = datos.documento().saldo().signum() <= 0 ? "PAGADO" : "PENDIENTE DE PAGO";
      escribir("Estado: " + estado, MARGEN, y, negrita, 9, TEXTO);
      y -= 16;
      var pagos = datos.documento().pagos().stream().filter(pe.com.rebolsysBackend.dto.ReservaPagoRespuestaDTO::verificado).toList();
      if (pagos.isEmpty()) {
        escribir("Medio de pago: Por registrar", MARGEN, y, normal, 9, TEXTO);
        y -= 14;
      } else {
        for (var pago : pagos) {
          asegurarEspacio(15, false);
          escribir(Objects.requireNonNullElse(pago.medioPago(), "Medio no identificado"), MARGEN, y, normal, 9, TEXTO);
          escribirDerecha(dinero(pago.monto()), MARGEN + ANCHO, y, normal, 9, TEXTO);
          y -= 14;
        }
      }
      y -= 4;
    }

    void disclaimer() throws IOException {
      asegurarEspacio(37, false);
      caja(MARGEN, y - 31, ANCHO, 31, LILA);
      escribir("DOCUMENTO DE PRUEBA / USO INTERNO. SIN VALIDEZ TRIBUTARIA DE PRODUCCIÓN.", MARGEN + 9, y - 19, negrita, 8, AZUL);
      y -= 40;
    }

    private void tituloSeccion(String titulo) throws IOException {
      asegurarEspacio(26, false);
      escribir(titulo, MARGEN, y - 10, negrita, 10, AZUL);
      lienzo.setStrokingColor(GRIS);
      lienzo.moveTo(MARGEN, y - 15);
      lienzo.lineTo(MARGEN + ANCHO, y - 15);
      lienzo.stroke();
      y -= 25;
    }

    private void cabeceraTabla() throws IOException {
      asegurarEspacio(29, false);
      lienzo.setNonStrokingColor(AZUL);
      lienzo.addRect(MARGEN, y - 26, ANCHO, 26);
      lienzo.fill();
      escribir("Descripción", MARGEN + 8, y - 17, negrita, 9, java.awt.Color.WHITE);
      escribir("Cantidad", MARGEN + 277, y - 17, negrita, 8, java.awt.Color.WHITE);
      escribirDerecha("P. unitario", MARGEN + 433, y - 17, negrita, 8, java.awt.Color.WHITE);
      escribirDerecha("Importe", MARGEN + ANCHO - 8, y - 17, negrita, 8, java.awt.Color.WHITE);
      y -= 26;
    }

    private void filaTotal(float x, float derecho, float baseline, String etiqueta, BigDecimal valor, boolean fuerte) throws IOException {
      escribir(etiqueta, x, baseline, fuerte ? negrita : normal, fuerte ? 10 : 9, TEXTO);
      escribirDerecha(dinero(valor), derecho, baseline, fuerte ? negrita : normal, fuerte ? 10 : 9, TEXTO);
    }

    private void asegurarEspacio(float alto, boolean repetirTabla) throws IOException {
      if (y - alto < PIE + MARGEN) {
        nuevaPagina(true);
        if (repetirTabla) cabeceraTabla();
      }
    }

    private void caja(float x, float y, float ancho, float alto, java.awt.Color relleno) throws IOException {
      lienzo.setNonStrokingColor(relleno);
      lienzo.addRect(x, y, ancho, alto);
      lienzo.fill();
      lienzo.setStrokingColor(new java.awt.Color(150, 150, 160));
      lienzo.addRect(x, y, ancho, alto);
      lienzo.stroke();
    }

    private void centrar(String texto, float x, float baseline, float ancho, PDFont fuente, float size, java.awt.Color color) throws IOException {
      String seguro = textoSeguro(texto);
      float w = fuente.getStringWidth(seguro) / 1000 * size;
      escribir(seguro, x + Math.max(4, (ancho - w) / 2), baseline, fuente, size, color);
    }

    private void escribirDerecha(String texto, float derecho, float baseline, PDFont fuente, float size, java.awt.Color color) throws IOException {
      String seguro = textoSeguro(texto);
      float w = fuente.getStringWidth(seguro) / 1000 * size;
      escribir(seguro, derecho - w, baseline, fuente, size, color);
    }

    private void escribir(String texto, float x, float baseline, PDFont fuente, float size, java.awt.Color color) throws IOException {
      lienzo.beginText();
      lienzo.setNonStrokingColor(color);
      lienzo.setFont(fuente, size);
      lienzo.newLineAtOffset(x, baseline);
      lienzo.showText(textoSeguro(texto));
      lienzo.endText();
    }

    private List<String> envolver(String texto, PDFont fuente, float size, float ancho) throws IOException {
      if (texto.isBlank()) return List.of("-");
      var resultado = new ArrayList<String>();
      var actual = new StringBuilder();
      for (var palabra : texto.split(" ")) {
        String prueba = actual.isEmpty() ? palabra : actual + " " + palabra;
        if (!actual.isEmpty() && fuente.getStringWidth(prueba) / 1000 * size > ancho) {
          resultado.add(actual.toString());
          actual.setLength(0);
          actual.append(palabra);
        } else {
          actual.setLength(0);
          actual.append(prueba);
        }
      }
      if (!actual.isEmpty()) resultado.add(actual.toString());
      return resultado;
    }

    private String ajustar(String valor, PDFont fuente, float size, float max) throws IOException {
      String seguro = textoSeguro(valor);
      if (fuente.getStringWidth(seguro) / 1000 * size <= max) return seguro;
      String recortado = seguro;
      while (!recortado.isEmpty() && fuente.getStringWidth(recortado + "…") / 1000 * size > max) {
        recortado = recortado.substring(0, recortado.length() - 1);
      }
      return recortado + "...";
    }

    void cerrarPagina() throws IOException {
      if (lienzo == null) return;
      float pie = MARGEN - 10;
      lienzo.setStrokingColor(GRIS);
      lienzo.moveTo(MARGEN, pie + 17);
      lienzo.lineTo(MARGEN + ANCHO, pie + 17);
      lienzo.stroke();
      escribir("Hospedaje Real Bolognesi · Comprobante interno de prueba", MARGEN, pie, normal, 8, new java.awt.Color(90, 90, 100));
      escribirDerecha("Página " + paginas.size(), MARGEN + ANCHO, pie, normal, 8, new java.awt.Color(90, 90, 100));
      lienzo.close();
      lienzo = null;
    }
  }
}
