package pe.com.rebolsysBackend.controller.comun;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pe.com.rebolsysBackend.controller.caja.CajaController;
import pe.com.rebolsysBackend.controller.caja.GastoController;
import pe.com.rebolsysBackend.controller.mantenimiento.HabitacionController;
import pe.com.rebolsysBackend.controller.mantenimiento.ProductoController;
import pe.com.rebolsysBackend.controller.reservas.ClienteController;
import pe.com.rebolsysBackend.controller.reservas.ReservaController;
import pe.com.rebolsysBackend.controller.ventas.VentaController;
import pe.com.rebolsysBackend.service.service.caja.GastoService;
import pe.com.rebolsysBackend.service.service.mantenimiento.ClienteService;
import pe.com.rebolsysBackend.service.service.mantenimiento.HabitacionService;
import pe.com.rebolsysBackend.service.service.mantenimiento.ProductoService;
import pe.com.rebolsysBackend.service.service.reservas.ReservaService;
import pe.com.rebolsysBackend.service.service.ventas.VentaService;
import pe.com.rebolsysBackend.exception.GlobalExceptionHandler;
import pe.com.rebolsysBackend.service.impl.caja.CajaService;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Controladores reales con servicios simulados: una entrada inválida no debe llegar al negocio. */
class ValidacionesEntradaTest {
  private MockMvc mvc;
  private final ReservaService reservas = mock(ReservaService.class);
  private final VentaService ventas = mock(VentaService.class);
  private final ProductoService productos = mock(ProductoService.class);
  private final HabitacionService habitaciones = mock(HabitacionService.class);
  private final GastoService gastos = mock(GastoService.class);
  private final ClienteService clientes = mock(ClienteService.class);
  private final CajaService caja = mock(CajaService.class);

  @BeforeEach void preparar() {
    mvc = MockMvcBuilders.standaloneSetup(new ReservaController(reservas), new VentaController(ventas),
        new ProductoController(productos), new HabitacionController(habitaciones),
        new GastoController(gastos), new ClienteController(clientes), new CajaController(caja))
        .setControllerAdvice(new GlobalExceptionHandler()).build();
  }

  @ParameterizedTest(name = "{0}: {2}")
  @MethodSource("cuerposInvalidos")
  void rechazaLimitesYNulosAntesDeInvocarLosServicios(String ruta, String cuerpo, String caso) throws Exception {
    mvc.perform(post("/api/v1/" + ruta).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
        .andExpect(status().isBadRequest());
    sinLlamadas();
  }

  private void sinLlamadas() {
    verifyNoInteractions(reservas, ventas, productos, habitaciones, gastos, clientes, caja);
  }

  static Stream<Arguments> cuerposInvalidos() {
    var casos = new ArrayList<Arguments>();
    agregar(casos, "reservas", """
        {"claveRegistro":"revision","idCliente":1,"idUsuario":1,
         "detalles":[{"idHabitacion":1,"fechaIngreso":"2030-01-01T14:00:00",
         "fechaSalida":"2030-01-02T12:00:00","costoEstadia":80,"ocupantes":[]}],
         "cobro":{"idFormaPago":1,"monto":80}}
        """, new String[][] {
          {"/claveRegistro", "\"\""}, {"/claveRegistro", cadena(65)},
          {"/idCliente", "0"}, {"/idUsuario", "null"}, {"/detalles", "null"},
          {"/detalles", "[]"}, {"/detalles", "[null]"},
          {"/detalles/0/idHabitacion", "-1"}, {"/detalles/0/fechaIngreso", "null"},
          {"/detalles/0/fechaSalida", "null"}, {"/detalles/0/costoEstadia", "-1"},
          {"/detalles/0/costoEstadia", "0.001"}, {"/detalles/0/costoEstadia", "10000000000"},
          {"/detalles/0/ocupantes", "[null]"},
          {"/detalles/0/ocupantes", "[{\"idCliente\":0,\"ocupaPlazaCama\":true}]"},
          {"/detalles/0/ocupantes", "[{\"idCliente\":1,\"ocupaPlazaCama\":null}]"},
          {"/cobro", "null"}, {"/cobro/idFormaPago", "0"}, {"/cobro/monto", "-1"},
          {"/cobro/monto", "0.001"}, {"/cobro/numeroOperacion", cadena(101)},
        });
    agregar(casos, "ventas/confirmadas", """
        {"claveRegistro":"revision","idUsuario":1,"modalidadCobro":"SEPARADA",
         "detalles":[{"idProducto":1,"cantidad":1,"precioEsperado":3.50}],
         "cobro":{"idFormaPago":1,"monto":3.50}}
        """, new String[][] {
          {"/modalidadCobro", "null"}, {"/modalidadCobro", "\"OTRA\""},
          {"/idUsuario", "0"}, {"/idClienteComprador", "-1"},
          {"/compradorNombreLibre", cadena(251)}, {"/detalles", "[]"},
          {"/detalles", "[null]"}, {"/detalles/0/idProducto", "0"},
          {"/detalles/0/cantidad", "0"}, {"/detalles/0/cantidad", "null"},
          {"/detalles/0/cantidad", "1000000000"}, {"/detalles/0/precioEsperado", "-1"},
          {"/detalles/0/precioEsperado", "0.001"}, {"/cobro/monto", "null"},
        });
    agregar(casos, "productos", """
        {"nombreProducto":"Agua","precioActual":2.50,"stock":0,"stockMinimo":0,
         "idCategoria":1,"estadoProducto":"ACTIVO"}
        """, new String[][] {
          {"/nombreProducto", "\" \""}, {"/nombreProducto", cadena(101)},
          {"/precioActual", "-1"}, {"/precioActual", "0.001"}, {"/precioActual", "100000000"},
          {"/stock", "null"}, {"/stock", "-1"}, {"/stock", "1000000000"},
          {"/stockMinimo", "null"}, {"/idCategoria", "0"}, {"/descripcion", cadena(501)},
          {"/imagenRuta", "\"https://externo.test/imagen.png\""}, {"/idUsuario", "0"},
        });
    agregar(casos, "gastos", """
        {"idUsuario":1,"idTurno":1,"idTipoMovimiento":1,"concepto":"Compra","monto":10}
        """, new String[][] {
          {"/idTurno", "0"}, {"/idUsuario", "null"}, {"/idTipoMovimiento", "-1"},
          {"/concepto", "\" \""}, {"/concepto", cadena(1001)}, {"/monto", "0"},
          {"/monto", "-1"}, {"/monto", "0.001"}, {"/documentoRespaldo", cadena(101)},
          {"/evidenciaSha256", "\"hash-invalido\""}, {"/claveRegistro", "\"clave con espacios\""},
        });
    agregar(casos, "habitaciones", """
        {"numeroCuarto":"201","idTipoHabitacion":1,"estadoHabitacion":"DISPONIBLE",
         "capacidadPersonas":2,"plazasCama":2,"maxMenoresSinCama":0,"precioPorNoche":80}
        """, new String[][] {
          {"/numeroCuarto", "\"\""}, {"/numeroCuarto", cadena(11)},
          {"/idTipoHabitacion", "0"}, {"/estadoHabitacion", "\"OTRO\""},
          {"/capacidadPersonas", "0"}, {"/capacidadPersonas", "100"},
          {"/plazasCama", "null"}, {"/maxMenoresSinCama", "2"}, {"/precioPorNoche", "-1"},
        });
    agregar(casos, "caja/turnos/1/cierre", "{\"revision\":" + cadena(64)
        + ",\"arqueos\":[{\"idFormaPago\":1,\"montoDeclarado\":0}]}", new String[][] {
          {"/revision", "\"\""}, {"/arqueos", "null"}, {"/arqueos", "[]"},
          {"/arqueos", "[null]"}, {"/arqueos/0/idFormaPago", "0"},
          {"/arqueos/0/montoDeclarado", "-1"}, {"/arqueos/0/montoDeclarado", "0.001"},
        });
    return casos.stream();
  }

  private static String cadena(int longitud) { return "\"" + "a".repeat(longitud) + "\""; }

  private static void agregar(List<Arguments> casos, String ruta, String base, String[][] cambios) {
    var mapper = JsonMapper.builder().build();
    for (var cambio : cambios) {
      var cuerpo = mapper.readTree(base);
      int barra = cambio[0].lastIndexOf('/');
      var padre = barra == 0 ? cuerpo : cuerpo.at(cambio[0].substring(0, barra));
      ((ObjectNode) padre).set(cambio[0].substring(barra + 1), mapper.readTree(cambio[1]));
      casos.add(Arguments.of(ruta, mapper.writeValueAsString(cuerpo), cambio[0] + "=" + cambio[1]));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "reservas?pagina=-1", "reservas?tamanio=0", "reservas?tamanio=101", "reservas?pagina=abc",
      "ventas?pagina=-1", "ventas?tamanio=101", "productos?pagina=-1", "productos?idCategoria=0",
      "gastos", "gastos?idTurno=-1", "habitaciones", "habitaciones?desde=no-es-fecha&hasta=2030-01-02",
      "recepcion/clientes", "recepcion/clientes?tipoDocumento=1",
      "recepcion/clientes?tipoDocumento=0&numeroDocumento=12345678",
      "recepcion/clientes?tipoDocumento=1&numeroDocumento=",
      "caja/turnos/1/movimientos?tamanio=101", "caja/turnos/0/movimientos",
  })
  void rechazaParametrosAusentesInvalidosYFueraDeRango(String ruta) throws Exception {
    mvc.perform(get("/api/v1/" + ruta)).andExpect(status().isBadRequest());
    sinLlamadas();
  }

  @org.junit.jupiter.api.Test
  void rechazaDocumentoConSoloEspacios() throws Exception {
    mvc.perform(get("/api/v1/recepcion/clientes").param("tipoDocumento", "1").param("numeroDocumento", "   "))
        .andExpect(status().isBadRequest());
    sinLlamadas();
  }
}
