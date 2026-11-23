package pe.com.rebolsysBackend.service.impl;

import jakarta.mail.MessagingException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import pe.com.rebolsysBackend.dto.*;
import pe.com.rebolsysBackend.exception.ServicioExternoException;
import pe.com.rebolsysBackend.util.NumeroComprobante;

/** SMTP se ejecuta después de leer una copia del documento, sin bloqueos sobre caja o comprobantes. */
@Service
@RequiredArgsConstructor
public class ComprobanteCorreoService {
  private final ComprobanteDocumentoService documentos;
  private final TicketPdfService tickets;
  private final ObjectProvider<JavaMailSender> transporte;
  @Value("${app.correo.habilitado:false}") private boolean habilitado;
  @Value("${app.correo.remitente:}") private String remitente;
  @Value("${spring.mail.host:}") private String servidor;

  public boolean disponible() {
    return habilitado && !remitente.isBlank() && !servidor.isBlank() && transporte.getIfAvailable() != null;
  }

  public Map<String, Object> destino(Long id) {
    var datos = documentos.obtener(id, false);
    return Map.of("correo", datos.correoReceptor() == null ? "" : datos.correoReceptor(),
        "correoHabilitado", disponible());
  }

  public void enviar(Long id, ComprobanteCorreoDTO solicitud) {
    var datos = documentos.obtener(id, true);
    if (!disponible()) throw new ServicioExternoException(HttpStatus.SERVICE_UNAVAILABLE,
        "El envío de comprobantes por correo no está configurado. Configura el servidor SMTP del hospedaje.");
    var correo = transporte.getObject();
    var doc = datos.documento();
    String numero = NumeroComprobante.formatear(doc.serie(), doc.numero());
    try {
      var mensaje = correo.createMimeMessage();
      var contenido = new MimeMessageHelper(mensaje, true, StandardCharsets.UTF_8.name());
      contenido.setValidateAddresses(true);
      contenido.setFrom(remitente, datos.hospedaje().nombre() == null ? "Hospedaje" : datos.hospedaje().nombre());
      contenido.setTo(solicitud.correo().strip());
      contenido.setSubject(doc.tipo() + " " + numero + " - comprobante de prueba");
      contenido.setText("Estimado/a " + doc.receptorNombre() + ":\n\n"
          + "Adjuntamos su " + doc.tipo().toLowerCase(java.util.Locale.ROOT) + " " + numero
          + " por S/ " + doc.total().toPlainString() + ".\n\n"
          + "Documento de prueba / uso interno. Sin validez tributaria de producción.\n"
          + "Gracias por su preferencia.");
      contenido.addAttachment("PRUEBA-" + doc.tipo() + "-" + numero + ".pdf",
          new ByteArrayResource(tickets.generar(datos)), "application/pdf");
      correo.send(mensaje);
    } catch (MailException ex) {
      throw new ServicioExternoException(HttpStatus.BAD_GATEWAY,
          "No se pudo confirmar el envío. Revisa el correo recibido antes de volver a enviarlo.");
    } catch (MessagingException | UnsupportedEncodingException ex) {
      throw new ServicioExternoException(HttpStatus.BAD_REQUEST,
          "No se pudo preparar el correo. Revisa el destinatario y el remitente configurado.");
    }
  }
}
