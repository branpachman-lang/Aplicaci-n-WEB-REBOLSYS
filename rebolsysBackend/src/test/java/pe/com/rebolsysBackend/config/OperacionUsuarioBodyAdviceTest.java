package pe.com.rebolsysBackend.config;

import pe.com.rebolsysBackend.dto.accesos.SesionActualDTO;
import pe.com.rebolsysBackend.dto.caja.cobros.PagoCuentaDTO;
import pe.com.rebolsysBackend.dto.mantenimiento.ProductoRequestDTO;
import pe.com.rebolsysBackend.dto.ventas.VentaConfirmacionDTO;
import pe.com.rebolsysBackend.dto.ventas.VentaRequestDTO;
import pe.com.rebolsysBackend.security.OperacionUsuarioBodyAdvice;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import jakarta.validation.Valid;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import pe.com.rebolsysBackend.exception.GlobalExceptionHandler;
import pe.com.rebolsysBackend.security.SesionOperacionService;
import tools.jackson.databind.json.JsonMapper;

class OperacionUsuarioBodyAdviceTest {
  SesionOperacionService sesion;MockMvc mvc;
  @RestController static class Operaciones {
    @PostMapping("/api/v1/ventas/confirmadas") public VentaRequestDTO venta(@Valid @RequestBody VentaRequestDTO r){return r;}
    @PostMapping("/api/v1/ventas/1/pagos") public PagoCuentaDTO pago(@Valid @RequestBody PagoCuentaDTO r){return r;}
    @PostMapping("/api/v1/productos") public ProductoRequestDTO producto(@Valid @RequestBody ProductoRequestDTO r){return r;}
    @PostMapping("/api/v1/ventas/1/confirmacion") public VentaConfirmacionDTO confirmar(@Valid @RequestBody VentaConfirmacionDTO r){return r;}
  }
  @BeforeEach void preparar(){
    sesion=mock(SesionOperacionService.class);when(sesion.actual()).thenReturn(new SesionActualDTO(130L,"recepcion.flujo","ACTIVA",101L,1L));
    mvc=MockMvcBuilders.standaloneSetup(new Operaciones()).setControllerAdvice(new OperacionUsuarioBodyAdvice(sesion,JsonMapper.builder().build()),new GlobalExceptionHandler()).build();
  }
  @Test void completarUsuarioAntesDeValidarUnaVenta()throws Exception{
    mvc.perform(post("/api/v1/ventas/confirmadas").contentType(MediaType.APPLICATION_JSON).content("{\"claveRegistro\":\"sesion-venta\",\"modalidadCobro\":\"SEPARADA\",\"detalles\":[{\"idProducto\":1,\"cantidad\":2}]}"))
      .andExpect(status().isOk()).andExpect(jsonPath("$.idUsuario").value(130)).andExpect(jsonPath("$.modalidadCobro").value("SEPARADA"));
  }
  @Test void confirmacionConCobroUsaActorAutenticadoYValidaDatosAnidados() throws Exception {
    mvc.perform(post("/api/v1/ventas/1/confirmacion").contentType(MediaType.APPLICATION_JSON)
        .content("{\"cobro\":{\"idFormaPago\":1,\"monto\":7}}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.idUsuario").value(130));
    mvc.perform(post("/api/v1/ventas/1/confirmacion").contentType(MediaType.APPLICATION_JSON)
        .content("{\"idUsuario\":999,\"cobro\":{\"idFormaPago\":1,\"monto\":7}}"))
        .andExpect(status().isConflict());
    mvc.perform(post("/api/v1/ventas/1/confirmacion").contentType(MediaType.APPLICATION_JSON)
        .content("{\"cobro\":{\"idFormaPago\":1,\"monto\":-1}}"))
        .andExpect(status().isBadRequest());
    mvc.perform(post("/api/v1/ventas/1/confirmacion").contentType(MediaType.APPLICATION_JSON)
        .content("{}"))
        .andExpect(status().isBadRequest());
  }
  @Test void rechazarOtroUsuario()throws Exception{
    mvc.perform(post("/api/v1/ventas/confirmadas").contentType(MediaType.APPLICATION_JSON).content("{\"idUsuario\":999,\"claveRegistro\":\"sesion-venta\",\"modalidadCobro\":\"SEPARADA\",\"detalles\":[{\"idProducto\":1,\"cantidad\":2}]}"))
      .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("La operación debe corresponder al usuario de la sesión actual"));
  }
  @Test void completarUsuarioYTurnoDeUnPago()throws Exception{
    mvc.perform(post("/api/v1/ventas/1/pagos").contentType(MediaType.APPLICATION_JSON).content("{\"claveOperacion\":\"sesion-pago\",\"idFormaPago\":1,\"monto\":5}"))
      .andExpect(status().isOk()).andExpect(jsonPath("$.idUsuario").value(130)).andExpect(jsonPath("$.idTurno").value(101));
  }
  @Test void completarResponsableDeProductoSinPerderStockPredeterminado()throws Exception{
    mvc.perform(post("/api/v1/productos").contentType(MediaType.APPLICATION_JSON).content("{\"nombreProducto\":\"Agua sin gas\",\"precioActual\":2.50,\"idCategoria\":2,\"estadoProducto\":\"ACTIVO\"}"))
      .andExpect(status().isOk()).andExpect(jsonPath("$.idUsuario").value(130))
      .andExpect(jsonPath("$.stock").value(0)).andExpect(jsonPath("$.stockMinimo").value(0));
  }
  @Test void noCobrarSinTurnoAbierto()throws Exception{
    when(sesion.actual()).thenReturn(new SesionActualDTO(130L,"recepcion.flujo","ACTIVA",null,1L));
    mvc.perform(post("/api/v1/ventas/1/pagos").contentType(MediaType.APPLICATION_JSON).content("{\"claveOperacion\":\"sesion-pago\",\"idFormaPago\":1,\"monto\":5}"))
      .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("El usuario actual necesita un único turno de caja abierto"));
  }
}
