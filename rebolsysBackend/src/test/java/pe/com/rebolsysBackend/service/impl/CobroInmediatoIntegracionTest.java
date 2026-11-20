package pe.com.rebolsysBackend.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import pe.com.rebolsysBackend.config.MockMvcReglasNegocio;
import tools.jackson.databind.ObjectMapper;
import pe.com.rebolsysBackend.dto.*;
import pe.com.rebolsysBackend.entity.*;
import pe.com.rebolsysBackend.entity.id.SerieComprobanteId;
import pe.com.rebolsysBackend.enums.*;
import pe.com.rebolsysBackend.service.service.*;

/** Transacciones reales sobre una base aislada; no escribe en Oracle ni cobra a proveedores. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:cobros;MODE=Oracle;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"
})
@ActiveProfiles("cobros-test")
@MockMvcReglasNegocio
class CobroInmediatoIntegracionTest {
  @Autowired EntityManager em;
  @Autowired TransactionTemplate tx;
  @Autowired ReservaService reservas;
  @Autowired VentaService ventas;
  @Autowired CuentaReservaService cuentasReserva;
  @Autowired CuentaVentaService cuentasVenta;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @MockitoBean(name = "apisPeruRestClient") RestClient consultaDocumentos;
  @MockitoBean ProveedorFacturacionClient facturacion;
  Long usuario, cliente, habitacion, producto, forma, turno;

  @BeforeEach
  void preparar() {
    tx.executeWithoutResult(s -> {
      String clave = UUID.randomUUID().toString().substring(0, 8);
      var td = new TipoDocumento(); td.setNombreDocumento("DNI " + clave); td.setCodigoSunat("1"); em.persist(td);
      var p = new Perfil(); p.setTipoDocumento(td); p.setNumeroDocumento(clave); p.setEsCliente(1); em.persist(p);
      cliente = p.getIdPerfil();
      var n = new PersonaNatural(); n.setPerfil(p); n.setNombres("Lucía"); n.setApellidos("Rojas");
      n.setFechaNacimiento(LocalDate.of(1990, 1, 1)); em.persist(n);
      var e = new Empleado(); e.setPersonaNatural(n); e.setFechaContratacion(LocalDate.now()); em.persist(e);
      var u = new Usuario(); u.setEmpleado(e); u.setUsername("cobro-" + clave); u.setEstadoUsuario(EstadoUsuario.ACTIVO); em.persist(u);
      usuario = u.getIdUsuario();
      var t = new TurnoCaja(); t.setUsuario(u); t.setEstadoSesion(1); em.persist(t); turno = t.getIdTurno();
      var th = new TipoHabitacion(); th.setNombreTipo("Simple " + clave); th.setCapacidadMax(1);
      th.setPrecioBase(new BigDecimal("80.00")); em.persist(th);
      var h = new Habitacion(); h.setNumeroCuarto(clave); h.setTipoHabitacion(th);
      h.setEstadoHabitacion(EstadoHabitacion.DISPONIBLE); h.setCapacidadPersonas(1); h.setPlazasCama(1); em.persist(h);
      habitacion = h.getIdHabitacion();
      var cat = new Categoria(); cat.setNombreCategoria("Bebidas " + clave); em.persist(cat);
      var pr = new Producto(); pr.setNombreProducto("Agua " + clave); pr.setCategoria(cat);
      pr.setPrecioActual(new BigDecimal("3.50")); pr.setStock(20); pr.setEstadoProducto(EstadoProducto.ACTIVO); em.persist(pr);
      producto = pr.getIdProducto();
      var fp = new FormaPago(); fp.setNombrePago("Efectivo " + clave); em.persist(fp); forma = fp.getIdFormaPago();
      if (cantidad("TipoMovimientoAlmacen", "codigo", "VENTA") == 0) {
        var m = new TipoMovimientoAlmacen(); m.setCodigo("VENTA"); m.setNombreMovimiento("Venta"); m.setSigno(-1); em.persist(m);
      }
      for (String codigo : List.of("COBRO_VENTA", "COBRO_RESERVA"))
        if (cantidad("TipoMovimientoCaja", "nombreMovimiento", codigo) == 0) {
          var m = new TipoMovimientoCaja(); m.setNombreMovimiento(codigo); m.setSigno(1); em.persist(m);
        }
      if (cantidad("TipoComprobante", "codigo", "CUENTA") == 0) {
        var tipo = new TipoComprobante(); tipo.setCodigo("CUENTA"); tipo.setNombreComprobante("Cuenta"); em.persist(tipo);
        var serie = new SerieComprobante(); serie.setId(new SerieComprobanteId(tipo.getIdTipoComprobante(), "CTA"));
        serie.setTipoComprobante(tipo); em.persist(serie);
      }
    });
  }

  private long cantidad(String entidad, String campo, Object valor) {
    return em.createQuery("select count(e) from " + entidad + " e where e." + campo + "=:v", Long.class)
        .setParameter("v", valor).getSingleResult();
  }

  private CobroInmediatoDTO cobro(String monto) { return new CobroInmediatoDTO(forma, new BigDecimal(monto), null); }
  private ReservaRegistroRequestDTO reserva(String clave, String monto) {
    var ahora = ReglasPropuesta.ahora();
    return new ReservaRegistroRequestDTO(clave, cliente, usuario,
        List.of(new ReservaDetalleRegistroDTO(habitacion, ahora.minusHours(1), ahora.plusHours(22),
            new BigDecimal("80.00"), List.of(new ReservaOcupanteRegistroDTO(cliente, null, null, true)))), cobro(monto));
  }
  private VentaRequestDTO venta(String clave, Long estancia, CobroInmediatoDTO pago) {
    return new VentaRequestDTO(clave, usuario, cliente, null,
        estancia == null ? ModalidadCobro.SEPARADA : ModalidadCobro.RESERVA, estancia,
        List.of(new VentaLineaRegistroDTO(producto, 2, new BigDecimal("3.50"))), pago);
  }
  private int stock() { return tx.execute(s -> em.find(Producto.class, producto).getStock()); }
  private void sinVenta(String clave) {
    assertThat(stock()).isEqualTo(20);
    tx.executeWithoutResult(s -> {
      assertThat(cantidad("Venta", "claveRegistro", clave)).isZero();
      assertThat(cantidad("MovimientoInventario", "producto.idProducto", producto)).isZero();
    });
  }

  @Test void reservaSePagaCompletaYReintentarNoDuplicaPagos() {
    var r = reserva(UUID.randomUUID().toString(), "80.00");
    var primera = reservas.registrar(r);
    assertThat(reservas.registrar(r).idReserva()).isEqualTo(primera.idReserva());
    var cuenta = cuentasReserva.cuenta(primera.idReserva());
    assertThat(cuenta.saldoPendiente()).isEqualByComparingTo("0");
    assertThat(cuenta.totalPagado()).isEqualByComparingTo("80");
    assertThat(cuenta.admiteConsumosConsolidados()).isFalse();
    assertThat(cuenta.cuentas().getFirst().pagos()).hasSize(1);
  }

  @Test void pagoParcialOExcesivoRevierteReservaYOcupantes() {
    for (String monto : List.of("20.00", "81.00")) {
      var r = reserva(UUID.randomUUID().toString(), monto);
      assertThatThrownBy(() -> reservas.registrar(r)).hasMessageContaining("exactamente");
      tx.executeWithoutResult(s -> {
        assertThat(cantidad("Reserva", "claveRegistro", r.claveRegistro())).isZero();
        assertThat(cantidad("ReservaOcupante", "huesped.perfil.idPerfil", cliente)).isZero();
      });
    }
  }

  @Test void ventaRechazaPagoAusenteParcialYExcesivoSinTocarStock() {
    for (CobroInmediatoDTO pago : Arrays.asList(null, cobro("1.00"), cobro("8.00"))) {
      var v = venta(UUID.randomUUID().toString(), null, pago);
      assertThatThrownBy(() -> ventas.registrarConfirmada(v));
      sinVenta(v.claveRegistro());
    }
  }

  @Test void falloDeCobroPosteriorAlDescuentoRevierteVentaMovimientoYStock() {
    var v = venta(UUID.randomUUID().toString(), null, new CobroInmediatoDTO(Long.MAX_VALUE, new BigDecimal("7.00"), null));
    assertThatThrownBy(() -> ventas.registrarConfirmada(v)).hasMessageContaining("forma de pago no existe");
    sinVenta(v.claveRegistro());
  }

  @Test void ventaPagadaReintentoYColisionDeClave() {
    var v = venta(UUID.randomUUID().toString(), null, cobro("7.00"));
    var primera = ventas.registrarConfirmada(v);
    tx.executeWithoutResult(s -> { var t = em.find(TurnoCaja.class, turno); t.setEstadoSesion(0); t.setFechaCierre(ReglasPropuesta.ahora()); });
    assertThat(ventas.registrarConfirmada(v).idVenta()).isEqualTo(primera.idVenta());
    assertThat(stock()).isEqualTo(18);
    var cuenta = cuentasVenta.cuenta(primera.idVenta());
    assertThat(cuenta.saldo()).isEqualByComparingTo("0");
    assertThat(cuenta.pagos()).hasSize(1);
    assertThatThrownBy(() -> ventas.registrarConfirmada(venta(v.claveRegistro(), null, cobro("6.00"))))
        .hasMessageContaining("otra solicitud");
  }

  @Test void consumoPagadoNoAumentaDeudaDeReservaYPermiteCheckout() {
    var r = reservas.registrar(reserva(UUID.randomUUID().toString(), "80.00"));
    Long d = r.detalles().getFirst().idDetalleReserva();
    reservas.cambiarEstado(d, "ingreso", new OperacionUsuarioDTO(usuario));
    var v = ventas.registrarConfirmada(venta(UUID.randomUUID().toString(), d, cobro("7.00")));
    var cuenta = cuentasReserva.cuenta(r.idReserva());
    assertThat(cuenta.totalPagado()).isEqualByComparingTo("87.00");
    assertThat(cuenta.saldoPendiente()).isEqualByComparingTo("0");
    assertThat(cuenta.cuentas()).hasSize(2);
    assertThat(cuentasVenta.cuenta(v.idVenta()).saldo()).isEqualByComparingTo("0");
    tx.executeWithoutResult(s -> {
      var pago = em.createQuery("from Pago p where p.comprobante.venta.idVenta=:id", Pago.class)
          .setParameter("id", v.idVenta()).getSingleResult();
      var flujo = em.createQuery("from FlujoCaja f where f.pago.idPago=:id", FlujoCaja.class)
          .setParameter("id", pago.getIdPago()).getSingleResult();
      assertThat(flujo.getTipoMovimientoCaja().getNombreMovimiento()).isEqualTo("COBRO_VENTA");
    });
    assertThat(reservas.cambiarEstado(d, "salida", new OperacionUsuarioDTO(usuario)).estadoDetalle())
        .isEqualTo(EstadoReserva.FINALIZADA);
  }

  @Test void borradorSoloConfirmaConPagoYFalloConservaBorradorSinMovimientos() {
    var borrador = ventas.registrar(venta(UUID.randomUUID().toString(), null, null));
    assertThatThrownBy(() -> ventas.confirmar(borrador.idVenta(), new OperacionUsuarioDTO(usuario)))
        .hasMessageContaining("pago completo");
    assertThatThrownBy(() -> ventas.confirmarCobrada(borrador.idVenta(), new VentaConfirmacionDTO(usuario, cobro("1.00"))));
    assertThat(ventas.obtener(borrador.idVenta()).estadoVenta()).isEqualTo(EstadoVenta.BORRADOR);
    assertThat(stock()).isEqualTo(20);
    ventas.confirmarCobrada(borrador.idVenta(), new VentaConfirmacionDTO(usuario, cobro("7.00")));
    assertThat(stock()).isEqualTo(18);
  }

  @Test void dosReintentosSimultaneosSoloCobranYDescuentanUnaVez() throws Exception {
    var v = venta(UUID.randomUUID().toString(), null, cobro("7.00"));
    var inicio = new CountDownLatch(1);
    try (var ejecutor = Executors.newFixedThreadPool(2)) {
      Callable<Long> operacion = () -> { inicio.await(); return ventas.registrarConfirmada(v).idVenta(); };
      var a = ejecutor.submit(operacion); var b = ejecutor.submit(operacion); inicio.countDown();
      var id = a.get(20, TimeUnit.SECONDS);
      assertThat(b.get(20, TimeUnit.SECONDS)).isEqualTo(id);
      assertThat(cuentasVenta.cuenta(id).pagos()).hasSize(1);
      assertThat(stock()).isEqualTo(18);
    }
  }

  @Test void apiExigeCobroYExponeElNuevoContrato() throws Exception {
    var r = reserva(UUID.randomUUID().toString(), "80.00");
    var cuerpo = (tools.jackson.databind.node.ObjectNode) json.valueToTree(r);
    cuerpo.remove("cobro");
    mvc.perform(post("/api/v1/reservas").contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(cuerpo))).andExpect(status().isBadRequest());
    mvc.perform(post("/api/v1/reservas").contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(r))).andExpect(status().isCreated());
    mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
        .andExpect(jsonPath("$.components.schemas.CobroInmediatoDTO.required").isArray())
        .andExpect(jsonPath("$.paths['/api/v1/ventas/{id}/confirmacion'].post.requestBody.content['application/json'].schema['$ref']")
            .value("#/components/schemas/VentaConfirmacionDTO"));
  }

  @Test void faltaDeStockYTurnoCerradoNoDejanRegistrosParciales() {
    tx.executeWithoutResult(s -> em.find(Producto.class, producto).setStock(1));
    var v = venta(UUID.randomUUID().toString(), null, cobro("7.00"));
    assertThatThrownBy(() -> ventas.registrarConfirmada(v)).hasMessageContaining("Stock insuficiente");
    assertThat(stock()).isEqualTo(1);
    tx.executeWithoutResult(s -> {
      assertThat(cantidad("Venta", "claveRegistro", v.claveRegistro())).isZero();
      var t = em.find(TurnoCaja.class, turno); t.setEstadoSesion(0); t.setFechaCierre(ReglasPropuesta.ahora());
    });
    var r = reserva(UUID.randomUUID().toString(), "80.00");
    assertThatThrownBy(() -> reservas.registrar(r)).hasMessageContaining("turno abierto");
    tx.executeWithoutResult(s -> assertThat(cantidad("Reserva", "claveRegistro", r.claveRegistro())).isZero());
  }

  @Test void confirmarBorradorPagadoSeRecuperaConTurnoCerradoYRechazaOtroPago() {
    var borrador = ventas.registrar(venta(UUID.randomUUID().toString(), null, null));
    var pago = new VentaConfirmacionDTO(usuario, cobro("7.00"));
    ventas.confirmarCobrada(borrador.idVenta(), pago);
    tx.executeWithoutResult(s -> { var t = em.find(TurnoCaja.class, turno); t.setEstadoSesion(0); t.setFechaCierre(ReglasPropuesta.ahora()); });
    assertThat(ventas.confirmarCobrada(borrador.idVenta(), pago).estadoVenta()).isEqualTo(EstadoVenta.CONFIRMADA);
    assertThat(cuentasVenta.cuenta(borrador.idVenta()).pagos()).hasSize(1);
    assertThat(stock()).isEqualTo(18);
    assertThatThrownBy(() -> ventas.confirmarCobrada(borrador.idVenta(), new VentaConfirmacionDTO(usuario,
        new CobroInmediatoDTO(forma, new BigDecimal("7.00"), "otro-numero"))))
        .hasMessageContaining("otro pago");
  }

  @Test void saldosHistoricosSeConservanYLosNuevosConsumosNoLosAumentan() {
    Long id = tx.execute(s -> {
      var r = new Reserva(); r.setCliente(em.find(Perfil.class, cliente)); r.setUsuario(em.find(Usuario.class, usuario));
      r.setClaveRegistro(UUID.randomUUID().toString()); r.setFirmaSolicitud("historica");
      var d = new DetalleReserva(); d.setHabitacion(em.find(Habitacion.class, habitacion));
      d.setFechaIngreso(ReglasPropuesta.ahora().minusHours(1)); d.setFechaSalida(ReglasPropuesta.ahora().plusDays(1));
      d.setFechaIngresoReal(ReglasPropuesta.ahora().minusHours(1)); d.setCostoEstadia(new BigDecimal("80.00"));
      d.setEstadoDetalle(EstadoReserva.EN_CURSO); r.agregarDetalle(d); em.persist(r);
      return r.getIdReserva();
    });
    Long estancia = reservas.obtener(id).detalles().getFirst().idDetalleReserva();
    assertThat(cuentasReserva.cuenta(id).saldoPendiente()).isEqualByComparingTo("80");
    cuentasReserva.pagar(id, new ReservaPagoRegistroDTO(UUID.randomUUID().toString(), usuario, turno,
        forma, new BigDecimal("40.00"), null, null));
    ventas.registrarConfirmada(venta(UUID.randomUUID().toString(), estancia, cobro("7.00")));
    var cuenta = cuentasReserva.cuenta(id);
    assertThat(cuenta.totalCargos()).isEqualByComparingTo("87");
    assertThat(cuenta.totalPagado()).isEqualByComparingTo("47");
    assertThat(cuenta.saldoPendiente()).isEqualByComparingTo("40");
    assertThatThrownBy(() -> reservas.cambiarEstado(estancia, "salida", new OperacionUsuarioDTO(usuario)))
        .hasMessageContaining("Liquida el saldo");
  }

  @Test void ventaGratuitaNoInsertaPagoDeImporteCero() {
    tx.executeWithoutResult(s -> em.find(Producto.class, producto).setPrecioActual(BigDecimal.ZERO));
    var solicitud = new VentaRequestDTO(UUID.randomUUID().toString(), usuario, cliente, null,
        ModalidadCobro.SEPARADA, null, List.of(new VentaLineaRegistroDTO(producto, 1, BigDecimal.ZERO)), cobro("0.00"));
    var v = ventas.registrarConfirmada(solicitud);
    var cuenta = cuentasVenta.cuenta(v.idVenta());
    assertThat(cuenta.saldo()).isEqualByComparingTo("0");
    assertThat(cuenta.pagos()).isEmpty();
    assertThat(stock()).isEqualTo(19);
  }

  @Test void noAceptaAlojamientoManipuladoNiTarifaDesactualizada() {
    var original = reserva(UUID.randomUUID().toString(), "80.00");
    var d = original.detalles().getFirst();
    var reducida = new ReservaRegistroRequestDTO(original.claveRegistro(), cliente, usuario,
        List.of(new ReservaDetalleRegistroDTO(habitacion, d.fechaIngreso(), d.fechaSalida(),
            BigDecimal.ONE, d.ocupantes())), cobro("1.00"));
    assertThatThrownBy(() -> reservas.registrar(reducida)).hasMessageContaining("tarifa o las fechas cambiaron");
    tx.executeWithoutResult(s -> em.find(Habitacion.class, habitacion).setPrecioPorNoche(new BigDecimal("90.00")));
    assertThatThrownBy(() -> reservas.registrar(original)).hasMessageContaining("tarifa o las fechas cambiaron");
    tx.executeWithoutResult(s -> assertThat(cantidad("Reserva", "claveRegistro", original.claveRegistro())).isZero());
  }
}
