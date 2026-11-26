package pe.com.rebolsysBackend.service.impl.reservas.yape;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import pe.com.rebolsysBackend.dto.reservas.ReservaDetalleRegistroDTO;
import pe.com.rebolsysBackend.dto.reservas.ReservaOcupanteRegistroDTO;
import pe.com.rebolsysBackend.dto.reservas.ReservaRegistroRequestDTO;
import pe.com.rebolsysBackend.entity.*;
import pe.com.rebolsysBackend.entity.id.RolPermisoId;
import pe.com.rebolsysBackend.entity.id.SerieComprobanteId;
import pe.com.rebolsysBackend.entity.id.UsuarioRolId;
import pe.com.rebolsysBackend.enums.EstadoHabitacion;
import pe.com.rebolsysBackend.enums.EstadoUsuario;
import pe.com.rebolsysBackend.repository.caja.cobros.PagoRepository;
import pe.com.rebolsysBackend.security.CredencialesService;
import pe.com.rebolsysBackend.security.JwtService;
import pe.com.rebolsysBackend.service.service.comun.comprobantes.ProveedorFacturacionClient;
import pe.com.rebolsysBackend.service.service.reservas.CuentaReservaService;
import pe.com.rebolsysBackend.service.service.reservas.ReservaService;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:pagoyape;MODE=Oracle;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.jpa.show-sql=false",
      "app.yape.habilitado=true",
      "app.yape.modo=SIMULADO",
      "app.yape.ambiente=PRUEBA"
    })
@ActiveProfiles("yape-test")
@AutoConfigureMockMvc
class PagoYapeIntegracionTest {
  @Autowired EntityManager em;
  @Autowired TransactionTemplate tx;
  @Autowired JdbcTemplate jdbc;
  @Autowired ReservaService reservas;
  @Autowired CuentaReservaService cuentas;
  @Autowired PagoRepository pagos;
  @Autowired CredencialesService credenciales;
  @Autowired PasswordEncoder encoder;
  @Autowired JwtService jwt;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @MockitoBean(name = "apisPeruRestClient") RestClient consultaDocumentos;
  @MockitoBean ProveedorFacturacionClient facturacion;

  private String tokenRecepcionista;
  private String tokenGerente;
  private Long idReserva;

  @BeforeEach
  void preparar() {
    jdbc.execute(
        """
        CREATE TABLE IF NOT EXISTS pago_yape (
          claveOperacion VARCHAR2(36) PRIMARY KEY,
          idReserva NUMBER NOT NULL,
          idUsuario NUMBER NOT NULL,
          idTurno NUMBER NOT NULL,
          importe NUMBER(12,2) NOT NULL,
          telefonoEnmascarado VARCHAR2(15) NOT NULL,
          modo VARCHAR2(15) NOT NULL,
          ambiente VARCHAR2(12) NOT NULL,
          estado VARCHAR2(12) NOT NULL,
          ordenProveedor VARCHAR2(64) UNIQUE,
          idPago NUMBER UNIQUE,
          mensajeProveedor VARCHAR2(250),
          creado TIMESTAMP NOT NULL
        )
        """);

    String sufijo = UUID.randomUUID().toString().substring(0, 8);
    String hashFijo = encoder.encode("ClavePrueba123!");
    var datos =
        tx.execute(
            s -> {
              var td = new TipoDocumento();
              td.setNombreDocumento("DNI-" + sufijo);
              td.setCodigoSunat("1");
              em.persist(td);

              var tc =
                  em.createQuery(
                          "select t from TipoCredencial t where t.nombreCredencial = 'PASSWORD'",
                          TipoCredencial.class)
                      .getResultStream()
                      .findFirst()
                      .orElseGet(
                          () -> {
                            var nuevo = new TipoCredencial(null, "PASSWORD");
                            em.persist(nuevo);
                            return nuevo;
                          });

              var pCli = crearPersona(td, sufijo + "C", 1, "Elena", "Mendoza");
              var pRec = crearPersona(td, sufijo + "R", 0, "Diego", "Huamán");
              var pGer = crearPersona(td, sufijo + "G", 0, "Mariana", "Quispe");

              var uRec = crearUsuario(pRec, "rec-" + sufijo, tc, hashFijo);
              var uGer = crearUsuario(pGer, "ger-" + sufijo, tc, hashFijo);

              var rolRec = obtenerORCrearRol("RECEPCIONISTA");
              var rolGer = obtenerORCrearRol("GERENTE");
              var permCobrar = obtenerORCrearPermiso("RESERVAS", "COBRAR");
              em.flush();

              if (em.find(RolPermiso.class, new RolPermisoId(rolRec.getIdRol(), permCobrar.getIdPermiso()))
                  == null) {
                em.persist(
                    new RolPermiso(
                        new RolPermisoId(rolRec.getIdRol(), permCobrar.getIdPermiso()),
                        rolRec,
                        permCobrar));
              }
              em.persist(
                  new UsuarioRol(
                      new UsuarioRolId(uRec.getIdUsuario(), rolRec.getIdRol()), uRec, rolRec));
              em.persist(
                  new UsuarioRol(
                      new UsuarioRolId(uGer.getIdUsuario(), rolGer.getIdRol()), uGer, rolGer));

              var tRec = new TurnoCaja();
              tRec.setUsuario(uRec);
              tRec.setEstadoSesion(1);
              em.persist(tRec);

              var th = new TipoHabitacion();
              th.setNombreTipo("Doble-" + sufijo);
              th.setCapacidadMax(2);
              th.setPrecioBase(new BigDecimal("80.00"));
              em.persist(th);

              var h = new Habitacion();
              h.setNumeroCuarto(sufijo);
              h.setTipoHabitacion(th);
              h.setEstadoHabitacion(EstadoHabitacion.DISPONIBLE);
              h.setCapacidadPersonas(2);
              h.setPlazasCama(2);
              em.persist(h);

              if (em.createQuery(
                          "select count(f) from FormaPago f where upper(f.nombrePago) = 'YAPE'",
                          Long.class)
                      .getSingleResult()
                  == 0) {
                em.persist(new FormaPago(null, "YAPE"));
              }
              if (em.createQuery(
                          "select count(t) from TipoMovimientoCaja t where t.nombreMovimiento = 'COBRO_RESERVA'",
                          Long.class)
                      .getSingleResult()
                  == 0) {
                em.persist(new TipoMovimientoCaja(null, "COBRO_RESERVA", 1));
              }
              if (em.createQuery(
                          "select count(t) from TipoComprobante t where t.codigo = 'CUENTA'",
                          Long.class)
                      .getSingleResult()
                  == 0) {
                var tip = new TipoComprobante(null, "CUENTA", "Cuenta", null);
                em.persist(tip);
                em.flush();
                em.persist(
                    new SerieComprobante(
                        new SerieComprobanteId(tip.getIdTipoComprobante(), "CTA"), 1L, tip));
              }

              return new DatosPrueba(
                  uRec.getIdUsuario(), uGer.getIdUsuario(), pCli.getIdPerfil(), h.getIdHabitacion());
            });

    tokenRecepcionista = jwt.emitir(credenciales.principalVigente(datos.idRec()), 60).token();
    tokenGerente = jwt.emitir(credenciales.principalVigente(datos.idGer()), 60).token();

    var res =
        reservas.registrar(
            new ReservaRegistroRequestDTO(
                "res-" + sufijo,
                datos.idCliente(),
                datos.idRec(),
                List.of(
                    new ReservaDetalleRegistroDTO(
                        datos.idHabitacion(),
                        LocalDateTime.now().plusHours(1),
                        LocalDateTime.now().plusDays(1),
                        new BigDecimal("80.00"),
                        List.of(
                            new ReservaOcupanteRegistroDTO(
                                datos.idCliente(), null, null, true)))),
                null));
    idReserva = res.idReserva();
  }

  private Perfil crearPersona(TipoDocumento td, String doc, int esCliente, String nom, String ape) {
    var p = new Perfil();
    p.setTipoDocumento(td);
    p.setNumeroDocumento(doc);
    p.setEsCliente(esCliente);
    em.persist(p);
    var pn = new PersonaNatural();
    pn.setPerfil(p);
    pn.setNombres(nom);
    pn.setApellidos(ape);
    pn.setFechaNacimiento(LocalDate.of(1992, 5, 10));
    em.persist(pn);
    return p;
  }

  private Usuario crearUsuario(
      Perfil p, String username, TipoCredencial tc, String hashFijo) {
    var e = new Empleado();
    e.setPersonaNatural(em.find(PersonaNatural.class, p.getIdPerfil()));
    e.setFechaContratacion(LocalDate.now());
    em.persist(e);
    var u = new Usuario();
    u.setEmpleado(e);
    u.setUsername(username);
    u.setEstadoUsuario(EstadoUsuario.ACTIVO);
    em.persist(u);
    var uc = new UsuarioCredencial();
    uc.setUsuario(u);
    uc.setTipoCredencial(tc);
    uc.setValorHash(hashFijo);
    uc.setCredencialTemporal(0);
    uc.setIntentosFallidos(0);
    em.persist(uc);
    return u;
  }

  private Rol obtenerORCrearRol(String nombre) {
    return em.createQuery("select r from Rol r where r.nombreRol = :n", Rol.class)
        .setParameter("n", nombre)
        .getResultStream()
        .findFirst()
        .orElseGet(
            () -> {
              var r = new Rol(null, nombre);
              em.persist(r);
              return r;
            });
  }

  private Permiso obtenerORCrearPermiso(String moduloNombre, String accion) {
    var mod =
        em.createQuery("select m from Modulo m where m.nombreModulo = :n", Modulo.class)
            .setParameter("n", moduloNombre)
            .getResultStream()
            .findFirst()
            .orElseGet(
                () -> {
                  var m = new Modulo(null, moduloNombre, "/reservas");
                  em.persist(m);
                  return m;
                });
    return em.createQuery(
            "select p from Permiso p where p.modulo.idModulo = :m and p.accion = :a",
            Permiso.class)
        .setParameter("m", mod.getIdModulo())
        .setParameter("a", accion)
        .getResultStream()
        .findFirst()
        .orElseGet(
            () -> {
              var p = new Permiso(null, mod, accion);
              em.persist(p);
              return p;
            });
  }

  @Test
  void cobraAbonoParcialLuegoPendienteYConciliaSinDuplicados() throws Exception {
    mvc.perform(
            get("/api/v1/pagos/yape/config")
                .header("Authorization", "Bearer " + tokenRecepcionista))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.habilitado").value(true))
        .andExpect(jsonPath("$.modo").value("SIMULADO"));

    // Gerente sin rol RECEPCIONISTA no puede cobrar reservas por Yape.
    mvc.perform(
            post("/api/v1/reservas/{id}/yape", idReserva)
                .header("Authorization", "Bearer " + tokenGerente)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "claveOperacion",
                            UUID.randomUUID().toString(),
                            "importe",
                            30.00,
                            "telefono",
                            "987654321",
                            "otp",
                            "123456"))))
        .andExpect(status().isForbidden());

    // 1. Abono parcial S/ 30.00 aprobado inmediatamente.
    String claveParcial = UUID.randomUUID().toString();
    mvc.perform(
            post("/api/v1/reservas/{id}/yape", idReserva)
                .header("Authorization", "Bearer " + tokenRecepcionista)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "claveOperacion",
                            claveParcial,
                            "importe",
                            30.00,
                            "telefono",
                            "987654321",
                            "otp",
                            "123456"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("APLICADO"))
        .andExpect(jsonPath("$.importe").value(30.00))
        .andExpect(jsonPath("$.telefonoEnmascarado").value("*** *** 321"))
        .andExpect(jsonPath("$.idPago").isNumber());

    assertThat(cuentas.cuenta(idReserva).saldoPendiente()).isEqualByComparingTo("50.00");

    // 2. Saldo restante S/ 50.00 con OTP 999999 queda PENDIENTE.
    String claveRestante = UUID.randomUUID().toString();
    mvc.perform(
            post("/api/v1/reservas/{id}/yape", idReserva)
                .header("Authorization", "Bearer " + tokenRecepcionista)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "claveOperacion",
                            claveRestante,
                            "importe",
                            50.00,
                            "telefono",
                            "912345678",
                            "otp",
                            "999999"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("PENDIENTE"))
        .andExpect(jsonPath("$.idPago").isEmpty());

    // Impide abrir otro intento mientras hay uno pendiente.
    mvc.perform(
            post("/api/v1/reservas/{id}/yape", idReserva)
                .header("Authorization", "Bearer " + tokenRecepcionista)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "claveOperacion",
                            UUID.randomUUID().toString(),
                            "importe",
                            50.00,
                            "telefono",
                            "912345678",
                            "otp",
                            "123456"))))
        .andExpect(status().isConflict());

    // 3. Concilia y aplica el pago restante.
    mvc.perform(
            post("/api/v1/reservas/{id}/yape/{clave}/conciliar", idReserva, claveRestante)
                .header("Authorization", "Bearer " + tokenRecepcionista))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("APLICADO"))
        .andExpect(jsonPath("$.idPago").isNumber());

    // Reintentar la conciliación devuelve exactamente el mismo pago sin duplicar.
    mvc.perform(
            post("/api/v1/reservas/{id}/yape/{clave}/conciliar", idReserva, claveRestante)
                .header("Authorization", "Bearer " + tokenRecepcionista))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("APLICADO"));

    var cuentaFinal = cuentas.cuenta(idReserva);
    assertThat(cuentaFinal.saldoPendiente()).isEqualByComparingTo("0.00");
    assertThat(cuentaFinal.totalPagado()).isEqualByComparingTo("80.00");
    assertThat(cuentaFinal.cuentas().getFirst().pagos()).hasSize(2);
    assertThat(pagos.findByClaveIdempotencia("YAPE-" + claveRestante)).isPresent();
  }

  @Test
  void eliminaDetalleDePagoYSoloPermiteSegunReglasDeNegocio() throws Exception {
    String claveParcial = UUID.randomUUID().toString();
    mvc.perform(
            post("/api/v1/reservas/{id}/yape", idReserva)
                .header("Authorization", "Bearer " + tokenRecepcionista)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "claveOperacion",
                            claveParcial,
                            "importe",
                            30.00,
                            "telefono",
                            "987654321",
                            "otp",
                            "123456"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.idPago").isNumber());

    Long idPagoYape = pagos.findByClaveIdempotencia("YAPE-" + claveParcial).orElseThrow().getIdPago();

    // Gerente sin rol RECEPCIONISTA no puede eliminar pagos de caja.
    mvc.perform(
            delete("/api/v1/reservas/{id}/pagos/{idPago}", idReserva, idPagoYape)
                .header("Authorization", "Bearer " + tokenGerente))
        .andExpect(status().isForbidden());

    // Regla: un pago acreditado en línea vía MERCADO_PAGO no se elimina desde caja.
    jdbc.update("update pago_yape set modo = 'MERCADO_PAGO' where idPago = ?", idPagoYape);
    mvc.perform(
            delete("/api/v1/reservas/{id}/pagos/{idPago}", idReserva, idPagoYape)
                .header("Authorization", "Bearer " + tokenRecepcionista))
        .andExpect(status().isConflict());

    // Al volver a SIMULADO en borrador, turno abierto y estancia activa, elimina pago y flujo de caja.
    jdbc.update("update pago_yape set modo = 'SIMULADO' where idPago = ?", idPagoYape);
    mvc.perform(
            delete("/api/v1/reservas/{id}/pagos/{idPago}", idReserva, idPagoYape)
                .header("Authorization", "Bearer " + tokenRecepcionista))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.saldoPendiente").value(80.0))
        .andExpect(jsonPath("$.totalPagado").value(0.0));

    assertThat(pagos.findById(idPagoYape)).isEmpty();
    Integer flujosRestantes =
        jdbc.queryForObject(
            "select count(*) from flujo_caja where idPago = ?", Integer.class, idPagoYape);
    assertThat(flujosRestantes).isZero();
    String estadoYape =
        jdbc.queryForObject(
            "select estado from pago_yape where claveOperacion = ?",
            String.class,
            claveParcial);
    assertThat(estadoYape).isEqualTo("RECHAZADO");

    // Regla: no permite eliminar si el turno cerró o el comprobante ya fue emitido.
    String claveSegundo = UUID.randomUUID().toString();
    mvc.perform(
            post("/api/v1/reservas/{id}/yape", idReserva)
                .header("Authorization", "Bearer " + tokenRecepcionista)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "claveOperacion",
                            claveSegundo,
                            "importe",
                            80.00,
                            "telefono",
                            "987654321",
                            "otp",
                            "123456"))))
        .andExpect(status().isOk());
    Long idPagoSegundo = pagos.findByClaveIdempotencia("YAPE-" + claveSegundo).orElseThrow().getIdPago();

    tx.executeWithoutResult(
        s -> {
          var p = pagos.findById(idPagoSegundo).orElseThrow();
          p.getComprobante().setEstadoComprobante(pe.com.rebolsysBackend.enums.EstadoComprobante.EMITIDO);
        });
    mvc.perform(
            delete("/api/v1/reservas/{id}/pagos/{idPago}", idReserva, idPagoSegundo)
                .header("Authorization", "Bearer " + tokenRecepcionista))
        .andExpect(status().isConflict());

    tx.executeWithoutResult(
        s -> {
          var p = pagos.findById(idPagoSegundo).orElseThrow();
          p.getComprobante().setEstadoComprobante(pe.com.rebolsysBackend.enums.EstadoComprobante.BORRADOR);
          p.getTurnoCaja().setEstadoSesion(0);
          p.getTurnoCaja().setFechaCierre(LocalDateTime.now());
        });
    mvc.perform(
            delete("/api/v1/reservas/{id}/pagos/{idPago}", idReserva, idPagoSegundo)
                .header("Authorization", "Bearer " + tokenRecepcionista))
        .andExpect(status().isConflict());
  }

  private record DatosPrueba(Long idRec, Long idGer, Long idCliente, Long idHabitacion) {}
}
