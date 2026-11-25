package pe.com.rebolsysBackend.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import pe.com.rebolsysBackend.config.RequestIdFilter;
import pe.com.rebolsysBackend.dto.accesos.SesionActualDTO;
import pe.com.rebolsysBackend.dto.ventas.VentaLineaRegistroDTO;
import pe.com.rebolsysBackend.dto.ventas.VentaRequestDTO;
import pe.com.rebolsysBackend.entity.*;
import pe.com.rebolsysBackend.enums.*;
import pe.com.rebolsysBackend.security.AccesosService;
import pe.com.rebolsysBackend.security.CredencialesService;
import pe.com.rebolsysBackend.service.impl.comun.AuditoriaOperacionService;
import pe.com.rebolsysBackend.service.service.comun.comprobantes.ProveedorFacturacionClient;
import pe.com.rebolsysBackend.service.service.mantenimiento.HabitacionService;
import pe.com.rebolsysBackend.service.service.reservas.ReservaService;
import pe.com.rebolsysBackend.service.service.ventas.VentaService;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:prodhardening;MODE=Oracle;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "springdoc.api-docs.enabled=false",
    "springdoc.swagger-ui.enabled=false",
    "app.seguridad.rate-limit-login.habilitado=true",
    "app.seguridad.rate-limit-login.max-intentos-ip=4",
    "app.seguridad.rate-limit-login.ventana=1m",
    "app.caja.directorio-evidencias=target/evidencias-hardening-test",
    "app.hospedaje.directorio-logotipos=target/logotipos-hardening-test"
})
@ActiveProfiles("hardening-test")
@AutoConfigureMockMvc
class SeguridadProduccionYBatchTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired EntityManager em;
  @Autowired TransactionTemplate tx;
  @Autowired CredencialesService credenciales;
  @Autowired AccesosService accesos;
  @Autowired AuditoriaOperacionService auditoria;
  @Autowired HabitacionService habitaciones;
  @Autowired VentaService ventas;
  @Autowired ReservaService reservas;
  @MockitoBean(name = "apisPeruRestClient") RestClient consultaDocumentos;
  @MockitoBean ProveedorFacturacionClient facturacion;

  @Test
  void bloqueaSwaggerYExponeHealthDeActuatorSinDetallesPublicos() throws Exception {
    mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
    mvc.perform(get("/swagger-ui.html")).andExpect(status().isUnauthorized());
    mvc.perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(header().exists(RequestIdFilter.CABECERA))
        .andExpect(jsonPath("$.status").value("UP"))
        .andExpect(jsonPath("$.components").doesNotExist());
    mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
  }

  @Test
  void rechazaEmpleadoCesadoSinAbrirTurnoYAplicaRateLimit() throws Exception {
    Long idUsuario = tx.execute(s -> {
      var tipo = tipoDocumentoDni();
      var perfil = new Perfil(null, tipo, "70000001", "cesado@example.test", null);
      em.persist(perfil);
      var persona = new PersonaNatural(null, perfil, "Empleado", "Cesado", LocalDate.of(1990, 1, 1));
      em.persist(persona);
      var empleado = new Empleado(null, persona, LocalDate.of(2024, 1, 1), LocalDate.of(2026, 1, 1));
      em.persist(empleado);
      var u = new Usuario(null, empleado, "cesado.prueba", EstadoUsuario.ACTIVO);
      em.persist(u);
      return u.getIdUsuario();
    });
    var rol = tx.execute(s -> {
      var r = new Rol(null, "RECEPCION_TEST");
      em.persist(r);
      return r.getIdRol();
    });
    tx.executeWithoutResult(s -> {
      accesos.permisos(rol, java.util.Set.of("CAJA:VER"));
      accesos.vincular(idUsuario, rol);
      var u = em.find(Usuario.class, idUsuario);
      credenciales.emitir(u);
    });

    assertThatThrownBy(() -> credenciales.iniciar("cesado.prueba", "cualquiera"))
        .isInstanceOf(BadCredentialsException.class);
    Long turnosAbiertos = tx.execute(s -> em.createQuery(
        "select count(t) from TurnoCaja t where t.usuario.idUsuario = :id", Long.class)
        .setParameter("id", idUsuario).getSingleResult());
    assertThat(turnosAbiertos).isZero();

    var cuerpoLogin = json.writeValueAsBytes(Map.of("usuario", "nadie", "contrasena", "clave-invalida"));
    for (int i = 0; i < 4; i++) {
      mvc.perform(post("/api/v1/sesion/iniciar").contentType(MediaType.APPLICATION_JSON).content(cuerpoLogin))
          .andExpect(status().isUnauthorized())
          .andExpect(header().exists(RequestIdFilter.CABECERA))
          .andExpect(jsonPath("$.requestId").isString());
    }
    mvc.perform(post("/api/v1/sesion/iniciar").contentType(MediaType.APPLICATION_JSON).content(cuerpoLogin))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.status").value(429))
        .andExpect(jsonPath("$.requestId").isString());
  }

  @Test
  void auditoriaPostCommitSoloPersisteSiLaTransaccionConfirma() {
    Long idUsuario = tx.execute(s -> {
      var tipo = tipoDocumentoDni();
      var perfil = new Perfil(null, tipo, "70000002", "aud@example.test", null);
      em.persist(perfil);
      var persona = new PersonaNatural(null, perfil, "Aud", "Test", LocalDate.of(1992, 2, 2));
      em.persist(persona);
      var empleado = new Empleado(null, persona, LocalDate.of(2025, 1, 1), null);
      em.persist(empleado);
      var u = new Usuario(null, empleado, "aud.user", EstadoUsuario.ACTIVO);
      em.persist(u);
      return u.getIdUsuario();
    });
    var sesion = new SesionActualDTO(idUsuario, "aud.user", "ACTIVA", null, null);

    tx.executeWithoutResult(status -> {
      auditoria.publicarPostCommit(sesion, "VENTA", 999L, "POST", "/api/v1/ventas", "127.0.0.1");
      status.setRollbackOnly();
    });
    Long trasRollback = tx.execute(s -> em.createQuery(
        "select count(a) from AuditoriaGeneral a where a.nombreTabla='VENTA' and cast(a.claveRegistro as string)='{\"id\":999}'",
        Long.class).getSingleResult());
    assertThat(trasRollback).isZero();

    tx.executeWithoutResult(status ->
        auditoria.publicarPostCommit(sesion, "VENTA", 999L, "POST", "/api/v1/ventas", "127.0.0.1"));
    Long trasCommit = tx.execute(s -> em.createQuery(
        "select count(a) from AuditoriaGeneral a where a.nombreTabla='VENTA' and cast(a.claveRegistro as string)='{\"id\":999}'",
        Long.class).getSingleResult());
    assertThat(trasCommit).isEqualTo(1L);
  }

  @Test
  void listadosConCargaPorLotesDevuelvenDatosCompletos() {
    var datos = tx.execute(s -> {
      var doc = tipoDocumentoDni();
      var perfilOp = new Perfil(null, doc, "70000003", "op@example.test", null);
      em.persist(perfilOp);
      em.persist(new PersonaNatural(null, perfilOp, "Operador", "Batch", LocalDate.of(1991, 3, 3)));
      var emp = new Empleado(null, em.find(PersonaNatural.class, perfilOp.getIdPerfil()), LocalDate.of(2025, 1, 1), null);
      em.persist(emp);
      var u = new Usuario(null, emp, "op.batch", EstadoUsuario.ACTIVO);
      em.persist(u);

      var perfilCli = new Perfil(null, doc, "70000004", "cli@example.test", null);
      perfilCli.setEsCliente(1);
      em.persist(perfilCli);
      em.persist(new PersonaNatural(null, perfilCli, "Cliente", "Uno", LocalDate.of(1993, 4, 4)));
      var hue = new Huesped();
      hue.setPerfil(perfilCli);
      em.persist(hue);

      var tipoHab = new TipoHabitacion();
      tipoHab.setNombreTipo("Doble Batch");
      tipoHab.setCapacidadMax(2);
      tipoHab.setPrecioBase(new BigDecimal("90.00"));
      em.persist(tipoHab);
      var hab = new Habitacion();
      hab.setNumeroCuarto("305");
      hab.setTipoHabitacion(tipoHab);
      hab.setEstadoHabitacion(EstadoHabitacion.DISPONIBLE);
      hab.setCapacidadPersonas(2);
      hab.setPlazasCama(2);
      hab.setMaxMenoresSinCama(0);
      em.persist(hab);

      var ingreso = LocalDateTime.now().plusDays(1).withHour(14).withMinute(0).withSecond(0).withNano(0);
      var salida = ingreso.plusDays(1).withHour(12);
      var res = new Reserva();
      res.setCliente(perfilCli);
      res.setUsuario(u);
      res.setClaveRegistro(UUID.randomUUID().toString());
      res.setFirmaSolicitud("firma-batch-test");
      em.persist(res);
      var det = new DetalleReserva();
      det.setReserva(res);
      det.setHabitacion(hab);
      det.setFechaIngreso(ingreso);
      det.setFechaSalida(salida);
      det.setCostoEstadia(new BigDecimal("90.00"));
      det.setEstadoDetalle(EstadoReserva.CONFIRMADA);
      em.persist(det);
      var oc = new ReservaOcupante();
      oc.setDetalleReserva(det);
      oc.setHuesped(hue);
      oc.setOcupaPlazaCama(1);
      em.persist(oc);

      var cat = new Categoria();
      cat.setNombreCategoria("Bebidas Batch");
      em.persist(cat);
      var prod = new Producto();
      prod.setNombreProducto("Agua Batch");
      prod.setCategoria(cat);
      prod.setPrecioActual(new BigDecimal("4.50"));
      prod.setStock(10);
      prod.setStockMinimo(2);
      prod.setEstadoProducto(EstadoProducto.ACTIVO);
      em.persist(prod);

      return Map.of("usuario", u.getIdUsuario(), "cliente", perfilCli.getIdPerfil(),
          "habitacion", hab.getIdHabitacion(), "producto", prod.getIdProducto());
    });

    ventas.registrar(new VentaRequestDTO(
        UUID.randomUUID().toString(),
        datos.get("usuario"),
        datos.get("cliente"),
        null,
        ModalidadCobro.SEPARADA,
        null,
        List.of(new VentaLineaRegistroDTO(datos.get("producto"), 2, new BigDecimal("4.50")))));

    var paginaReservas = reservas.listar(0, 10);
    assertThat(paginaReservas.contenido()).isNotEmpty();
    assertThat(paginaReservas.contenido().getFirst().detalles()).hasSize(1);
    assertThat(paginaReservas.contenido().getFirst().detalles().getFirst().ocupantes()).hasSize(1);

    var paginaVentas = ventas.listar(0, 10);
    assertThat(paginaVentas.contenido()).isNotEmpty();
    assertThat(paginaVentas.contenido().getFirst().detalles()).hasSize(1);

    var estados = habitaciones.estado(LocalDate.now(), LocalDate.now().plusDays(5));
    assertThat(estados).anySatisfy(h -> {
      if ("305".equals(h.numero())) {
        assertThat(h.reservas()).hasSize(1);
      }
    });
  }

  private TipoDocumento tipoDocumentoDni() {
    var existentes = em.createQuery(
        "select t from TipoDocumento t where t.nombreDocumento = 'DNI'", TipoDocumento.class)
        .getResultList();
    if (!existentes.isEmpty()) return existentes.getFirst();
    var nuevo = new TipoDocumento(null, "DNI", "1");
    em.persist(nuevo);
    return nuevo;
  }
}
