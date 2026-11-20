package pe.com.rebolsysBackend.service.impl;

import java.io.*;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.springframework.stereotype.Service;
import pe.com.rebolsysBackend.dto.TicketComprobanteDTO;

/** Representación local de 80 mm. No firma XML ni acredita aceptación de SUNAT. */
@Service
public class TicketPdfService {
  private static final float ANCHO = 80 * 72 / 25.4f;
  private static final float MARGEN = 11;
  private static final float PASO = 12;
  private static final java.nio.charset.Charset CARACTERES = java.nio.charset.Charset.forName("windows-1252");
  private record Renglon(String texto, boolean negrita) {}

  public byte[] generar(TicketComprobanteDTO datos) {
    try (var pdf = new PDDocument(); var salida = new ByteArrayOutputStream()) {
      // Cada solicitud posee sus fuentes y recursos PDF; no compartirlos entre documentos concurrentes.
      PDFont normal = new PDType1Font(Standard14Fonts.FontName.COURIER);
      PDFont negrita = new PDType1Font(Standard14Fonts.FontName.COURIER_BOLD);
      var c = datos.documento();
      var renglones = new ArrayList<Renglon>();
      agregar(renglones, Objects.requireNonNullElse(datos.hospedaje().nombre(), "HOSPEDAJE"), true);
      agregar(renglones, "RUC: " + Objects.requireNonNullElse(datos.hospedaje().ruc(), "Sin configurar"), false);
      agregar(renglones, datos.hospedaje().direccion(), false);
      agregar(renglones, "---------------------------------------", false);
      agregar(renglones, c.tipo() + " INTERNA", true);
      agregar(renglones, c.serie() + "-" + c.numero(), true);
      agregar(renglones, c.fechaEmision() == null ? "" : c.fechaEmision().format(DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm")), false);
      agregar(renglones, "Cliente: " + c.receptorNombre(), false);
      agregar(renglones, "Documento: " + c.receptorNumeroDoc(), false);
      if (c.numeroReserva() != null) agregar(renglones, "Reserva: " + c.numeroReserva(), false);
      if (c.idVenta() != null) agregar(renglones, "Venta: V" + c.idVenta(), false);
      agregar(renglones, "---------------------------------------", false);
      for (var linea : c.lineas()) {
        agregar(renglones, linea.descripcion(), false);
        var precio = linea.total().divide(linea.cantidad(), 2, java.math.RoundingMode.HALF_UP);
        agregar(renglones, linea.cantidad().stripTrailingZeros().toPlainString() + " x " + dinero(precio) + " = " + dinero(linea.total()), false);
      }
      agregar(renglones, "---------------------------------------", false);
      agregar(renglones, "Subtotal: " + dinero(datos.subtotal()), false);
      agregar(renglones, "IGV:      " + dinero(datos.igv()), false);
      agregar(renglones, "TOTAL:    " + dinero(c.total()), true);
      agregar(renglones, "Pagado:   " + dinero(c.pagado()), false);
      agregar(renglones, "Saldo:    " + dinero(c.saldo()), false);
      for (var p : c.pagos()) {
        agregar(renglones, "Pago #" + p.idPago() + " - " + dinero(p.monto()), false);
        if (p.numeroOperacion() != null) agregar(renglones, "Operacion: " + p.numeroOperacion(), false);
      }
      agregar(renglones, "---------------------------------------", false);
      agregar(renglones, "DOCUMENTO DE PRUEBA / USO INTERNO", true);
      agregar(renglones, "SIN VALIDEZ TRIBUTARIA DE PRODUCCION", false);
      agregar(renglones, "Gracias por su preferencia.", false);
      for (int desde = 0; desde < renglones.size(); desde += 1100) {
        var paginaLineas = renglones.subList(desde, Math.min(desde + 1100, renglones.size()));
        float alto = Math.max(180, 2 * MARGEN + PASO * paginaLineas.size());
        var pagina = new PDPage(new PDRectangle(ANCHO, alto));
        pdf.addPage(pagina);
        try (var contenido = new PDPageContentStream(pdf, pagina)) {
          contenido.beginText();
          contenido.setLeading(PASO);
          contenido.newLineAtOffset(MARGEN, alto - MARGEN - 9);
          for (var linea : paginaLineas) {
            contenido.setFont(linea.negrita() ? negrita : normal, 8.5f);
            contenido.showText(linea.texto());
            contenido.newLine();
          }
          contenido.endText();
        }
      }
      pdf.getDocumentInformation().setTitle(c.tipo() + " " + c.serie() + "-" + c.numero() + " - PRUEBA");
      pdf.save(salida);
      return salida.toByteArray();
    } catch (IOException ex) {
      throw new UncheckedIOException("No se pudo generar el ticket PDF", ex);
    }
  }

  private String dinero(BigDecimal valor) { return "S/ " + valor.setScale(2).toPlainString(); }

  private void agregar(List<Renglon> destino, String texto, boolean negrita) throws IOException {
    if (texto == null || texto.isBlank()) return;
    // No interpretar contenido del cliente como HTML; sustituir glifos no disponibles en la fuente térmica.
    var seguro = new StringBuilder();
    var caracteres = CARACTERES.newEncoder();
    texto.codePoints().forEach(cp -> {
      String caracter = Character.isISOControl(cp) ? " " : new String(Character.toChars(cp));
      seguro.append(caracteres.canEncode(caracter) ? caracter : "?");
    });
    String pendiente = seguro.toString();
    while (pendiente.length() > 0) {
      int limite = Math.min(39, pendiente.length());
      if (limite < pendiente.length()) {
        int espacio = pendiente.lastIndexOf(' ', limite);
        if (espacio > 15) limite = espacio;
      }
      destino.add(new Renglon(pendiente.substring(0, limite).strip(), negrita));
      pendiente = pendiente.substring(limite).stripLeading();
    }
  }
}
