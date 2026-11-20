package pe.com.rebolsysBackend.limpieza;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import jakarta.persistence.EntityManager;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import pe.com.rebolsysBackend.dto.ItemAsignacionDTO;
import pe.com.rebolsysBackend.dto.SesionActualDTO;
import pe.com.rebolsysBackend.entity.*;
import pe.com.rebolsysBackend.enums.*;
import pe.com.rebolsysBackend.service.impl.ReglasPropuesta;
import pe.com.rebolsysBackend.security.SesionOperacionService;
import pe.com.rebolsysBackend.security.AccesosService;
import pe.com.rebolsysBackend.security.CredencialesService;
import pe.com.rebolsysBackend.security.JwtService;
import pe.com.rebolsysBackend.service.service.ItemService;
import pe.com.rebolsysBackend.service.service.ProveedorFacturacionClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:limpieza;MODE=Oracle;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false",
    "springdoc.api-docs.version=OPENAPI_3_0",
    "app.limpieza.directorio-evidencias=target/evidencias-limpieza-test"
})
@AutoConfigureMockMvc
@ActiveProfiles("limpieza-test")
class FlujoLimpiezaTest {
  private static final String API = "/api/v1/limpieza";
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired EntityManager em;
  @Autowired TransactionTemplate tx;
  @Autowired ItemService items;
  @Autowired WebApplicationContext contexto;
  @Autowired AccesosService accesos;
  @Autowired CredencialesService credenciales;
  @Autowired JwtService jwt;
  @MockitoBean(name = "apisPeruRestClient") RestClient consultaDocumentos;
  @MockitoBean ProveedorFacturacionClient facturacion;
  private Long habitacion;
  private Long item;
  private Long usuarioId;
  private String contrasenaTemporal;

  private RequestPostProcessor actor(SesionActualDTO actual) {
    return request -> {
      request.setAttribute(SesionOperacionService.ATRIBUTO, actual);
      return request;
    };
  }

  @BeforeEach
  void preparar() {
    tx.executeWithoutResult(s -> {
      var tipoDoc = new TipoDocumento(null, "Documento de prueba", null);
      em.persist(tipoDoc);
      var perfil = new Perfil(null, tipoDoc, "10000000", null, null);
      em.persist(perfil);
      var persona = new PersonaNatural(null, perfil, "Personal", "Prueba", null);
      em.persist(persona);
      var empleado = new Empleado(null, persona, LocalDate.of(2025, 1, 1), null);
      em.persist(empleado);
      var usuario = new Usuario(null, empleado, "prueba_limpieza", EstadoUsuario.ACTIVO);
      em.persist(usuario);
      usuarioId = usuario.getIdUsuario();
      var rol = new Rol(null, AccesosService.MANTENIMIENTO);
      em.persist(rol);
      em.flush();
      accesos.permisos(rol.getIdRol(), new java.util.HashSet<>(
          accesos.catalogo().stream().map(AccesosService.Funcion::codigo).toList()));
      accesos.vincular(usuarioId, rol.getIdRol());
      contrasenaTemporal = credenciales.emitir(usuario).contrasenaTemporal();
      var turno = new TurnoCaja();
      turno.setUsuario(usuario);
      turno.setFechaApertura(ReglasPropuesta.ahora().minusHours(1));
      turno.setEstadoSesion(1);
      em.persist(turno);
      var tipo = new TipoHabitacion();
      tipo.setNombreTipo("Simple");
      tipo.setCapacidadMax(2);
      em.persist(tipo);
      var cuarto = new Habitacion();
      cuarto.setTipoHabitacion(tipo);
      cuarto.setNumeroCuarto("301");
      cuarto.setCapacidadPersonas(2);
      cuarto.setPlazasCama(2);
      cuarto.setEstadoHabitacion(EstadoHabitacion.POR_LIMPIAR);
      em.persist(cuarto);
      registrarSalida(cuarto, perfil, usuario);
      var tipoItem = new TipoItem(null, "Muebles");
      em.persist(tipoItem);
      var mueble = new Item();
      mueble.setTipoItem(tipoItem);
      mueble.setNombreItem("Mesa");
      mueble.setEstadoItem(EstadoItem.ACTIVO);
      em.persist(mueble);
      em.persist(new TipoMovimientoAlmacen(null, "ASIG_ITEM", "Asignación", 0));
      em.persist(new TipoMovimientoAlmacen(null, "EST_ITEM", "Conservación", 0));
      em.persist(new TipoMovimientoAlmacen(null, "RET_ITEM", "Retiro", 0));
      habitacion = cuarto.getIdHabitacion();
      item = mueble.getIdItem();
      items.asignar(item, new ItemAsignacionDTO("asignacion-prueba", usuarioId,
          habitacion, EstadoConservacion.DANADO, "Inventario anterior"));
    });
    var temporal = credenciales.iniciar("prueba_limpieza", contrasenaTemporal);
    var principal = credenciales.cambiar(temporal, contrasenaTemporal, "Acceso definitivo para el flujo de limpieza");
    var token = jwt.emitir(principal, 30).token();
    mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity())
        .defaultRequest(get("/").header("Authorization", "Bearer " + token)).build();
  }

  @Test
  void protegeLaVistaDeHabitacionYResuelveIncidenciasEnTransaccionesCompletas() throws Exception {
    mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/limpieza/informes/{id}/aprobar'].post").exists());
    mvc.perform(get(API + "/habitaciones").param("estado", "POR_LIMPIAR").param("numeroHabitacion", "301"))
        .andExpect(status().isOk()).andExpect(jsonPath("$[0].idHabitacion").value(habitacion));
    mvc.perform(get(API + "/habitaciones").param("fechaSalida", "2025-10-27"))
        .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    mvc.perform(get(API + "/habitaciones").param("fechaSalida", "2025-10-28"))
        .andExpect(status().isOk()).andExpect(jsonPath("$[0].fechaSalida").value("2025-10-28T08:30:00"));
    String cuarto = API + "/habitaciones/" + habitacion;
    String estadoFisico = "/api/v1/habitaciones/" + habitacion + "/estado";
    long id = respuesta(mvc.perform(post(cuarto + "/iniciar"))).path("idInformeRevision").asLong();
    mvc.perform(post(cuarto + "/iniciar"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.idInformeRevision").value(id))
        .andExpect(jsonPath("$.items[0].estado").doesNotExist());
    assertEstadoPersistido(EstadoHabitacion.EN_LIMPIEZA);
    String enviar = API + "/borradores/" + id + "/enviar";
    String informe = API + "/informes/" + id;
    mvc.perform(get(cuarto)).andExpect(status().isOk())
        .andExpect(jsonPath("$.habitacion.estado").value("EN_LIMPIEZA"))
        .andExpect(jsonPath("$.habitacion.responsable.usuario").value("prueba_limpieza"))
        .andExpect(jsonPath("$.items[0].estado").value("SIN_REGISTRAR"))
        .andExpect(jsonPath("$.estadoInforme").value("SIN_INFORME"))
        .andExpect(jsonPath("$.estadoTickets").value("SIN_TICKET"));
    mvc.perform(patch(estadoFisico).contentType(MediaType.APPLICATION_JSON)
        .content("{\"estadoHabitacion\":\"DISPONIBLE\"}")).andExpect(status().isConflict());
    mvc.perform(post(enviar).contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}"))
        .andExpect(status().isConflict());
    var faltante = Map.of("idItem", item, "estadoEncontrado", "DANADO");
    mvc.perform(post(enviar).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(Map.of("items", List.of(faltante)))))
        .andExpect(status().isConflict());

    var bytes = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
    var foto = respuesta(mvc.perform(multipart(API + "/evidencias")
        .file(new MockMultipartFile("archivo", "mesa.png", "image/png", bytes.toByteArray())))
        .andExpect(status().isCreated()));
    mvc.perform(get(foto.path("evidenciaRuta").asText())).andExpect(status().isOk())
        .andExpect(content().bytes(bytes.toByteArray()));
    var reporte = Map.of("items", List.of(Map.of("idItem", item, "estadoEncontrado", "DANADO",
        "observaciones", "Pata suelta", "evidencia", foto)));
    var enviado = respuesta(mvc.perform(post(enviar).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(reporte))).andExpect(status().isOk())
        .andExpect(jsonPath("$.informe.estado").value("POR_APROBAR"))
        .andExpect(jsonPath("$.informe.puedeAprobar").value(false)));
    assertEstadoPersistido(EstadoHabitacion.EN_LIMPIEZA);
    tx.executeWithoutResult(t -> em.find(Habitacion.class, habitacion).setEstadoHabitacion(EstadoHabitacion.DISPONIBLE));
    mvc.perform(get(cuarto)).andExpect(status().isOk())
        .andExpect(jsonPath("$.habitacion.estado").value("DISPONIBLE"))
        .andExpect(jsonPath("$.habitacion.puedeIniciarLimpieza").value(false))
        .andExpect(jsonPath("$.items[0].estado").value("SIN_REGISTRAR"))
        .andExpect(jsonPath("$.ultimoInforme").isEmpty())
        .andExpect(jsonPath("$.tickets").isEmpty());
    tx.executeWithoutResult(t -> em.find(Habitacion.class, habitacion).setEstadoHabitacion(EstadoHabitacion.EN_LIMPIEZA));
    mvc.perform(get(cuarto)).andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].estado").value("SIN_REGISTRAR"))
        .andExpect(jsonPath("$.estadoInforme").value("SIN_INFORME"))
        .andExpect(jsonPath("$.ultimoInforme").isEmpty())
        .andExpect(jsonPath("$.estadoTickets").value("SIN_TICKET"))
        .andExpect(jsonPath("$.tickets").isEmpty());
    mvc.perform(post(informe + "/aprobar")).andExpect(status().isConflict());
    mvc.perform(get(API + "/informes").param("nombre", "inf-" + id).param("estado", "POR_APROBAR")
        .param("fechaEmision", enviado.path("informe").path("fechaEnvio").asText().substring(0, 10)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(1));
    long detalle = enviado.path("items").get(0).path("idDetalleInforme").asLong();
    String nuevoTicket = informe + "/items/" + detalle + "/ticket";
    var permisosAtencion = List.of("LIMPIEZA:APROBAR_INFORME", "LIMPIEZA:CREAR_TICKET", "LIMPIEZA:EDITAR_TICKET",
        "LIMPIEZA:ELIMINAR_TICKET", "LIMPIEZA:CARGAR_EVIDENCIA", "LIMPIEZA:ELIMINAR_INFORME",
        "LIMPIEZA:VER_INFORMES", "LIMPIEZA:VER_TICKETS", "LIMPIEZA:VER_HABITACIONES");
    var personalLimpieza = new SesionActualDTO(usuarioId, "prueba_limpieza", "ACTIVA", null, null,
        false, false, List.of("LIMPIEZA"), permisosAtencion);
    var personalMantenimiento = new SesionActualDTO(usuarioId, "prueba_mantenimiento", "ACTIVA", null, null,
        false, false, List.of("MANTENIMIENTO"), permisosAtencion);
    mvc.perform(get(informe).requestAttr(SesionOperacionService.ATRIBUTO, personalLimpieza))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].puedeCrearTicket").value(false))
        .andExpect(jsonPath("$.informe.puedeEditar").value(false));
    mvc.perform(get(informe).requestAttr(SesionOperacionService.ATRIBUTO, personalMantenimiento))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].puedeCrearTicket").value(true))
        .andExpect(jsonPath("$.informe.puedeEditar").value(false));
    mvc.perform(get(cuarto).requestAttr(SesionOperacionService.ATRIBUTO, personalLimpieza))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.habitacion.puedeIniciarLimpieza").value(false))
        .andExpect(jsonPath("$.habitacion.puedeRetomarBorrador").value(false));
    var fotoAtencion = respuesta(mvc.perform(multipart(API + "/evidencias")
        .file(new MockMultipartFile("archivo", "reparacion.png", "image/png", bytes.toByteArray()))
        .with(actor(personalMantenimiento))).andExpect(status().isCreated()));
    var reparacion = Map.of("accion", Map.of("tipoAccion", "REPARACION", "costoReparacion", 15), "evidencia", fotoAtencion);
    mvc.perform(post(nuevoTicket).with(actor(personalLimpieza))
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(reparacion)))
        .andExpect(status().isForbidden());
    long primerTicket = respuesta(mvc.perform(post(nuevoTicket)
        .with(actor(personalMantenimiento)).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(reparacion))).andExpect(status().isCreated()))
        .path("ticket").path("idTicket").asLong();
    mvc.perform(get(informe).requestAttr(SesionOperacionService.ATRIBUTO, personalLimpieza))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.informe.puedeAprobar").value(false))
        .andExpect(jsonPath("$.informe.puedeEliminar").value(false))
        .andExpect(jsonPath("$.tickets[0].puedeEditar").value(false))
        .andExpect(jsonPath("$.tickets[0].puedeEliminar").value(false));
    mvc.perform(get(informe).requestAttr(SesionOperacionService.ATRIBUTO, personalMantenimiento))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.informe.puedeAprobar").value(true))
        .andExpect(jsonPath("$.informe.puedeEliminar").value(true))
        .andExpect(jsonPath("$.tickets[0].puedeEditar").value(true))
        .andExpect(jsonPath("$.tickets[0].puedeEliminar").value(true));
    assertThat(items.obtener(item).estadoActualItem()).isEqualTo(EstadoConservacion.BUENO);
    mvc.perform(get(cuarto)).andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].estado").value("SIN_REGISTRAR"))
        .andExpect(jsonPath("$.estadoInforme").value("SIN_INFORME"))
        .andExpect(jsonPath("$.ultimoInforme").isEmpty())
        .andExpect(jsonPath("$.estadoTickets").value("SIN_TICKET"))
        .andExpect(jsonPath("$.tickets").isEmpty());
    mvc.perform(get(informe)).andExpect(status().isOk())
        .andExpect(jsonPath("$.informe.tieneIncidencias").value(true))
        .andExpect(jsonPath("$.informe.puedeAprobar").value(true))
        .andExpect(jsonPath("$.items[0].estadoEncontrado").value("DANADO"))
        .andExpect(jsonPath("$.items[0].estadoActualItem").value("BUENO"));
    mvc.perform(delete(informe).with(actor(personalLimpieza)))
        .andExpect(status().isForbidden());
    mvc.perform(delete(API + "/tickets/" + primerTicket).with(actor(personalLimpieza)))
        .andExpect(status().isForbidden());
    mvc.perform(delete(API + "/tickets/" + primerTicket)
        .with(actor(personalMantenimiento))).andExpect(status().isNoContent());
    assertThat(items.obtener(item).estadoActualItem()).isEqualTo(EstadoConservacion.DANADO);
    mvc.perform(post(informe + "/aprobar")).andExpect(status().isConflict());

    long idTicket = respuesta(mvc.perform(post(nuevoTicket)
        .with(actor(personalMantenimiento)).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(reparacion))).andExpect(status().isCreated()))
        .path("ticket").path("idTicket").asLong();
    String ticket = API + "/tickets/" + idTicket;
    var reposicion = "{\"tipoAccion\":\"REPOSICION\",\"observacionTecnica\":\"Mesa repuesta\"}";
    mvc.perform(put(ticket).with(actor(personalLimpieza))
        .contentType(MediaType.APPLICATION_JSON).content(reposicion)).andExpect(status().isForbidden());
    mvc.perform(put(ticket).with(actor(personalMantenimiento))
        .contentType(MediaType.APPLICATION_JSON).content(reposicion))
        .andExpect(status().isOk()).andExpect(jsonPath("$.costoReparacion").doesNotExist());
    mvc.perform(get(API + "/tickets").param("tipoAccion", "REPOSICION").param("nombre", "TKT-" + idTicket)
        .param("numeroHabitacion", "301")).andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElementos").value(1));
    mvc.perform(post(informe + "/aprobar").with(actor(personalLimpieza)))
        .andExpect(status().isForbidden());
    mvc.perform(get(informe)).andExpect(status().isOk())
        .andExpect(jsonPath("$.informe.estado").value("POR_APROBAR"));

    mvc.perform(post(informe + "/aprobar").with(actor(personalMantenimiento)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.informe.estado").value("APROBADO"));
    mvc.perform(get(cuarto)).andExpect(status().isOk())
        .andExpect(jsonPath("$.habitacion.estado").value("DISPONIBLE"))
        .andExpect(jsonPath("$.items[0].estado").value("BUENO"))
        .andExpect(jsonPath("$.ultimoInforme.informe.idInformeRevision").value(id))
        .andExpect(jsonPath("$.tickets[0].idTicket").value(idTicket));
    assertEstadoPersistido(EstadoHabitacion.DISPONIBLE);
    mvc.perform(delete(informe)).andExpect(status().isConflict());
    mvc.perform(delete(ticket)).andExpect(status().isConflict());
    mvc.perform(put(ticket).contentType(MediaType.APPLICATION_JSON).content(reposicion))
        .andExpect(status().isConflict());
    mvc.perform(get(API + "/inicio")).andExpect(status().isOk())
        .andExpect(jsonPath("$.informesAprobados").value(1))
        .andExpect(jsonPath("$.ticketsGenerados").value(1));
    var auditoria = tx.execute(s -> em.createQuery("""
        select a.accion from AuditoriaGeneral a where a.nombreTabla = 'TICKET_MANTENIMIENTO'
        and cast(a.claveRegistro as string) = :id order by a.idAuditoria
        """, AccionAuditoria.class).setParameter("id", "{\"id\":" + idTicket + "}").getResultList());
    assertThat(auditoria).containsExactly(AccionAuditoria.INSERT, AccionAuditoria.UPDATE);

    for (String estado : List.of("POR_LIMPIAR", "MANTENIMIENTO")) {
      mvc.perform(patch(estadoFisico).contentType(MediaType.APPLICATION_JSON)
          .content(json.writeValueAsBytes(Map.of("estadoHabitacion", estado)))).andExpect(status().isOk());
      mvc.perform(get(cuarto)).andExpect(status().isOk())
          .andExpect(jsonPath("$.items[0].estado").value("BUENO"))
          .andExpect(jsonPath("$.ultimoInforme.informe.idInformeRevision").value(id));
    }
    mvc.perform(patch(estadoFisico).contentType(MediaType.APPLICATION_JSON)
        .content("{\"estadoHabitacion\":\"EN_LIMPIEZA\"}")).andExpect(status().isConflict());
    verificarEliminacionYAprobacionSinIncidencias(cuarto, estadoFisico, foto);
    verificarHistorialTrasRetiro(informe);
    verificarInicioConcurrente();
  }

  private void verificarHistorialTrasRetiro(String informe) throws Exception {
    Long destino = tx.execute(s -> {
      var cuarto = new Habitacion();
      cuarto.setTipoHabitacion(em.find(Habitacion.class, habitacion).getTipoHabitacion());
      cuarto.setNumeroCuarto("302");
      cuarto.setCapacidadPersonas(1);
      cuarto.setPlazasCama(1);
      cuarto.setEstadoHabitacion(EstadoHabitacion.DISPONIBLE);
      em.persist(cuarto);
      return cuarto.getIdHabitacion();
    });
    items.asignar(item, new ItemAsignacionDTO("traslado-prueba", usuarioId, destino,
        EstadoConservacion.BUENO, "Traslado a otra habitación"));
    items.asignar(item, new ItemAsignacionDTO("retiro-prueba", usuarioId, null,
        EstadoConservacion.DANADO, "Retiro del mueble"));

    var historial = items.historial(item);
    assertThat(historial.get(0).accion()).isEqualTo("RETIRO");
    assertThat(historial.get(0).idHabitacionOrigen()).isEqualTo(destino);
    assertThat(historial.get(1).accion()).isEqualTo("TRASLADO");
    assertThat(historial.get(1).idHabitacionOrigen()).isEqualTo(habitacion);
    assertThat(historial.get(1).idHabitacionDestino()).isEqualTo(destino);
    mvc.perform(get(informe)).andExpect(status().isOk())
        .andExpect(jsonPath("$.informe.idHabitacion").value(habitacion))
        .andExpect(jsonPath("$.items[0].idItem").value(item))
        .andExpect(jsonPath("$.items[0].estadoEncontrado").value("DANADO"));
  }

  private void registrarSalida(Habitacion cuarto, Perfil perfil, Usuario usuario) {
    perfil.setEsCliente(1);
    var reserva = new Reserva();
    reserva.setCliente(perfil);
    reserva.setUsuario(usuario);
    reserva.setClaveRegistro("reserva-limpieza");
    reserva.setFirmaSolicitud("firma-limpieza");
    var estancia = new DetalleReserva();
    estancia.setHabitacion(cuarto);
    estancia.setFechaIngreso(LocalDateTime.of(2025, 10, 27, 12, 0));
    estancia.setFechaSalida(LocalDateTime.of(2025, 10, 28, 10, 0));
    estancia.setFechaIngresoReal(LocalDateTime.of(2025, 10, 27, 12, 0));
    estancia.setFechaSalidaReal(LocalDateTime.of(2025, 10, 28, 8, 30));
    estancia.setEstadoDetalle(EstadoReserva.FINALIZADA);
    estancia.setCostoEstadia(BigDecimal.ZERO);
    reserva.agregarDetalle(estancia);
    em.persist(reserva);
  }

  private void verificarEliminacionYAprobacionSinIncidencias(String cuarto, String estadoFisico, JsonNode foto)
      throws Exception {
    mvc.perform(patch(estadoFisico).contentType(MediaType.APPLICATION_JSON)
        .content("{\"estadoHabitacion\":\"POR_LIMPIAR\"}")).andExpect(status().isOk());
    var borrador = respuesta(mvc.perform(post(cuarto + "/iniciar")).andExpect(status().isOk()));
    long id = borrador.path("idInformeRevision").asLong();
    long asignacionActual = borrador.path("items").get(0).path("idItem").asLong();
    var itemsConIncidencia = List.of(Map.of("idItem", asignacionActual, "estadoEncontrado", "FALTANTE",
        "evidencia", foto));
    var enviado = respuesta(mvc.perform(post(API + "/borradores/" + id + "/enviar").contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(Map.of("items", itemsConIncidencia)))).andExpect(status().isOk()));
    long detalle = enviado.path("items").get(0).path("idDetalleInforme").asLong();
    String informe = API + "/informes/" + id;
    var datosTicket = Map.of("accion", Map.of("tipoAccion", "REPOSICION"), "evidencia", foto);
    long ticket = respuesta(mvc.perform(post(informe + "/items/" + detalle + "/ticket")
        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(datosTicket)))
        .andExpect(status().isCreated())).path("ticket").path("idTicket").asLong();
    mvc.perform(delete(informe)).andExpect(status().isNoContent());
    mvc.perform(get(API + "/tickets/" + ticket)).andExpect(status().isNotFound());
    mvc.perform(get(cuarto)).andExpect(status().isOk())
        .andExpect(jsonPath("$.habitacion.estado").value("POR_LIMPIAR"));

    assertEstadoPersistido(EstadoHabitacion.POR_LIMPIAR);
    var cancelable = respuesta(mvc.perform(post(cuarto + "/iniciar")).andExpect(status().isOk()));
    mvc.perform(delete(API + "/borradores/" + cancelable.path("idInformeRevision").asLong()))
        .andExpect(status().isNoContent());
    assertEstadoPersistido(EstadoHabitacion.POR_LIMPIAR);
    var siguiente = respuesta(mvc.perform(post(cuarto + "/iniciar")).andExpect(status().isOk()));
    long nuevoId = siguiente.path("idInformeRevision").asLong();
    var sinIncidencia = List.of(Map.of("idItem", asignacionActual, "estadoEncontrado", "BUENO"));
    mvc.perform(post(API + "/borradores/" + nuevoId + "/enviar").contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(Map.of("items", sinIncidencia))))
        .andExpect(status().isOk()).andExpect(jsonPath("$.informe.puedeAprobar").value(true));
    mvc.perform(post(API + "/informes/" + nuevoId + "/aprobar")).andExpect(status().isOk());
  }

  private void verificarInicioConcurrente() throws Exception {
    Long segundoUsuario = tx.execute(s -> {
      var tipoDoc = em.createQuery("select t from TipoDocumento t", TipoDocumento.class).getResultList().get(0);
      var perfil = new Perfil(null, tipoDoc, "10000001", null, null); em.persist(perfil);
      var persona = new PersonaNatural(null, perfil, "Segundo", "Operador", null); em.persist(persona);
      var empleado = new Empleado(null, persona, LocalDate.of(2025, 1, 1), null); em.persist(empleado);
      var usuario = new Usuario(null, empleado, "segundo_limpieza", EstadoUsuario.ACTIVO); em.persist(usuario);
      return usuario.getIdUsuario();
    });
    Long cuarto = tx.execute(s -> {
      var h = new Habitacion();
      h.setTipoHabitacion(em.find(Habitacion.class, habitacion).getTipoHabitacion());
      h.setNumeroCuarto("303"); h.setCapacidadPersonas(1); h.setPlazasCama(1);
      h.setEstadoHabitacion(EstadoHabitacion.POR_LIMPIAR); em.persist(h);
      return h.getIdHabitacion();
    });
    var barrera = new java.util.concurrent.CyclicBarrier(2);
    try (var ejecutor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var tareas = List.of(usuarioId, segundoUsuario).stream().map(id -> ejecutor.submit(() -> {
        barrera.await(10, java.util.concurrent.TimeUnit.SECONDS);
        var actual = new SesionActualDTO(id, "operador", "ACTIVA", null, null, false, false,
            List.of("LIMPIEZA"), List.of("LIMPIEZA:INICIAR"));
        return mvc.perform(post(API + "/habitaciones/" + cuarto + "/iniciar")
            .requestAttr(SesionOperacionService.ATRIBUTO, actual)).andReturn().getResponse().getStatus();
      })).toList();
      assertThat(List.of(tareas.get(0).get(20, java.util.concurrent.TimeUnit.SECONDS),
          tareas.get(1).get(20, java.util.concurrent.TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 409);
    }
    Long activos = tx.execute(s -> em.createQuery(
        "select count(i) from InformeRevision i where i.habitacion.idHabitacion = :id", Long.class)
        .setParameter("id", cuarto).getSingleResult());
    assertThat(activos).isEqualTo(1);
  }

  private void assertEstadoPersistido(EstadoHabitacion estado) {
    String persistido = tx.execute(s -> (String) em.createNativeQuery(
        "select estadoHabitacion from habitacion where idHabitacion = :id", String.class)
        .setParameter("id", habitacion).getSingleResult());
    assertThat(persistido).isEqualTo(estado.name());
  }

  private JsonNode respuesta(ResultActions respuesta) throws Exception {
    return json.readTree(respuesta.andReturn().getResponse().getContentAsByteArray());
  }
}
