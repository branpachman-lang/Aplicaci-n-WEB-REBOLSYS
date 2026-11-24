package pe.com.rebolsysBackend.service.impl.comun.comprobantes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import pe.com.rebolsysBackend.config.FacturacionApisPeruProperties;
import pe.com.rebolsysBackend.dto.comun.OperacionUsuarioDTO;
import pe.com.rebolsysBackend.dto.comun.comprobantes.ResultadoProveedorCpeDTO;
import pe.com.rebolsysBackend.entity.Comprobante;
import pe.com.rebolsysBackend.entity.EmisionElectronica;
import pe.com.rebolsysBackend.entity.Usuario;
import pe.com.rebolsysBackend.enums.EstadoEmisionElectronica;
import pe.com.rebolsysBackend.enums.EstadoUsuario;
import pe.com.rebolsysBackend.exception.FacturacionProveedorException;
import pe.com.rebolsysBackend.exception.RecursoNoEncontradoException;
import pe.com.rebolsysBackend.exception.ReglaNegocioException;
import pe.com.rebolsysBackend.exception.ServicioExternoException;
import pe.com.rebolsysBackend.repository.caja.cobros.PagoRepository;
import pe.com.rebolsysBackend.repository.comun.comprobantes.ComprobanteDetalleRepository;
import pe.com.rebolsysBackend.repository.comun.comprobantes.ComprobanteRepository;
import pe.com.rebolsysBackend.repository.comun.comprobantes.EmisionElectronicaRepository;
import pe.com.rebolsysBackend.service.impl.comun.ReglasPropuesta;
import pe.com.rebolsysBackend.service.impl.configuracion.HospedajeService;
import pe.com.rebolsysBackend.service.service.comun.comprobantes.ProveedorFacturacionClient;

/** Estados de envío y consulta del CPE con un proveedor simulado: no se emite ningún comprobante real. */
@ExtendWith(MockitoExtension.class)
class EmisionElectronicaServiceImplTest {
  private static final long COMPROBANTE = 21L;
  private static final OperacionUsuarioDTO OPERACION = new OperacionUsuarioDTO(5L);

  @Mock EmisionElectronicaRepository emisiones;
  @Mock ComprobanteRepository comprobantes;
  @Mock ComprobanteDetalleRepository lineas;
  @Mock PagoRepository pagos;
  @Mock ReglasPropuesta reglas;
  @Mock DocumentoFiscalBuilder builder;
  @Mock ProveedorFacturacionClient proveedor;
  @Mock PlatformTransactionManager transacciones;
  @Mock EntityManager em;
  @Mock HospedajeService hospedaje;

  FacturacionApisPeruProperties config;
  EmisionElectronicaServiceImpl servicio;
  EmisionElectronica emision;

  @BeforeEach
  void preparar() {
    config = new FacturacionApisPeruProperties();
    config.setHabilitada(true);
    config.setToken("token-de-prueba");
    config.setIdEmpresa(1L);
    config.setRuc("20999999999");
    config.setRazonSocial("Hospedaje de prueba");
    config.setDireccion("Jr. Prueba 123");
    config.setUbigeo("020101");
    config.setDepartamento("Ancash");
    config.setProvincia("Huaraz");
    config.setDistrito("Huaraz");
    servicio = new EmisionElectronicaServiceImpl(emisiones, comprobantes, lineas, pagos, reglas, builder,
        proveedor, config, transacciones, em, hospedaje, new tools.jackson.databind.ObjectMapper());
    emision = emision(EstadoEmisionElectronica.PREPARADO, 0);
    lenient().when(emisiones.findByComprobanteIdComprobante(COMPROBANTE)).thenAnswer(i -> Optional.of(emision));
    lenient().when(emisiones.bloquearComprobante(COMPROBANTE)).thenAnswer(i -> Optional.of(emision));
    lenient().when(emisiones.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
  }

  @Test
  void informaLaConfiguracionYExigeQueEsteCompleta() {
    assertThat(servicio.configuracion()).containsEntry("habilitada", true).containsEntry("ambiente", "BETA");
    config.setHabilitada(false);
    assertThatThrownBy(() -> servicio.enviar(COMPROBANTE, OPERACION)).isInstanceOf(ServicioExternoException.class);
    verify(proveedor, never()).enviar(anyString(), anyString(), anyString(), anyString(), anyLong());
  }

  @Test
  void unEnvioAceptadoGuardaXmlCdrYRespuesta() {
    when(proveedor.enviar("{}", "20999999999", "03", "B001", 15L))
        .thenReturn(new ResultadoProveedorCpeDTO("<xml/>", "hash", "UEsDBA==", "0", "Aceptada", true));

    var resultado = servicio.enviar(COMPROBANTE, OPERACION);

    assertThat(resultado.estado()).isEqualTo(EstadoEmisionElectronica.ACEPTADO);
    assertThat(resultado.intentos()).isEqualTo(1);
    assertThat(resultado.codigoRespuesta()).isEqualTo("0");
    assertThat(emision.getHashCpe()).isEqualTo("hash");
    assertThat(emision.getXmlFirmado()).isEqualTo("<xml/>");
  }

  @Test
  void unRechazoDelProveedorQuedaRegistradoComoDefinitivo() {
    when(proveedor.enviar(anyString(), anyString(), anyString(), anyString(), anyLong()))
        .thenReturn(new ResultadoProveedorCpeDTO(null, null, null, "2800", "Rechazada", false));

    assertThat(servicio.enviar(COMPROBANTE, OPERACION).estado()).isEqualTo(EstadoEmisionElectronica.RECHAZADO);
    // Un documento definitivo no se vuelve a enviar.
    assertThat(servicio.enviar(COMPROBANTE, OPERACION).estado()).isEqualTo(EstadoEmisionElectronica.RECHAZADO);
    verify(proveedor).enviar(anyString(), anyString(), anyString(), anyString(), anyLong());
  }

  @Test
  void unaRespuestaSinVeredictoQuedaIncierta() {
    when(proveedor.enviar(anyString(), anyString(), anyString(), anyString(), anyLong()))
        .thenReturn(new ResultadoProveedorCpeDTO(null, null, null, null, "Sin CDR", null));
    assertThat(servicio.enviar(COMPROBANTE, OPERACION).estado()).isEqualTo(EstadoEmisionElectronica.INCIERTO);

    assertThatThrownBy(() -> servicio.enviar(COMPROBANTE, OPERACION))
        .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("CDR");
  }

  @Test
  void laFaltaDeRespuestaSeRegistraComoIncierta() {
    when(proveedor.enviar(anyString(), anyString(), anyString(), anyString(), anyLong()))
        .thenThrow(new FacturacionProveedorException("Tiempo de espera agotado", true));

    var resultado = servicio.enviar(COMPROBANTE, OPERACION);

    assertThat(resultado.estado()).isEqualTo(EstadoEmisionElectronica.INCIERTO);
    assertThat(resultado.codigoRespuesta()).isEqualTo("NO_CONFIRMADO");
  }

  @Test
  void unErrorDelProveedorPermiteReintentar() {
    when(proveedor.enviar(anyString(), anyString(), anyString(), anyString(), anyLong()))
        .thenThrow(new FacturacionProveedorException("Credencial rechazada", false))
        .thenReturn(new ResultadoProveedorCpeDTO(null, null, null, "0", "Aceptada", true));

    var fallo = servicio.enviar(COMPROBANTE, OPERACION);
    assertThat(fallo.estado()).isEqualTo(EstadoEmisionElectronica.ERROR_ENVIO);
    assertThat(fallo.codigoRespuesta()).isEqualTo("ERROR_PROVEEDOR");

    var reintento = servicio.enviar(COMPROBANTE, OPERACION);
    assertThat(reintento.estado()).isEqualTo(EstadoEmisionElectronica.ACEPTADO);
    assertThat(reintento.intentos()).isEqualTo(2);
  }

  @Test
  void noEnviaSimulacionesNiDocumentosEnCurso() {
    emision.setCodigoRespuesta("SIMULACION_LOCAL");
    assertThatThrownBy(() -> servicio.enviar(COMPROBANTE, OPERACION)).isInstanceOf(ReglaNegocioException.class);

    emision = emision(EstadoEmisionElectronica.ENVIANDO, 1);
    assertThat(servicio.enviar(COMPROBANTE, OPERACION).estado()).isEqualTo(EstadoEmisionElectronica.ENVIANDO);
    verify(proveedor, never()).enviar(anyString(), anyString(), anyString(), anyString(), anyLong());
  }

  @Test
  void consultarResuelveUnEnvioIncierto() {
    emision = emision(EstadoEmisionElectronica.INCIERTO, 1);
    when(proveedor.consultar("20999999999", "03", "B001", 15L))
        .thenReturn(new ResultadoProveedorCpeDTO(null, null, "UEsDBA==", "0", "Aceptada", true));

    assertThat(servicio.consultar(COMPROBANTE, OPERACION).estado()).isEqualTo(EstadoEmisionElectronica.ACEPTADO);
    // Un resultado definitivo se devuelve sin volver a consultar al proveedor.
    assertThat(servicio.consultar(COMPROBANTE, OPERACION).estado()).isEqualTo(EstadoEmisionElectronica.ACEPTADO);
    verify(proveedor).consultar(anyString(), anyString(), anyString(), anyLong());
  }

  @Test
  void consultarSinEnvioPrevioOConFalloSeControla() {
    assertThatThrownBy(() -> servicio.consultar(COMPROBANTE, OPERACION))
        .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Envía");

    emision = emision(EstadoEmisionElectronica.ERROR_ENVIO, 1);
    when(proveedor.consultar(anyString(), anyString(), anyString(), anyLong()))
        .thenThrow(new FacturacionProveedorException("Sin conexión", false));
    // Cualquier fallo de consulta deja el documento incierto: no se sabe si SUNAT lo recibió.
    assertThat(servicio.consultar(COMPROBANTE, OPERACION).estado()).isEqualTo(EstadoEmisionElectronica.INCIERTO);
  }

  @Test
  void obtenerExigeUnaEmisionExistente() {
    assertThat(servicio.obtener(COMPROBANTE).idComprobante()).isEqualTo(COMPROBANTE);
    when(emisiones.findByComprobanteIdComprobante(99L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> servicio.obtener(99L)).isInstanceOf(RecursoNoEncontradoException.class);
  }

  @Test
  void entregaArchivosSoloCuandoEstanDisponiblesYSonValidos() {
    assertThatThrownBy(() -> servicio.archivo(COMPROBANTE, "xml")).isInstanceOf(RecursoNoEncontradoException.class);
    assertThatThrownBy(() -> servicio.archivo(COMPROBANTE, "cdr")).isInstanceOf(RecursoNoEncontradoException.class);

    emision.setXmlFirmado("<xml/>");
    assertThat(servicio.archivo(COMPROBANTE, "xml")).isEqualTo("<xml/>".getBytes(StandardCharsets.UTF_8));

    var zip = new byte[] {'P', 'K', 3, 4};
    emision.setCdrZip(Base64.getEncoder().encodeToString(zip));
    assertThat(servicio.archivo(COMPROBANTE, "cdr")).isEqualTo(zip);
    emision.setCdrZip(Base64.getEncoder().encodeToString("no es zip".getBytes(StandardCharsets.UTF_8)));
    assertThatThrownBy(() -> servicio.archivo(COMPROBANTE, "cdr")).isInstanceOf(ServicioExternoException.class);
    emision.setCdrZip("%%%");
    assertThatThrownBy(() -> servicio.archivo(COMPROBANTE, "cdr")).isInstanceOf(ServicioExternoException.class);

    when(proveedor.pdf("{}", "20999999999")).thenReturn(new byte[] {1})
        .thenThrow(new FacturacionProveedorException("Caído", false));
    assertThat(servicio.archivo(COMPROBANTE, "pdf")).containsExactly(1);
    assertThatThrownBy(() -> servicio.archivo(COMPROBANTE, "pdf")).isInstanceOf(ServicioExternoException.class);

    emision.setCodigoRespuesta("SIMULACION_LOCAL");
    assertThatThrownBy(() -> servicio.archivo(COMPROBANTE, "xml")).isInstanceOf(ReglaNegocioException.class);
  }

  private static EmisionElectronica emision(EstadoEmisionElectronica estado, int intentos) {
    var comprobante = new Comprobante();
    comprobante.setIdComprobante(COMPROBANTE);
    var e = new EmisionElectronica();
    e.setIdEmision(3L);
    e.setComprobante(comprobante);
    e.setUsuario(new Usuario(5L, null, "caja", EstadoUsuario.ACTIVO));
    e.setEstado(estado);
    e.setIntentos(intentos);
    e.setAmbiente("BETA");
    e.setRucEmisor("20999999999");
    e.setTipoDocumento("03");
    e.setSerie("B001");
    e.setCorrelativo(15L);
    e.setFechaEmision(LocalDateTime.now());
    e.setFechaActualizacion(LocalDateTime.now());
    e.setSolicitud("{}");
    e.setSubtotal(new BigDecimal("84.75"));
    e.setIgv(new BigDecimal("15.25"));
    e.setTotal(new BigDecimal("100.00"));
    return e;
  }
}
