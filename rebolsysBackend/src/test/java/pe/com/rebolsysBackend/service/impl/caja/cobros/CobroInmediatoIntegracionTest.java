package pe.com.rebolsysBackend.service.impl.caja.cobros;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import pe.com.rebolsysBackend.dto.caja.cobros.CobroInmediatoDTO;
import pe.com.rebolsysBackend.dto.comun.OperacionUsuarioDTO;
import pe.com.rebolsysBackend.dto.mantenimiento.ClienteRegistroRequestDTO;
import pe.com.rebolsysBackend.dto.reservas.ReservaComprobanteRegistroDTO;
import pe.com.rebolsysBackend.dto.reservas.ReservaDetalleRegistroDTO;
import pe.com.rebolsysBackend.dto.reservas.ReservaOcupanteRegistroDTO;
import pe.com.rebolsysBackend.dto.reservas.ReservaOcupantesGuardarDTO;
import pe.com.rebolsysBackend.dto.reservas.ReservaPagoRegistroDTO;
import pe.com.rebolsysBackend.dto.reservas.ReservaRegistroRequestDTO;
import pe.com.rebolsysBackend.dto.reservas.ReservaSalidaDTO;
import pe.com.rebolsysBackend.dto.ventas.VentaConfirmacionDTO;
import pe.com.rebolsysBackend.dto.ventas.VentaLineaRegistroDTO;
import pe.com.rebolsysBackend.dto.ventas.VentaRequestDTO;
import pe.com.rebolsysBackend.service.impl.comun.ReglasPropuesta;
import pe.com.rebolsysBackend.service.impl.reservas.CheckoutVencidoService;
import pe.com.rebolsysBackend.service.service.comun.comprobantes.ProveedorFacturacionClient;
import pe.com.rebolsysBackend.service.service.mantenimiento.ClienteService;
import pe.com.rebolsysBackend.service.service.reservas.CuentaReservaService;
import pe.com.rebolsysBackend.service.service.reservas.ReservaService;
import pe.com.rebolsysBackend.service.service.ventas.CuentaVentaService;
import pe.com.rebolsysBackend.service.service.ventas.VentaService;
import pe.com.rebolsysBackend.config.MockMvcReglasNegocio;
import tools.jackson.databind.ObjectMapper;
import pe.com.rebolsysBackend.entity.*;
import pe.com.rebolsysBackend.entity.id.SerieComprobanteId;
import pe.com.rebolsysBackend.enums.*;

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
  @Autowired ClienteService clientes;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired CheckoutVencidoService checkout;
  @Autowired pe.com.rebolsysBackend.repository.reservas.DetalleReservaRepository detalles;
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

  @Test
  void registraYBuscaTitularesSinDuplicarElDocumento() throws Exception {
    Long pasaporte = tx.execute(s -> {
      var tipo = new TipoDocumento();
      tipo.setNombreDocumento("PASAPORTE TITULAR");
      tipo.setCodigoSunat("7");
      em.persist(tipo);
      return tipo.getIdTipoDocumento();
    });
    var mayor = ReglasPropuesta.ahora().toLocalDate().minusYears(18);
    String ruta = "/api/v1/recepcion/clientes/titulares";
    var persona = new ClienteRegistroRequestDTO(pasaporte, "ax091", false, "Alejandra", "Quispe Flores",
        mayor, null, null, null, null);
    var respuesta = mvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(persona))).andExpect(status().isOk()).andReturn();
    long id = json.readTree(respuesta.getResponse().getContentAsByteArray()).get("idCliente").asLong();
    var duplicado = new ClienteRegistroRequestDTO(pasaporte, "AX091", false, "Nombre", "Alternativo",
        mayor, null, null, null, null);
    mvc.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(duplicado)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.idCliente").value(id))
        .andExpect(jsonPath("$.persona.nombres").value("Alejandra"));
    mvc.perform(get(ruta).param("empresa", "false").param("nombre", "ALEJANDRA QUISPE FLORES"))
        .andExpect(status().isOk()).andExpect(jsonPath("$[0].idCliente").value(id));
    mvc.perform(get(ruta).param("empresa", "false").param("tipoDocumento", pasaporte.toString())
        .param("numeroDocumento", "ax091"))
        .andExpect(status().isOk()).andExpect(jsonPath("$[0].idCliente").value(id));
    mvc.perform(get(ruta).param("empresa", "true").param("numeroDocumento", "ax091"))
        .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    var menor = new ClienteRegistroRequestDTO(pasaporte, "AX092", false, "Ana", "Prueba",
        mayor.plusMonths(1), null, null, null, null);
    assertThatThrownBy(() -> clientes.registrarTitular(menor)).hasMessageContaining("18 años");
    var empresaConPasaporte = new ClienteRegistroRequestDTO(pasaporte, "AX093", true, null, null,
        null, "Empresa de prueba", null, null, null);
    assertThatThrownBy(() -> clientes.registrarTitular(empresaConPasaporte)).hasMessageContaining("RUC");
  }

  @Test
  void marcaElIngresoVencidoSinCambiarElEstadoDeLaEstancia() {
    var creada = reservas.registrar(reserva(UUID.randomUUID().toString(), "80.00"));
    var detalle = creada.detalles().getFirst();
    assertThat(detalle.ingresoVencido()).isFalse();
    tx.executeWithoutResult(s -> em.find(DetalleReserva.class, detalle.idDetalleReserva())
        .setFechaIngreso(ReglasPropuesta.ahora().minusHours(3)));
    var pendiente = reservas.obtener(creada.idReserva()).detalles().getFirst();
    assertThat(pendiente.ingresoVencido()).isTrue();
    assertThat(pendiente.estadoDetalle()).isEqualTo(EstadoReserva.CONFIRMADA);
    var ingresada = reservas.cambiarEstado(detalle.idDetalleReserva(), "ingreso", new OperacionUsuarioDTO(usuario));
    assertThat(ingresada.ingresoVencido()).isFalse();
  }

  @Test
  void rechazaLaMismaReferenciaEnOtraCuentaSinRegistrarUnSegundoCobro() {
    var solicitud = reserva(UUID.randomUUID().toString(), "80.00");
    String referencia = "AB004812";
    var original = new ReservaRegistroRequestDTO(solicitud.claveRegistro(), solicitud.idCliente(),
        solicitud.idUsuario(), solicitud.detalles(), new CobroInmediatoDTO(forma, new BigDecimal("80.00"), referencia));
    var registrada = reservas.registrar(original);
    assertThat(reservas.registrar(original).idReserva()).isEqualTo(registrada.idReserva());
    var otro = venta(UUID.randomUUID().toString(), null, new CobroInmediatoDTO(forma, new BigDecimal("7.00"), " ab004812 "));
    assertThatThrownBy(() -> ventas.registrarConfirmada(otro))
        .hasMessageContaining("ya está registrada en la reserva R" + registrada.idReserva());
    sinVenta(otro.claveRegistro());
    tx.executeWithoutResult(s -> assertThat(cantidad("Pago", "numeroOperacion", referencia)).isEqualTo(1));
  }

  private CobroInmediatoDTO cobro(String monto) { return new CobroInmediatoDTO(forma, new BigDecimal(monto), null); }
  private ReservaRegistroRequestDTO reserva(String clave, String monto) {
    var ahora = ReglasPropuesta.ahora();
    return new ReservaRegistroRequestDTO(clave, cliente, usuario,
        List.of(new ReservaDetalleRegistroDTO(habitacion, ahora.plusHours(1), ahora.plusDays(1),
            new BigDecimal("80.00"), List.of(new ReservaOcupanteRegistroDTO(cliente, null, null, true)))), cobro(monto));
  }
  private void prepararIngresoAhora(Long id) {
    var ahora = ReglasPropuesta.ahora();
    tx.executeWithoutResult(s -> {
      var detalle = em.find(DetalleReserva.class, id);
      detalle.setFechaIngreso(ahora.minusMinutes(1));
      detalle.setFechaSalida(ahora.plusHours(22));
    });
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
    assertThat(cuenta.admiteConsumosConsolidados()).isTrue();
    assertThat(cuenta.cuentas().getFirst().pagos()).hasSize(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"10", "20"})
  void facturaConRucConservaReservaConDniHuespedYPago(String prefijo) {
    Long tipoRuc = tx.execute(s -> {
      em.find(Perfil.class, cliente).setNumeroDocumento("12345678");
      var documentos = em.createQuery("from TipoDocumento where codigoSunat='6'", TipoDocumento.class).getResultList();
      TipoDocumento td;
      if (documentos.isEmpty()) {
        td = new TipoDocumento(); td.setNombreDocumento("RUC de facturación"); td.setCodigoSunat("6"); em.persist(td);
      } else td = documentos.getFirst();
      var comprobantes = em.createQuery("from TipoComprobante where codigo='FACTURA'", TipoComprobante.class).getResultList();
      if (comprobantes.isEmpty()) {
        var tipo = new TipoComprobante(); tipo.setCodigo("FACTURA"); tipo.setNombreComprobante("Factura"); em.persist(tipo);
        var serie = new SerieComprobante(); serie.setId(new SerieComprobanteId(tipo.getIdTipoComprobante(), "F002"));
        serie.setTipoComprobante(tipo); em.persist(serie);
      }
      return td.getIdTipoDocumento();
    });
    boolean empresa = prefijo.equals("20");
    var fiscal = new ClienteRegistroRequestDTO(tipoRuc, prefijo + "123456789", empresa,
        empresa ? null : "Lucía", empresa ? null : "Rojas", null,
        empresa ? "Operaciones Altiplano SAC" : null, "Av. Los Andes 450", null, null);
    Long receptor = clientes.guardarCliente(fiscal).getIdPerfil();
    assertThat(clientes.guardarCliente(fiscal).getIdPerfil()).isEqualTo(receptor);

    var reserva = reservas.registrar(reserva(UUID.randomUUID().toString(), "80.00"));
    finalizarPagada(reserva.detalles().getFirst().idDetalleReserva());
    var antes = cuentasReserva.cuenta(reserva.idReserva()).cuentas().getFirst();
    var solicitud = new ReservaComprobanteRegistroDTO(usuario, turno, "FACTURA", receptor, null);
    var factura = cuentasReserva.emitir(reserva.idReserva(), solicitud);
    assertThat(factura.serie()).isEqualTo("F002");
    assertThat(cuentasReserva.emitir(reserva.idReserva(), solicitud).idCuenta()).isEqualTo(factura.idCuenta());
    assertThat(factura.idCuenta()).isEqualTo(antes.idCuenta());
    assertThat(factura.idClienteReceptor()).isEqualTo(receptor);
    assertThat(factura.receptorNumeroDoc()).isEqualTo(fiscal.numeroDocumento());
    assertThat(factura.tipo()).isEqualTo("FACTURA");
    assertThat(factura.pagado()).isEqualByComparingTo("80.00");
    assertThat(factura.saldo()).isZero();
    assertThat(factura.pagos()).hasSize(1);
    assertThat(factura.pagos().getFirst().idPago()).isEqualTo(antes.pagos().getFirst().idPago());
    tx.executeWithoutResult(s -> {
      var original = em.find(Reserva.class, reserva.idReserva());
      assertThat(original.getCliente().getIdPerfil()).isEqualTo(cliente);
      assertThat(original.getCliente().getNumeroDocumento()).isEqualTo("12345678");
      assertThat(cantidad("Reserva", "idReserva", reserva.idReserva())).isEqualTo(1);
      assertThat(cantidad("Comprobante", "reserva.idReserva", reserva.idReserva())).isEqualTo(1);
      assertThat(cantidad("ReservaOcupante", "huesped.perfil.idPerfil", cliente)).isEqualTo(1);
      assertThat(cantidad("Pago", "comprobante.idComprobante", factura.idCuenta())).isEqualTo(1);
    });
  }

  @Test void boletaNuevaUsaB002YNoAvanzaElContadorAlRecuperarla() {
    var inicio = tx.execute(s -> {
      em.find(Perfil.class, cliente).setNumeroDocumento("12345678");
      var id = prepararBoleta();
      return Map.entry(id, em.find(SerieComprobante.class, id).getSiguienteNumero());
    });
    var reserva = reservas.registrar(reserva(UUID.randomUUID().toString(), "80.00"));
    finalizarPagada(reserva.detalles().getFirst().idDetalleReserva());
    var solicitud = new ReservaComprobanteRegistroDTO(usuario, turno, "BOLETA", cliente, null);
    var boleta = cuentasReserva.emitir(reserva.idReserva(), solicitud);
    assertThat(boleta.serie()).isEqualTo("B002"); assertThat(boleta.numero()).isEqualTo(inicio.getValue().toString());
    assertThat(cuentasReserva.emitir(reserva.idReserva(), solicitud).idCuenta()).isEqualTo(boleta.idCuenta());
    tx.executeWithoutResult(s -> assertThat(em.find(SerieComprobante.class, inicio.getKey()).getSiguienteNumero())
        .isEqualTo(inicio.getValue() + 1L));
  }

  private void finalizarPagada(Long id) {
    prepararIngresoAhora(id);
    reservas.cambiarEstado(id, "ingreso", new OperacionUsuarioDTO(usuario));
    reservas.cambiarEstado(id, "salida", new OperacionUsuarioDTO(usuario));
  }

  @Test void pagoExcesivoRevierteReservaYOcupantes() {
    for (String monto : List.of("81.00", "90.00")) {
      var r = reserva(UUID.randomUUID().toString(), monto);
      assertThatThrownBy(() -> reservas.registrar(r)).hasMessageContaining("supera el saldo");
      tx.executeWithoutResult(s -> {
        assertThat(cantidad("Reserva", "claveRegistro", r.claveRegistro())).isZero();
        assertThat(cantidad("ReservaOcupante", "huesped.perfil.idPerfil", cliente)).isZero();
      });
    }
  }

  @Test void reservaSinPagoPuedeCancelarseSinTurno() {
    var r = reserva(UUID.randomUUID().toString(), "80.00");
    tx.executeWithoutResult(s -> {
      var t = em.find(TurnoCaja.class, turno);
      t.setEstadoSesion(0);
      t.setFechaCierre(ReglasPropuesta.ahora());
    });
    var guardada = reservas.registrar(new ReservaRegistroRequestDTO(r.claveRegistro(), cliente, usuario, r.detalles()));
    assertThat(cuentasReserva.cuenta(guardada.idReserva()).saldoPendiente()).isEqualByComparingTo("80");
    assertThat(reservas.cambiarEstado(guardada.detalles().getFirst().idDetalleReserva(), "cancelacion",
        new OperacionUsuarioDTO(usuario)).estadoDetalle()).isEqualTo(EstadoReserva.CANCELADA);
    assertThat(cuentasReserva.cuenta(guardada.idReserva()).saldoPendiente()).isZero();
  }

  @Test void gerenteSinRolRecepcionistaNoPuedeCobrarReservaPorHttp() throws Exception {
    mvc.perform(post("/api/v1/reservas").contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(reserva(UUID.randomUUID().toString(), "20.00"))))
        .andExpect(status().isForbidden());
  }

  @Test
  @org.springframework.security.test.context.support.WithMockUser(authorities = {
      "ROLE_RECEPCIONISTA", "RESERVAS:CREAR", "RESERVAS:COBRAR"})
  void recepcionistaConPermisoPuedeRegistrarAnticipoPorHttp() throws Exception {
    mvc.perform(post("/api/v1/reservas").contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(reserva(UUID.randomUUID().toString(), "20.00"))))
        .andExpect(status().isCreated());
  }

  @Test void reintentoDeSalidaSinVentaNiNuevoCobroDevuelveElMismoComprobante() {
    var reserva = reservas.registrar(reserva(UUID.randomUUID().toString(), "80.00"));
    Long estancia = reserva.detalles().getFirst().idDetalleReserva();
    var operadorSalida = nuevoOperador();
    prepararIngresoAhora(estancia);
    reservas.cambiarEstado(estancia, "ingreso", new OperacionUsuarioDTO(usuario));
    var emision = new ReservaSalidaDTO.Comprobante("BOLETA", cliente);
    var sinCobro = new ReservaSalidaDTO(operadorSalida.usuario(), null, UUID.randomUUID().toString(), null, emision);
    var salidaSoloComprobante = reservas.registrarSalida(estancia, sinCobro);
    assertThat(reservas.registrarSalida(estancia, sinCobro)).isEqualTo(salidaSoloComprobante);
    assertThat(salidaSoloComprobante.idPago()).isNull();
    assertThat(salidaSoloComprobante.idComprobante()).isNotNull();
    assertThatThrownBy(() -> reservas.registrarSalida(estancia,
        new ReservaSalidaDTO(operadorSalida.usuario(), null, UUID.randomUUID().toString(), null, emision)))
        .hasMessageContaining("clave de salida");
    assertThat(reservas.registrarSalida(estancia, new ReservaSalidaDTO(usuario, null, null, null, null))
        .estadoDetalle()).isEqualTo(EstadoReserva.FINALIZADA);
  }

  @Test void liquidaConsumoConTresOperadoresYRecuperaSalidaDelTurnoCerrado() throws Exception {
    var reserva = reservas.registrar(reserva(UUID.randomUUID().toString(), "20.00"));
    Long estancia = reserva.detalles().getFirst().idDetalleReserva();
    var operadorVenta = nuevoOperador();
    var operadorSalida = nuevoOperador();
    prepararIngresoAhora(estancia);
    reservas.cambiarEstado(estancia, "ingreso", new OperacionUsuarioDTO(operadorVenta.usuario()));
    var consumo = new VentaRequestDTO(UUID.randomUUID().toString(), operadorVenta.usuario(), cliente, null,
        ModalidadCobro.RESERVA, estancia, List.of(new VentaLineaRegistroDTO(producto, 4, new BigDecimal("3.50"))), null);
    var v = ventas.registrarConfirmada(consumo);
    assertThat(ventas.registrarConfirmada(consumo).idVenta()).isEqualTo(v.idVenta());
    assertThat(stock()).isEqualTo(16);
    var previa = cuentasReserva.cuenta(reserva.idReserva());
    assertThat(previa.totalCargos()).isEqualByComparingTo("94");
    assertThat(previa.saldoPendiente()).isEqualByComparingTo("74");
    var emision = new ReservaSalidaDTO.Comprobante("BOLETA", cliente);
    assertThatThrownBy(() -> reservas.registrarSalida(estancia,
        new ReservaSalidaDTO(operadorSalida.usuario(), null, UUID.randomUUID().toString(), cobro("60.00"), emision)))
        .hasMessageContaining("saldo vigente");
    assertThatThrownBy(() -> reservas.registrarSalida(estancia,
        new ReservaSalidaDTO(operadorSalida.usuario(), null, UUID.randomUUID().toString(), cobro("74.00"),
            new ReservaSalidaDTO.Comprobante("BOLETA", Long.MAX_VALUE)))).hasMessageContaining("receptor");
    assertThat(cuentasReserva.cuenta(reserva.idReserva()).saldoPendiente()).isEqualByComparingTo("74");
    var solicitud = new ReservaSalidaDTO(operadorSalida.usuario(), null, UUID.randomUUID().toString(), cobro("74.00"), emision);
    var inicio = new CountDownLatch(1);
    try (var ejecutor = Executors.newFixedThreadPool(2)) {
      var primero = ejecutor.submit(() -> { inicio.await(); return reservas.registrarSalida(estancia, solicitud); });
      var segundo = ejecutor.submit(() -> { inicio.await(); return reservas.registrarSalida(estancia, solicitud); });
      inicio.countDown();
      var salida = primero.get(10, TimeUnit.SECONDS);
      assertThat(segundo.get(10, TimeUnit.SECONDS)).isEqualTo(salida);
      assertThat(salida.estadoDetalle()).isEqualTo(EstadoReserva.FINALIZADA);
      assertThat(salida.saldoPendiente()).isZero();
      tx.executeWithoutResult(s -> {
        var c = em.find(Comprobante.class, salida.idComprobante());
        var p = em.find(Pago.class, salida.idPago());
        assertThat(c.getTurnoCaja().getIdTurno()).isEqualTo(operadorSalida.turno());
        assertThat(p.getUsuario().getIdUsuario()).isEqualTo(operadorSalida.usuario());
        assertThat(cantidad("Pago", "comprobante.idComprobante", c.getIdComprobante())).isEqualTo(2);
        assertThat(em.find(Reserva.class, reserva.idReserva()).getUsuario().getIdUsuario()).isEqualTo(usuario);
        assertThat(em.find(Venta.class, v.idVenta()).getUsuario().getIdUsuario()).isEqualTo(operadorVenta.usuario());
        assertThat(em.find(Habitacion.class, habitacion).getEstadoHabitacion()).isEqualTo(EstadoHabitacion.POR_LIMPIAR);
        var t = em.find(TurnoCaja.class, operadorSalida.turno());
        t.setEstadoSesion(0);
        t.setFechaCierre(ReglasPropuesta.ahora());
      });
      assertThat(reservas.registrarSalida(estancia, solicitud)).isEqualTo(salida);
      assertThatThrownBy(() -> reservas.registrarSalida(estancia, new ReservaSalidaDTO(usuario, null,
          solicitud.claveOperacion(), solicitud.cobro(), solicitud.comprobante()))).hasMessageContaining("clave de salida");
    }
  }

  private record Operador(Long usuario, Long turno) {}

  private Operador nuevoOperador() {
    return tx.execute(s -> {
      var clave = UUID.randomUUID().toString().substring(0, 8);
      var perfil = new Perfil(); perfil.setTipoDocumento(em.find(Perfil.class, cliente).getTipoDocumento());
      perfil.setNumeroDocumento(clave); perfil.setEsCliente(0); em.persist(perfil);
      var persona = new PersonaNatural(); persona.setPerfil(perfil); persona.setNombres("Operador");
      persona.setApellidos("Recepción"); persona.setFechaNacimiento(LocalDate.of(1990, 1, 1)); em.persist(persona);
      var empleado = new Empleado(); empleado.setPersonaNatural(persona);
      empleado.setFechaContratacion(LocalDate.now()); em.persist(empleado);
      var cuenta = new Usuario(); cuenta.setEmpleado(empleado); cuenta.setUsername("turno-" + clave);
      cuenta.setEstadoUsuario(EstadoUsuario.ACTIVO); em.persist(cuenta);
      var turno = new TurnoCaja(); turno.setUsuario(cuenta); turno.setEstadoSesion(1); em.persist(turno);
      prepararBoleta();
      return new Operador(cuenta.getIdUsuario(), turno.getIdTurno());
    });
  }

  private SerieComprobanteId prepararBoleta() {
    var tipos = em.createQuery("from TipoComprobante where codigo='BOLETA'", TipoComprobante.class).getResultList();
    if (!tipos.isEmpty()) return new SerieComprobanteId(tipos.getFirst().getIdTipoComprobante(), "B002");
    var tipo = new TipoComprobante(); tipo.setCodigo("BOLETA"); tipo.setNombreComprobante("Boleta"); em.persist(tipo);
    var id = new SerieComprobanteId(tipo.getIdTipoComprobante(), "B002");
    var serie = new SerieComprobante(); serie.setTipoComprobante(tipo); serie.setId(id); em.persist(serie);
    return id;
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
    prepararIngresoAhora(d);
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

  @Test void apiPermiteReservaSinCobroYExponeElNuevoContrato() throws Exception {
    var ahora = ReglasPropuesta.ahora();
    var sinCobroReq = new ReservaRegistroRequestDTO(UUID.randomUUID().toString(), cliente, usuario,
        List.of(new ReservaDetalleRegistroDTO(habitacion, ahora.plusDays(3), ahora.plusDays(4),
            new BigDecimal("80.00"), List.of(new ReservaOcupanteRegistroDTO(cliente, null, null, true)))),
        null);
    mvc.perform(post("/api/v1/reservas").contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(sinCobroReq))).andExpect(status().isCreated())
        .andExpect(jsonPath("$.detalles[0].estadoDetalle").value("CONFIRMADA"));
    var r = reserva(UUID.randomUUID().toString(), "80.00");
    reservas.registrar(r);
    mvc.perform(get("/api/v1/reservas").param("incluirCuenta", "true")).andExpect(status().isOk())
        .andExpect(jsonPath("$.contenido[0].cuenta.saldoAlojamiento").value(0))
        .andExpect(jsonPath("$.contenido[0].cuenta.totalPagado").value(80))
        .andExpect(jsonPath("$.contenido[0].detalles[0].ocupantes[0].nombre").isNotEmpty());
    mvc.perform(get("/api/v1/reservas")).andExpect(status().isOk())
        .andExpect(jsonPath("$.contenido[0].cuenta").doesNotExist());
    mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
        .andExpect(jsonPath("$.components.schemas.CobroInmediatoDTO.required").isArray())
        .andExpect(jsonPath("$.paths['/api/v1/reservas/detalles/{id}/salida'].post.requestBody.content['application/json'].schema['$ref']")
            .value("#/components/schemas/ReservaSalidaDTO"))
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
    assertThat(reservas.listar(0, 1, true).contenido().getFirst().cuenta().saldoAlojamiento())
        .isEqualByComparingTo("80");
    cuentasReserva.pagar(id, new ReservaPagoRegistroDTO(UUID.randomUUID().toString(), usuario, turno,
        forma, new BigDecimal("40.00"), null, null));
    ventas.registrarConfirmada(venta(UUID.randomUUID().toString(), estancia, cobro("7.00")));
    var cuenta = cuentasReserva.cuenta(id);
    assertThat(cuenta.totalCargos()).isEqualByComparingTo("87");
    assertThat(cuenta.totalPagado()).isEqualByComparingTo("47");
    assertThat(cuenta.saldoPendiente()).isEqualByComparingTo("40");
    var fila = reservas.listar(0, 1, true).contenido().getFirst();
    assertThat(fila.detalles()).isEqualTo(reservas.obtener(id).detalles());
    assertThat(fila.cuenta().totalCargos()).isEqualByComparingTo(cuenta.totalCargos());
    assertThat(fila.cuenta().totalPagado()).isEqualByComparingTo(cuenta.totalPagado());
    assertThat(fila.cuenta().saldoPendiente()).isEqualByComparingTo(cuenta.saldoPendiente());
    assertThat(fila.cuenta().saldoAlojamiento()).isEqualByComparingTo("40");
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

  private Long estanciaVencida(LocalDateTime salida) {
    var r = reservas.registrar(reserva(UUID.randomUUID().toString(), "80.00"));
    Long id = r.detalles().getFirst().idDetalleReserva();
    prepararIngresoAhora(id);
    reservas.cambiarEstado(id, "ingreso", new OperacionUsuarioDTO(usuario));
    tx.executeWithoutResult(s -> {
      var d = em.find(DetalleReserva.class, id);
      d.setFechaIngreso(salida.minusDays(1));
      d.setFechaIngresoReal(salida.minusDays(1));
      d.setFechaSalida(salida);
    });
    return id;
  }

  private void comprobarEstancia(Long id, EstadoReserva estado, EstadoHabitacion habitacionEsperada) {
    tx.executeWithoutResult(s -> {
      var d = em.find(DetalleReserva.class, id);
      assertThat(d.getEstadoDetalle()).isEqualTo(estado);
      assertThat(d.getHabitacion().getEstadoHabitacion()).isEqualTo(habitacionEsperada);
    });
  }

  @Test
  void unaSalidaVencidaSigueOcupadaHastaQueRecepcionLaConfirma() {
    Long id = estanciaVencida(ReglasPropuesta.ahora().minusMinutes(1));
    Long idReserva = tx.execute(s -> em.find(DetalleReserva.class, id).getReserva().getIdReserva());
    var pendiente = reservas.obtener(idReserva).detalles().getFirst();
    assertThat(pendiente.salidaPendiente()).isTrue();
    assertThat(pendiente.fechaSalidaReal()).isNull();
    comprobarEstancia(id, EstadoReserva.EN_CURSO, EstadoHabitacion.OCUPADO);

    var salida = reservas.cambiarEstado(id, "salida", new OperacionUsuarioDTO(usuario));
    assertThat(salida.salidaPendiente()).isFalse();
    assertThat(salida.fechaSalidaReal()).isAfter(pendiente.fechaSalida());
    comprobarEstancia(id, EstadoReserva.FINALIZADA, EstadoHabitacion.POR_LIMPIAR);
  }

  @Test void checkoutVencidoFinalizaAlLlegarLaHoraSinDuplicarAuditoriaNiPagos() {
    var hora = ReglasPropuesta.ahora().withNano(0);
    Long id = estanciaVencida(hora);
    assertThat(checkout.finalizar(id, hora.minusNanos(1))).isFalse();
    assertThat(checkout.finalizar(id, hora)).isTrue();
    comprobarEstancia(id, EstadoReserva.FINALIZADA, EstadoHabitacion.POR_LIMPIAR);
    assertThat(checkout.finalizar(id, hora.plusMinutes(1))).isFalse();
    tx.executeWithoutResult(s -> {
      var d = em.find(DetalleReserva.class, id);
      assertThat(d.getFechaSalidaReal()).isEqualTo(hora);
      var auditorias = em.createQuery("from AuditoriaGeneral a where a.nombreTabla='DETALLE_RESERVA'"
          + " and a.claveRegistro=:clave", AuditoriaGeneral.class)
          .setParameter("clave", "{\"id\":" + id + "}").getResultList();
      assertThat(auditorias).hasSize(1);
      assertThat(auditorias.getFirst().getUsuario()).isNull();
      assertThat(auditorias.getFirst().getDatosNuevos()).contains("CHECKOUT_AUTOMATICO");
      assertThat(cuentasReserva.cuenta(d.getReserva().getIdReserva()).cuentas().getFirst().pagos()).hasSize(1);
    });
    // El reintento manual tampoco cambia la hora de salida ni el estado de limpieza.
    assertThat(reservas.cambiarEstado(id, "salida", new OperacionUsuarioDTO(usuario)).fechaSalidaReal()).isEqualTo(hora);
  }

  @Test void checkoutRegistraHoraDeProcesamientoAlRecuperarUnaSalidaAntigua() {
    var ahora = ReglasPropuesta.ahora().withNano(0);
    Long id = estanciaVencida(ahora.minusDays(2));
    assertThat(checkout.finalizar(id, ahora)).isTrue();
    tx.executeWithoutResult(s -> assertThat(em.find(DetalleReserva.class, id).getFechaSalidaReal()).isEqualTo(ahora));
  }

  @Test void deudaDeAlojamientoImpideCheckoutYSeReintentaTrasLiquidarla() {
    var ahora = ReglasPropuesta.ahora().withNano(0);
    Long id = estanciaVencida(ahora);
    Long pago = tx.execute(s -> {
      var p = em.createQuery("from Pago p where p.comprobante.reserva.idReserva=:id", Pago.class)
          .setParameter("id", em.find(DetalleReserva.class, id).getReserva().getIdReserva()).getSingleResult();
      p.setVerificado(0);
      return p.getIdPago();
    });
    assertThat(checkout.finalizar(id, ahora)).isFalse();
    comprobarEstancia(id, EstadoReserva.EN_CURSO, EstadoHabitacion.OCUPADO);
    tx.executeWithoutResult(s -> em.find(Pago.class, pago).setVerificado(1));
    assertThat(checkout.finalizar(id, ahora.plusMinutes(1))).isTrue();
  }

  @Test void sobrepagoDeAlojamientoNoOcultaDeudaDeConsumos() {
    var ahora = ReglasPropuesta.ahora().withNano(0);
    Long id = estanciaVencida(ahora);
    var v = ventas.registrarConfirmada(venta(UUID.randomUUID().toString(), id, cobro("7.00")));
    Long reservaId = tx.execute(s -> {
      var d = em.find(DetalleReserva.class, id);
      var pago = em.createQuery("from Pago p where p.comprobante.reserva.idReserva=:id", Pago.class)
          .setParameter("id", d.getReserva().getIdReserva()).getSingleResult();
      pago.setMonto(new BigDecimal("81.00"));
      em.createQuery("from Pago p where p.comprobante.venta.idVenta=:id", Pago.class)
          .setParameter("id", v.idVenta()).getSingleResult().setMonto(new BigDecimal("6.00"));
      return d.getReserva().getIdReserva();
    });
    assertThat(cuentasReserva.cuenta(reservaId).saldoPendiente()).isEqualByComparingTo("0");
    assertThat(checkout.finalizar(id, ahora)).isFalse();
    assertThatThrownBy(() -> reservas.cambiarEstado(id, "salida", new OperacionUsuarioDTO(usuario)))
        .hasMessageContaining("Liquida las cuentas de venta").hasMessageContaining("V" + v.idVenta());
    comprobarEstancia(id, EstadoReserva.EN_CURSO, EstadoHabitacion.OCUPADO);
  }

  @Test void estanciaSinCuentaNoSeFinalizaSiDebeAlojamiento() {
    Long id = tx.execute(s -> {
      var r = new Reserva(); r.setCliente(em.find(Perfil.class, cliente)); r.setUsuario(em.find(Usuario.class, usuario));
      r.setClaveRegistro(UUID.randomUUID().toString()); r.setFirmaSolicitud("historica-checkout");
      var ahora = ReglasPropuesta.ahora();
      var d = new DetalleReserva(); d.setHabitacion(em.find(Habitacion.class, habitacion));
      d.getHabitacion().setEstadoHabitacion(EstadoHabitacion.OCUPADO);
      d.setFechaIngreso(ahora.minusDays(2)); d.setFechaIngresoReal(ahora.minusDays(2));
      d.setFechaSalida(ahora.minusDays(1)); d.setCostoEstadia(new BigDecimal("80"));
      d.setEstadoDetalle(EstadoReserva.EN_CURSO); r.agregarDetalle(d); em.persist(r); em.flush();
      return d.getIdDetalleReserva();
    });
    assertThat(checkout.finalizar(id, ReglasPropuesta.ahora())).isFalse();
    comprobarEstancia(id, EstadoReserva.EN_CURSO, EstadoHabitacion.OCUPADO);
  }

  @Test void dosCheckoutsAutomaticosSimultaneosSoloFinalizanUnaVez() throws Exception {
    var ahora = ReglasPropuesta.ahora().withNano(0);
    Long id = estanciaVencida(ahora);
    var inicio = new CountDownLatch(1);
    try (var ejecutor = Executors.newFixedThreadPool(2)) {
      Callable<Boolean> tarea = () -> { inicio.await(); return checkout.finalizar(id, ahora); };
      var a = ejecutor.submit(tarea); var b = ejecutor.submit(tarea); inicio.countDown();
      assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
    comprobarEstancia(id, EstadoReserva.FINALIZADA, EstadoHabitacion.POR_LIMPIAR);
  }

  @Test void salidaManualYAutomaticaSimultaneasSonIdempotentes() throws Exception {
    var ahora = ReglasPropuesta.ahora().withNano(0);
    Long id = estanciaVencida(ahora);
    var inicio = new CountDownLatch(1);
    try (var ejecutor = Executors.newFixedThreadPool(2)) {
      var a = ejecutor.submit(() -> { inicio.await(); return checkout.finalizar(id, ahora); });
      var b = ejecutor.submit(() -> { inicio.await(); return reservas.cambiarEstado(id, "salida", new OperacionUsuarioDTO(usuario)); });
      inicio.countDown(); a.get(20, TimeUnit.SECONDS);
      assertThat(b.get(20, TimeUnit.SECONDS).estadoDetalle()).isEqualTo(EstadoReserva.FINALIZADA);
    }
    comprobarEstancia(id, EstadoReserva.FINALIZADA, EstadoHabitacion.POR_LIMPIAR);
  }

  @Test void falloDeConsistenciaNoModificaHabitacionNiEstancia() {
    var ahora = ReglasPropuesta.ahora().withNano(0);
    Long id = estanciaVencida(ahora);
    tx.executeWithoutResult(s -> em.find(DetalleReserva.class, id).setFechaIngresoReal(ahora.plusHours(1)));
    assertThatThrownBy(() -> checkout.finalizar(id, ahora)).hasMessageContaining("anterior al ingreso real");
    comprobarEstancia(id, EstadoReserva.EN_CURSO, EstadoHabitacion.OCUPADO);
    tx.executeWithoutResult(s -> assertThat(em.find(DetalleReserva.class, id).getFechaSalidaReal()).isNull());
  }

  @Test void noPisaHabitacionesEnMantenimientoAunqueLaEstanciaSeaInconsistente() {
    var ahora = ReglasPropuesta.ahora().withNano(0);
    Long id = estanciaVencida(ahora);
    tx.executeWithoutResult(s -> em.find(Habitacion.class, habitacion).setEstadoHabitacion(EstadoHabitacion.MANTENIMIENTO));
    assertThatThrownBy(() -> checkout.finalizar(id, ahora)).hasMessageContaining("debe estar ocupada");
    comprobarEstancia(id, EstadoReserva.EN_CURSO, EstadoHabitacion.MANTENIMIENTO);
  }

  @Test void candidataDesaparecidaOConfirmadaNoSeFinalizaYConsultarNoMutaEstados() {
    assertThat(checkout.finalizar(Long.MAX_VALUE, ReglasPropuesta.ahora())).isFalse();
    var r = reservas.registrar(reserva(UUID.randomUUID().toString(), "80.00"));
    Long id = r.detalles().getFirst().idDetalleReserva();
    assertThat(checkout.finalizar(id, ReglasPropuesta.ahora().plusDays(2))).isFalse();
    reservas.obtener(r.idReserva()); reservas.listar(0, 100);
    comprobarEstancia(id, EstadoReserva.CONFIRMADA, EstadoHabitacion.DISPONIBLE);
  }

  @Test void consultaDeCandidatasRespetaLaHoraYElCursor() {
    var ahora = ReglasPropuesta.ahora().withNano(0);
    Long id = estanciaVencida(ahora);
    var limite = org.springframework.data.domain.PageRequest.of(0, 100);
    assertThat(detalles.vencidas(ahora.minusSeconds(1), id - 1, limite)).doesNotContain(id);
    assertThat(detalles.vencidas(ahora, id - 1, limite)).contains(id);
    assertThat(detalles.vencidas(ahora, id, limite)).doesNotContain(id);
    checkout.finalizar(id, ahora);
    assertThat(detalles.vencidas(ahora, id - 1, limite)).doesNotContain(id);
  }

  @Test void reservaConCheckoutPasadoSeRechazaSinInsertarNiCobrar() {
    var ahora = ReglasPropuesta.ahora();
    var solicitud = new ReservaRegistroRequestDTO(UUID.randomUUID().toString(), cliente, usuario,
        List.of(new ReservaDetalleRegistroDTO(habitacion, ahora.minusDays(2), ahora.minusDays(1),
            new BigDecimal("80.00"), List.of())), cobro("80.00"));
    assertThatThrownBy(() -> reservas.registrar(solicitud)).hasMessageContaining("debe estar en el futuro");
    tx.executeWithoutResult(s -> assertThat(cantidad("Reserva", "claveRegistro", solicitud.claveRegistro())).isZero());
  }

  @Test void reservaConIngresoPasadoYCheckoutFuturoSeRechazaSinInsertarNiCobrar() {
    var ahora = ReglasPropuesta.ahora();
    var solicitud = new ReservaRegistroRequestDTO(UUID.randomUUID().toString(), cliente, usuario,
        List.of(new ReservaDetalleRegistroDTO(habitacion, ahora.minusDays(1), ahora.plusDays(1),
            new BigDecimal("160.00"), List.of())), cobro("160.00"));
    assertThatThrownBy(() -> reservas.registrar(solicitud))
        .hasMessageContaining("fecha de ingreso").hasMessageContaining("pasado");
    tx.executeWithoutResult(s -> assertThat(cantidad("Reserva", "claveRegistro", solicitud.claveRegistro())).isZero());
  }

  @Test void noCreaConsumosConUnaEstanciaEnCacheQueElCheckoutYaFinalizo() {
    var ahora = ReglasPropuesta.ahora().withNano(0);
    Long id = estanciaVencida(ahora);
    var solicitud = venta(UUID.randomUUID().toString(), id, null);
    assertThatThrownBy(() -> tx.execute(s -> {
      assertThat(em.find(DetalleReserva.class, id).getEstadoDetalle()).isEqualTo(EstadoReserva.EN_CURSO);
      assertThat(checkout.finalizar(id, ahora)).isTrue();
      return ventas.registrar(solicitud);
    })).hasMessageContaining("requiere una estancia en curso");
    sinVenta(solicitud.claveRegistro());
    comprobarEstancia(id, EstadoReserva.FINALIZADA, EstadoHabitacion.POR_LIMPIAR);
  }

  @Test void rechazaFechasInvertidasHabitacionesDuplicadasYOcupantesRepetidos() {
    var original = reserva(UUID.randomUUID().toString(), "80.00");
    var d = original.detalles().getFirst();
    var invertida = new ReservaDetalleRegistroDTO(habitacion, d.fechaSalida(), d.fechaIngreso(), d.costoEstadia(), d.ocupantes());
    var repetidos = new ReservaDetalleRegistroDTO(habitacion, d.fechaIngreso(), d.fechaSalida(), d.costoEstadia(),
        List.of(d.ocupantes().getFirst(), d.ocupantes().getFirst()));
    for (var caso : List.of(List.of(invertida), List.of(d, d), List.of(repetidos))) {
      var solicitud = new ReservaRegistroRequestDTO(UUID.randomUUID().toString(), cliente, usuario, caso, cobro("80.00"));
      assertThatThrownBy(() -> reservas.registrar(solicitud)).isInstanceOf(pe.com.rebolsysBackend.exception.ReglaNegocioException.class);
      tx.executeWithoutResult(s -> assertThat(cantidad("Reserva", "claveRegistro", solicitud.claveRegistro())).isZero());
    }
  }

  @Test void checkoutDejaHabitacionPorLimpiarYPermiteUnaReservaFutura() {
    var ahora = ReglasPropuesta.ahora().withNano(0);
    Long id = estanciaVencida(ahora);
    assertThat(checkout.finalizar(id, ahora)).isTrue();
    var solicitud = reserva(UUID.randomUUID().toString(), "80.00");
    assertThat(reservas.registrar(solicitud).detalles().getFirst().estadoDetalle())
        .isEqualTo(EstadoReserva.CONFIRMADA);
    comprobarEstancia(id, EstadoReserva.FINALIZADA, EstadoHabitacion.POR_LIMPIAR);
  }

  @Test void estanciaEnCursoBloqueaElCruceYAdmiteUnaReservaAlTerminar() {
    var salida = ReglasPropuesta.ahora().plusHours(3).withNano(0);
    estanciaVencida(salida);
    var cruce = reserva(UUID.randomUUID().toString(), "80.00");
    assertThatThrownBy(() -> reservas.registrar(cruce)).hasMessageContaining("ya tiene una reserva");
    var posterior = new ReservaRegistroRequestDTO(UUID.randomUUID().toString(), cliente, usuario,
        List.of(new ReservaDetalleRegistroDTO(habitacion, salida, salida.plusDays(1),
            new BigDecimal("80.00"), List.of())), cobro("80.00"));
    assertThat(reservas.registrar(posterior).detalles().getFirst().estadoDetalle())
        .isEqualTo(EstadoReserva.CONFIRMADA);
  }

  @Test void completaHuespedesEnUnaEstanciaConfirmadaYPermiteElIngreso() {
    var original = reserva(UUID.randomUUID().toString(), "80.00");
    var detalle = original.detalles().getFirst();
    var solicitud = new ReservaRegistroRequestDTO(original.claveRegistro(), cliente, usuario,
        List.of(new ReservaDetalleRegistroDTO(habitacion, detalle.fechaIngreso(), detalle.fechaSalida(),
            detalle.costoEstadia(), List.of())), original.cobro());
    Long id = reservas.registrar(solicitud).detalles().getFirst().idDetalleReserva();
    prepararIngresoAhora(id);
    var actor = new OperacionUsuarioDTO(usuario);
    assertThatThrownBy(() -> reservas.cambiarEstado(id, "ingreso", actor))
        .hasMessageContaining("Completa los ocupantes");
    var ocupantes = new ReservaOcupantesGuardarDTO(usuario, detalle.ocupantes());
    assertThat(reservas.guardarOcupantes(id, ocupantes).ocupantes()).hasSize(1);
    assertThat(reservas.cambiarEstado(id, "ingreso", actor).estadoDetalle()).isEqualTo(EstadoReserva.EN_CURSO);
    assertThatThrownBy(() -> reservas.guardarOcupantes(id, ocupantes)).hasMessageContaining("antes del ingreso");
  }

  @Test void reservaConfirmadaSuperpuestaNoPermiteOtraReservaNiPagosHuerfanos() {
    reservas.registrar(reserva(UUID.randomUUID().toString(), "80.00"));
    var segunda = reserva(UUID.randomUUID().toString(), "80.00");
    assertThatThrownBy(() -> reservas.registrar(segunda)).hasMessageContaining("ya tiene una reserva");
    tx.executeWithoutResult(s -> assertThat(cantidad("Reserva", "claveRegistro", segunda.claveRegistro())).isZero());
  }

  @Test void menoresExigenAdultoEnLaMismaHabitacionYCamaDesdeLosSieteAnios() {
    Long menor = tx.execute(s -> {
      var p = new Perfil(); p.setTipoDocumento(em.find(Perfil.class, cliente).getTipoDocumento());
      p.setNumeroDocumento(UUID.randomUUID().toString().substring(0, 8)); p.setEsCliente(1); em.persist(p);
      var n = new PersonaNatural(); n.setPerfil(p); n.setNombres("Menor"); n.setApellidos("Prueba");
      n.setFechaNacimiento(ReglasPropuesta.ahora().toLocalDate().minusYears(6)); em.persist(n);
      var h = em.find(Habitacion.class, habitacion); h.setCapacidadPersonas(2); h.setPlazasCama(1); h.setMaxMenoresSinCama(1);
      return p.getIdPerfil();
    });
    var original = reserva(UUID.randomUUID().toString(), "80.00");
    var d = original.detalles().getFirst();
    for (var ocupantes : List.of(
        List.of(new ReservaOcupanteRegistroDTO(menor, null, null, false)),
        List.of(new ReservaOcupanteRegistroDTO(menor, cliente, ParentescoResponsable.MADRE, false)),
        List.of(new ReservaOcupanteRegistroDTO(cliente, null, null, true), new ReservaOcupanteRegistroDTO(menor, null, null, false)),
        List.of(new ReservaOcupanteRegistroDTO(cliente, null, null, false)))) {
      var solicitud = new ReservaRegistroRequestDTO(UUID.randomUUID().toString(), cliente, usuario,
          List.of(new ReservaDetalleRegistroDTO(habitacion, d.fechaIngreso(), d.fechaSalida(), d.costoEstadia(), ocupantes)), cobro("80.00"));
      assertThatThrownBy(() -> reservas.registrar(solicitud)).isInstanceOf(pe.com.rebolsysBackend.exception.ReglaNegocioException.class);
    }
    var familia = List.of(new ReservaOcupanteRegistroDTO(cliente, null, null, true),
        new ReservaOcupanteRegistroDTO(menor, cliente, ParentescoResponsable.MADRE, false));
    var solicitud = new ReservaRegistroRequestDTO(UUID.randomUUID().toString(), cliente, usuario,
        List.of(new ReservaDetalleRegistroDTO(habitacion, d.fechaIngreso(), d.fechaSalida(), d.costoEstadia(), familia)), cobro("80.00"));
    tx.executeWithoutResult(s -> em.find(PersonaNatural.class, menor).setFechaNacimiento(d.fechaIngreso().toLocalDate().minusYears(7)));
    assertThatThrownBy(() -> reservas.registrar(solicitud)).hasMessageContaining("menor de siete años");
    tx.executeWithoutResult(s -> em.find(PersonaNatural.class, menor).setFechaNacimiento(d.fechaIngreso().toLocalDate().minusYears(6)));
    assertThat(reservas.registrar(solicitud).detalles().getFirst().ocupantes()).hasSize(2);
  }

  @Test void titularMenorDeEdadSeRechazaAunqueNoSeCarguenOcupantes() {
    tx.executeWithoutResult(s -> em.find(PersonaNatural.class, cliente).setFechaNacimiento(ReglasPropuesta.ahora().toLocalDate().minusYears(17)));
    var original = reserva(UUID.randomUUID().toString(), "80.00");
    var d = original.detalles().getFirst();
    var solicitud = new ReservaRegistroRequestDTO(original.claveRegistro(), cliente, usuario,
        List.of(new ReservaDetalleRegistroDTO(habitacion, d.fechaIngreso(), d.fechaSalida(), d.costoEstadia(), List.of())), cobro("80.00"));
    assertThatThrownBy(() -> reservas.registrar(solicitud)).hasMessageContaining("mayor de edad");
  }

  @Test void identidadInvalidaSeRechazaTambienAlInvocarDirectamenteElServicio() {
    var datos = new ClienteRegistroRequestDTO(0L, "x", false, "", "",
        LocalDate.now().plusYears(1), null, null, "correo-invalido", null);
    assertThatThrownBy(() -> clientes.guardarPersona(datos)).isInstanceOf(jakarta.validation.ConstraintViolationException.class);
    assertThatThrownBy(() -> clientes.actualizarPersona(cliente, datos)).isInstanceOf(jakarta.validation.ConstraintViolationException.class);
    tx.executeWithoutResult(s -> assertThat(em.find(PersonaNatural.class, cliente).getNombres()).isEqualTo("Lucía"));
  }
}
