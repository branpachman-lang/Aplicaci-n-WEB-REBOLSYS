package pe.com.rebolsysBackend.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import pe.com.rebolsysBackend.config.MockMvcReglasNegocio;
import pe.com.rebolsysBackend.dto.*;
import pe.com.rebolsysBackend.entity.*;
import pe.com.rebolsysBackend.entity.id.SerieComprobanteId;
import pe.com.rebolsysBackend.enums.*;
import pe.com.rebolsysBackend.repository.EmisionElectronicaRepository;
import pe.com.rebolsysBackend.service.service.*;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:entrega;MODE=Oracle;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false",
    "springdoc.api-docs.version=OPENAPI_3_0", "app.correo.habilitado=true",
    "app.correo.remitente=hospedaje@example.test", "spring.mail.host=127.0.0.1"
})
@MockMvcReglasNegocio
@ActiveProfiles("entrega-test")
class ComprobanteEntregaTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired EntityManager em;
  @Autowired TransactionTemplate tx;
  @Autowired EmisionElectronicaService emisiones;
  @Autowired EmisionElectronicaRepository repositorioEmisiones;
  @Autowired ComprobanteCorreoService correos;
  @MockitoBean JavaMailSender smtp;
  @MockitoBean(name = "apisPeruRestClient") RestClient consultaDocumentos;
  @MockitoBean ProveedorFacturacionClient proveedor;
  private static final AtomicLong NUMERO = new AtomicLong();
  Long comprobante, usuario;

  @BeforeEach void preparar() {
    when(smtp.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
    tx.executeWithoutResult(s -> {
      String clave = UUID.randomUUID().toString().substring(0, 8);
      var td = new TipoDocumento(); td.setNombreDocumento("DNI-" + clave); td.setCodigoSunat("1"); em.persist(td);
      var p = new Perfil(); p.setTipoDocumento(td); p.setNumeroDocumento(clave); em.persist(p);
      var n = new PersonaNatural(); n.setPerfil(p); n.setNombres("María"); n.setApellidos("Pérez");
      n.setFechaNacimiento(LocalDate.of(1990, 1, 1)); em.persist(n);
      var e = new Empleado(); e.setPersonaNatural(n); e.setFechaContratacion(LocalDate.now()); em.persist(e);
      var u = new Usuario(); u.setEmpleado(e); u.setUsername("emisor-" + clave); u.setEstadoUsuario(EstadoUsuario.ACTIVO); em.persist(u); usuario = u.getIdUsuario();
      var t = new TurnoCaja(); t.setUsuario(u); t.setEstadoSesion(1); em.persist(t);
      var ruc = new TipoDocumento(); ruc.setNombreDocumento("RUC-" + clave); ruc.setCodigoSunat("6"); em.persist(ruc);
      var empresa = new Perfil(); empresa.setTipoDocumento(ruc); empresa.setNumeroDocumento("20601234567");
      empresa.setCorreo("cliente@example.test"); empresa.setEsCliente(1); em.persist(empresa);
      var juridica = new PersonaJuridica(); juridica.setPerfil(empresa); juridica.setRazonSocial("Comercial Española SAC");
      juridica.setDireccionFiscal("Av. Los Álamos 123"); em.persist(juridica);
      var th = new TipoHabitacion(); th.setNombreTipo("Simple-" + clave); th.setCapacidadMax(1); th.setPrecioBase(new BigDecimal("118")); em.persist(th);
      var h = new Habitacion(); h.setNumeroCuarto(clave); h.setTipoHabitacion(th); h.setCapacidadPersonas(1); h.setPlazasCama(1);
      h.setEstadoHabitacion(EstadoHabitacion.DISPONIBLE); em.persist(h);
      var r = new Reserva(); r.setCliente(empresa); r.setUsuario(u); r.setClaveRegistro(clave); r.setFirmaSolicitud("fixture");
      var d = new DetalleReserva(); d.setHabitacion(h); d.setFechaIngreso(LocalDateTime.now().minusDays(2));
      d.setFechaSalida(LocalDateTime.now().minusDays(1)); d.setCostoEstadia(new BigDecimal("118")); d.setEstadoDetalle(EstadoReserva.FINALIZADA);
      r.agregarDetalle(d); em.persist(r);
      var tipos = em.createQuery("from TipoComprobante where codigo='FACTURA'", TipoComprobante.class).getResultList();
      TipoComprobante tipo;
      if (tipos.isEmpty()) {
        tipo = new TipoComprobante(); tipo.setCodigo("FACTURA"); tipo.setNombreComprobante("Factura"); em.persist(tipo);
        var serie = new SerieComprobante(); serie.setTipoComprobante(tipo); serie.setId(new SerieComprobanteId(tipo.getIdTipoComprobante(), "FINT")); em.persist(serie);
      } else tipo = tipos.getFirst();
      var c = new Comprobante(); c.setReserva(r); c.setCliente(empresa); c.setTurnoCaja(t); c.setTipoComprobante(tipo); c.setSerie("FINT");
      c.setNumeroComprobante(String.valueOf(NUMERO.incrementAndGet())); c.setFechaEmision(ReglasPropuesta.ahora());
      c.setSubtotal(new BigDecimal("118")); c.setIgv(BigDecimal.ZERO); c.setTotalPagar(new BigDecimal("118"));
      c.setReceptorNombre(juridica.getRazonSocial()); c.setReceptorNumeroDoc(empresa.getNumeroDocumento()); c.setReceptorTipoDoc("6");
      c.setEstadoComprobante(EstadoComprobante.EMITIDO); em.persist(c); comprobante = c.getIdComprobante();
      var l = new ComprobanteDetalle(); l.setComprobante(c); l.setNumeroLinea(1); l.setDetalleReserva(d);
      l.setDescripcion("Alojamiento habitación " + clave + " con baño privado y atención en recepción");
      l.setCantidad(BigDecimal.ONE); l.setValorUnitario(new BigDecimal("118")); l.setIgv(BigDecimal.ZERO); em.persist(l);
      var f = new FormaPago(); f.setNombrePago("Efectivo-" + clave); em.persist(f);
      var pago = new Pago(); pago.setComprobante(c); pago.setUsuario(u); pago.setTurnoCaja(t); pago.setFormaPago(f);
      pago.setMonto(new BigDecimal("118")); pago.setVerificado(1); pago.setClaveIdempotencia("pago-" + clave); em.persist(pago);
      if (em.createQuery("select count(h) from Hospedaje h", Long.class).getSingleResult() == 0) {
        var hospedaje = new Hospedaje(); hospedaje.setNombre("Hospedaje Real Bolognesi"); hospedaje.setRuc("20123456789");
        hospedaje.setDireccion("Av. Bolognesi 123, Juliaca"); em.persist(hospedaje);
      }
    });
  }

  private String ruta(String sufijo) { return "/api/v1/comprobantes/" + comprobante + sufijo; }
  private EmisionElectronicaRequestDTO solicitud() {
    return new EmisionElectronicaRequestDTO(UUID.randomUUID().toString(), usuario, new BigDecimal("18"), "Av. Los Álamos 123", List.of(), List.of());
  }

  @Test void simulaSinProveedorConImpuestoIncluidoYSinFalsaAceptacionFiscal() throws Exception {
    var solicitud = solicitud();
    var primera = emisiones.simular(comprobante, solicitud);
    assertThat(emisiones.simular(comprobante, solicitud).idEmision()).isEqualTo(primera.idEmision());
    assertThat(primera.estado()).isEqualTo(EstadoEmisionElectronica.PREPARADO);
    assertThat(primera.codigoRespuesta()).isEqualTo("SIMULACION_LOCAL");
    assertThat(primera.subtotal()).isEqualByComparingTo("100.00");
    assertThat(primera.igv()).isEqualByComparingTo("18.00");
    assertThat(primera.xmlDisponible()).isFalse(); assertThat(primera.cdrDisponible()).isFalse();
    assertThatThrownBy(() -> emisiones.enviar(comprobante, new OperacionUsuarioDTO(usuario))).hasMessageContaining("no se puede enviar");
    assertThatThrownBy(() -> emisiones.archivo(comprobante, "xml")).hasMessageContaining("no tiene archivos fiscales");
    verifyNoInteractions(proveedor);
  }

  @Test void ticketPdfA4ContieneReceptorTotalesYAdvertencia() throws Exception {
    emisiones.simular(comprobante, solicitud());
    var respuesta = mvc.perform(get(ruta("/ticket"))).andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_PDF)).andExpect(header().string("Cache-Control", "no-store"))
        .andReturn().getResponse().getContentAsByteArray();
    try (var pdf = Loader.loadPDF(respuesta)) {
      assertThat(pdf.getPage(0).getMediaBox().getWidth() * 25.4 / 72).isCloseTo(210, within(0.01));
      assertThat(pdf.getPage(0).getMediaBox().getHeight() * 25.4 / 72).isCloseTo(297, within(0.01));
      String texto = new PDFTextStripper().getText(pdf);
      assertThat(texto).contains("FACTURA INTERNA", "Comercial Española SAC", "S/ 118.00", "S/ 18.00", "SIN VALIDEZ TRIBUTARIA");
      Path destino = Path.of("target/pruebas-comprobantes"); Files.createDirectories(destino);
      Files.write(destino.resolve("comprobante-A4.pdf"), respuesta);
      ImageIO.write(new PDFRenderer(pdf).renderImageWithDPI(0, 150), "png", destino.resolve("comprobante-A4.png").toFile());
    }
  }

  @Test void ticketPdfA4MuestraSaldoPendienteEnElResumen() throws Exception {
    tx.executeWithoutResult(s -> {
      var pago = em.createQuery("from Pago p where p.comprobante.idComprobante=:id", Pago.class)
          .setParameter("id", comprobante).getSingleResult();
      pago.setMonto(new BigDecimal("20.00"));
    });
    var respuesta = mvc.perform(get(ruta("/ticket"))).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsByteArray();
    try (var pdf = Loader.loadPDF(respuesta)) {
      String texto = new PDFTextStripper().getText(pdf);
      assertThat(texto).contains("Saldo pendiente", "S/ 20.00", "S/ 98.00", "PENDIENTE DE PAGO");
      Path destino = Path.of("target/pruebas-comprobantes"); Files.createDirectories(destino);
      ImageIO.write(new PDFRenderer(pdf).renderImageWithDPI(0, 150), "png", destino.resolve("comprobante-A4-pendiente.png").toFile());
    }
  }

  @Test void correoAdjuntaPdfSinEmitirDeNuevoNiModificarPagos() throws Exception {
    mvc.perform(get(ruta("/entrega"))).andExpect(status().isOk())
        .andExpect(jsonPath("$.correo").value("cliente@example.test")).andExpect(jsonPath("$.correoHabilitado").value(true));
    mvc.perform(post(ruta("/correo")).contentType(MediaType.APPLICATION_JSON).content("{\"correo\":\"destino@example.test\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.mensaje").value("El servidor de correo aceptó el comprobante para su envío."));
    var enviado = ArgumentCaptor.forClass(MimeMessage.class); verify(smtp).send(enviado.capture());
    var mensaje = enviado.getValue();
    assertThat(mensaje.getAllRecipients()).hasSize(1);
    assertThat(mensaje.getAllRecipients()[0].toString()).isEqualTo("destino@example.test");
    var partes = (Multipart) mensaje.getContent();
    boolean adjunto = false;
    for (int i = 0; i < partes.getCount(); i++) if (partes.getBodyPart(i).getFileName() != null) {
      var parte = partes.getBodyPart(i); assertThat(parte.getFileName()).endsWith(".pdf");
      try (var pdf = Loader.loadPDF(parte.getInputStream().readAllBytes())) { assertThat(pdf.getNumberOfPages()).isPositive(); }
      adjunto = true;
    }
    assertThat(adjunto).isTrue();
    tx.executeWithoutResult(s -> {
      assertThat(em.createQuery("select count(p) from Pago p where p.comprobante.idComprobante=:id", Long.class)
          .setParameter("id", comprobante).getSingleResult()).isEqualTo(1);
      assertThat(em.find(Comprobante.class, comprobante).getEstadoComprobante()).isEqualTo(EstadoComprobante.EMITIDO);
    });
  }

  @Test void rechazaCorreoInvalidoYNoExponeErroresDelServidorSmtp() throws Exception {
    mvc.perform(post(ruta("/correo")).contentType(MediaType.APPLICATION_JSON).content("{\"correo\":\"no-es-correo\"}"))
        .andExpect(status().isBadRequest());
    verify(smtp, never()).send(any(MimeMessage.class));
    doThrow(new MailSendException("dato privado del proveedor")).when(smtp).send(any(MimeMessage.class));
    var respuesta = mvc.perform(post(ruta("/correo")).contentType(MediaType.APPLICATION_JSON).content("{\"correo\":\"cliente@example.test\"}"))
        .andExpect(status().isBadGateway()).andReturn().getResponse().getContentAsString();
    assertThat(respuesta).doesNotContain("dato privado del proveedor");
  }

  @Test @WithMockUser(authorities = {"VENTAS:VER", "VENTAS:EMITIR_COMPROBANTE"})
  void permisoDeVentasNoPermiteLeerOEnviarUnaFacturaDeReserva() throws Exception {
    mvc.perform(get(ruta("/ticket"))).andExpect(status().isForbidden());
    mvc.perform(get(ruta("/entrega"))).andExpect(status().isForbidden());
    mvc.perform(post(ruta("/correo")).contentType(MediaType.APPLICATION_JSON).content("{\"correo\":\"cliente@example.test\"}"))
        .andExpect(status().isForbidden());
    verify(smtp, never()).send(any(MimeMessage.class));
  }

  @Test void noFingeEnvioSiCorreoEstaDeshabilitado() throws Exception {
    ReflectionTestUtils.setField(correos, "habilitado", false);
    try {
      mvc.perform(post(ruta("/correo")).contentType(MediaType.APPLICATION_JSON).content("{\"correo\":\"cliente@example.test\"}"))
          .andExpect(status().isServiceUnavailable());
      verify(smtp, never()).send(any(MimeMessage.class));
    } finally { ReflectionTestUtils.setField(correos, "habilitado", true); }
  }

  @Test void guardaElContratoActualParaVerificacionLocal() throws Exception {
    var respuesta = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    var contrato = json.readTree(respuesta);
    assertThat(contrato.path("paths").has("/api/v1/comprobantes/{id}/correo")).isTrue();
    assertThat(contrato.path("paths").has("/api/v1/comprobantes/{id}/simulacion")).isTrue();
    Files.write(Path.of("target/openapi-comprobantes.json"), respuesta);
  }
}
