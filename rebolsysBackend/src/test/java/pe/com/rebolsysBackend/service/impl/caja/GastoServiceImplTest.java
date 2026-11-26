package pe.com.rebolsysBackend.service.impl.caja;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import pe.com.rebolsysBackend.dto.caja.GastoFiltroDTO;
import pe.com.rebolsysBackend.dto.caja.GastoRequestDTO;
import pe.com.rebolsysBackend.dto.caja.GastoResumenDTO;
import pe.com.rebolsysBackend.entity.FlujoCaja;
import pe.com.rebolsysBackend.entity.Pago;
import pe.com.rebolsysBackend.entity.TipoMovimientoCaja;
import pe.com.rebolsysBackend.entity.TurnoCaja;
import pe.com.rebolsysBackend.entity.Usuario;
import pe.com.rebolsysBackend.enums.EstadoUsuario;
import pe.com.rebolsysBackend.exception.RecursoNoEncontradoException;
import pe.com.rebolsysBackend.exception.ReglaNegocioException;
import pe.com.rebolsysBackend.repository.accesos.UsuarioRepository;
import pe.com.rebolsysBackend.repository.caja.FlujoCajaRepository;
import pe.com.rebolsysBackend.repository.caja.TipoMovimientoCajaRepository;
import pe.com.rebolsysBackend.repository.caja.TurnoCajaRepository;
import pe.com.rebolsysBackend.service.impl.comun.ReglasPropuesta;

@ExtendWith(MockitoExtension.class)
class GastoServiceImplTest {
  private static final long USUARIO = 7L;
  private static final long TURNO = 11L;
  private static final long TIPO_GASTO = 3L;
  private static final String HUELLA = "A".repeat(64);

  @Mock EntityManager entityManager;
  @Mock FlujoCajaRepository flujos;
  @Mock TurnoCajaRepository turnos;
  @Mock TipoMovimientoCajaRepository tipos;
  @Mock ReglasPropuesta reglas;
  @Mock UsuarioRepository usuarios;
  @Mock CajaService caja;
  @InjectMocks GastoServiceImpl servicio;

  @Test
  void registraUnGastoValidoComoSalidaDeCaja() {
    var turno = turno(USUARIO, true);
    when(turnos.bloquear(TURNO)).thenReturn(Optional.of(turno));
    when(flujos.findByClaveRegistro("clave-1")).thenReturn(Optional.empty());
    when(tipos.findById(TIPO_GASTO)).thenReturn(Optional.of(gasto()));
    when(flujos.saveAndFlush(any())).thenAnswer(inv -> {
      FlujoCaja f = inv.getArgument(0);
      f.setIdFlujo(99L);
      return f;
    });

    var respuesta = servicio.registrar(solicitud("  Compra de focos  ", "doc-1 ", null, null, "clave-1"));

    var guardado = ArgumentCaptor.forClass(FlujoCaja.class);
    verify(flujos).saveAndFlush(guardado.capture());
    verify(reglas).usuario(USUARIO);
    verify(entityManager).refresh(turno);
    assertThat(guardado.getValue().getConcepto()).isEqualTo("Compra de focos");
    assertThat(guardado.getValue().getDocumentoRespaldo()).isEqualTo("doc-1");
    assertThat(guardado.getValue().getClaveRegistro()).isEqualTo("clave-1");
    assertThat(respuesta.idFlujo()).isEqualTo(99L);
    assertThat(respuesta.monto()).isEqualByComparingTo("15.50");
    assertThat(respuesta.turnoAbierto()).isTrue();
  }

  @Test
  void registraSinClaveNiEvidenciaYNormalizaLaHuella() {
    when(turnos.bloquear(TURNO)).thenReturn(Optional.of(turno(USUARIO, true)));
    when(tipos.findById(TIPO_GASTO)).thenReturn(Optional.of(gasto()));
    when(flujos.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

    var respuesta = servicio.registrar(solicitud("Pasajes", " ", "comprobantes/ticket.pdf", HUELLA, null));

    verify(flujos, never()).findByClaveRegistro(any());
    assertThat(respuesta.documentoRespaldo()).isNull();
    assertThat(respuesta.evidenciaSha256()).isEqualTo("a".repeat(64));
  }

  @Test
  void rechazaUnTurnoInexistente() {
    when(turnos.bloquear(TURNO)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> servicio.registrar(solicitud("Gasto", null, null, null, null)))
        .isInstanceOf(RecursoNoEncontradoException.class);
    verify(flujos, never()).saveAndFlush(any());
  }

  @Test
  void rechazaUnTurnoAjenoSinGuardar() {
    when(turnos.bloquear(TURNO)).thenReturn(Optional.of(turno(99L, true)));
    assertThatThrownBy(() -> servicio.registrar(solicitud("Gasto", null, null, null, "clave-1")))
        .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("otro usuario");
    verify(flujos, never()).findByClaveRegistro(any());
    verify(flujos, never()).saveAndFlush(any());
  }

  @Test
  void rechazaUnTurnoCerradoSinGuardar() {
    when(turnos.bloquear(TURNO)).thenReturn(Optional.of(turno(USUARIO, false)));
    assertThatThrownBy(() -> servicio.registrar(solicitud("Gasto", null, null, null, null)))
        .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("abierto");
    verify(flujos, never()).saveAndFlush(any());
  }

  @Test
  void rechazaTiposQueNoSonSalidaOInexistentes() {
    when(turnos.bloquear(TURNO)).thenReturn(Optional.of(turno(USUARIO, true)));
    when(tipos.findById(TIPO_GASTO)).thenReturn(Optional.of(new TipoMovimientoCaja(TIPO_GASTO, "INGRESO", 1)));
    assertThatThrownBy(() -> servicio.registrar(solicitud("Gasto", null, null, null, null)))
        .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("salida");

    when(tipos.findById(TIPO_GASTO)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> servicio.registrar(solicitud("Gasto", null, null, null, null)))
        .isInstanceOf(RecursoNoEncontradoException.class);
    verify(flujos, never()).saveAndFlush(any());
  }

  @Test
  void rechazaEvidenciasDeCajaOIncompletas() {
    when(turnos.bloquear(TURNO)).thenReturn(Optional.of(turno(USUARIO, true)));
    when(tipos.findById(TIPO_GASTO)).thenReturn(Optional.of(gasto()));
    var rutaCaja = "caja/123e4567-e89b-12d3-a456-426614174000.png";
    assertThatThrownBy(() -> servicio.registrar(solicitud("Gasto", null, rutaCaja, HUELLA, null)))
        .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("módulo de Caja");
    assertThatThrownBy(() -> servicio.registrar(solicitud("Gasto", null, "otra/ruta.pdf", null, null)))
        .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("juntas");
    assertThatThrownBy(() -> servicio.registrar(solicitud("Gasto", null, null, HUELLA, null)))
        .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("juntas");
    verify(flujos, never()).saveAndFlush(any());
  }

  @Test
  void repetirLaMismaSolicitudDevuelveElRegistroExistente() {
    var turno = turno(USUARIO, true);
    when(turnos.bloquear(TURNO)).thenReturn(Optional.of(turno));
    var previo = flujo(turno, gasto(), "Compra de focos", "15.50");
    previo.setDocumentoRespaldo("doc-1");
    previo.setClaveRegistro("clave-1");
    when(flujos.findByClaveRegistro("clave-1")).thenReturn(Optional.of(previo));

    var respuesta = servicio.registrar(solicitud(" Compra de focos ", "doc-1", null, null, "clave-1"));

    assertThat(respuesta.idFlujo()).isEqualTo(previo.getIdFlujo());
    verify(flujos, never()).saveAndFlush(any());
    verify(tipos, never()).findById(any());
  }

  @Test
  void repetirLaClaveConOtrosDatosSeRechaza() {
    var turno = turno(USUARIO, true);
    when(turnos.bloquear(TURNO)).thenReturn(Optional.of(turno));
    var previo = flujo(turno, gasto(), "Compra de focos", "15.50");
    when(flujos.findByClaveRegistro("clave-1")).thenReturn(Optional.of(previo));

    assertThatThrownBy(() -> servicio.registrar(new GastoRequestDTO(USUARIO, TURNO, TIPO_GASTO,
        "Compra de focos", new BigDecimal("20.00"), null, null, null, "clave-1")))
        .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("otro gasto");
    verify(flujos, never()).saveAndFlush(any());
  }

  @Test
  void laClaveDeUnCobroNoSeReutilizaComoGasto() {
    var turno = turno(USUARIO, true);
    when(turnos.bloquear(TURNO)).thenReturn(Optional.of(turno));
    var cobro = flujo(turno, gasto(), "Compra de focos", "15.50");
    cobro.setPago(new Pago());
    when(flujos.findByClaveRegistro("clave-1")).thenReturn(Optional.of(cobro));

    assertThatThrownBy(() -> servicio.registrar(solicitud("Compra de focos", null, null, null, "clave-1")))
        .isInstanceOf(ReglaNegocioException.class);
  }

  @Test
  void obtieneSoloGastosYListaLosDelTurno() {
    var turno = turno(USUARIO, false);
    var gasto = flujo(turno, gasto(), "Limpieza", "8.00");
    var ingreso = flujo(turno, new TipoMovimientoCaja(1L, "INGRESO", 1), "Ingreso", "5.00");
    when(flujos.findById(1L)).thenReturn(Optional.of(gasto));
    when(flujos.findById(2L)).thenReturn(Optional.of(ingreso));

    assertThat(servicio.obtener(1L).turnoAbierto()).isFalse();
    assertThatThrownBy(() -> servicio.obtener(2L)).isInstanceOf(RecursoNoEncontradoException.class);

    when(turnos.existsById(TURNO)).thenReturn(true);
    when(flujos.findByTurnoCajaIdTurnoOrderByIdFlujoDesc(TURNO)).thenReturn(List.of(gasto, ingreso));
    assertThat(servicio.listar(TURNO)).hasSize(1).first()
        .satisfies(g -> assertThat(g.concepto()).isEqualTo("Limpieza"));

    when(turnos.existsById(5L)).thenReturn(false);
    assertThatThrownBy(() -> servicio.listar(5L)).isInstanceOf(RecursoNoEncontradoException.class);
  }

  @Test
  @SuppressWarnings("unchecked")
  void consultaElHistorialConFiltrosValidados() {
    var turno = turno(USUARIO, true);
    var filtro = new GastoFiltroDTO(TURNO, USUARIO, TIPO_GASTO, null, null, " focos ", null, null, null, null);
    when(turnos.existsById(TURNO)).thenReturn(true);
    when(usuarios.existsById(USUARIO)).thenReturn(true);
    when(tipos.findById(TIPO_GASTO)).thenReturn(Optional.of(gasto()));
    when(flujos.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(flujo(turno, gasto(), "Focos", "15.50"))));
    var resumen = new GastoResumenDTO(1L, new BigDecimal("15.50"));
    when(flujos.resumir(any())).thenReturn(resumen);

    var historial = servicio.historial(filtro);

    var pagina = ArgumentCaptor.forClass(Pageable.class);
    verify(flujos).findAll(any(Specification.class), pagina.capture());
    assertThat(pagina.getValue().getPageSize()).isEqualTo(20);
    assertThat(pagina.getValue().getSort().getOrderFor("fechaHora").getDirection()).isEqualTo(Sort.Direction.DESC);
    assertThat(historial.gastos().contenido()).hasSize(1);
    assertThat(historial.resumen()).isEqualTo(resumen);
  }

  @Test
  void elHistorialRechazaReferenciasInexistentesOTiposDeIngreso() {
    when(turnos.existsById(TURNO)).thenReturn(false);
    assertThatThrownBy(() -> servicio.historial(filtro(TURNO, null, null)))
        .isInstanceOf(RecursoNoEncontradoException.class).hasMessageContaining("turno");

    when(usuarios.existsById(USUARIO)).thenReturn(false);
    assertThatThrownBy(() -> servicio.historial(filtro(null, USUARIO, null)))
        .isInstanceOf(RecursoNoEncontradoException.class).hasMessageContaining("usuario");

    when(tipos.findById(1L)).thenReturn(Optional.of(new TipoMovimientoCaja(1L, "INGRESO", 1)));
    assertThatThrownBy(() -> servicio.historial(filtro(null, null, 1L)))
        .isInstanceOf(ReglaNegocioException.class);

    when(tipos.findById(2L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> servicio.historial(filtro(null, null, 2L)))
        .isInstanceOf(RecursoNoEncontradoException.class);
  }

  @Test
  void elCatalogoSoloOfreceTiposDeSalida() {
    when(tipos.findAll(any(Sort.class))).thenReturn(List.of(
        gasto(), new TipoMovimientoCaja(1L, "INGRESO_MANUAL", 1)));
    assertThat(servicio.tipos()).singleElement()
        .satisfies(t -> assertThat(t.id()).isEqualTo(TIPO_GASTO));
  }

  private static GastoFiltroDTO filtro(Long turno, Long usuario, Long tipo) {
    return new GastoFiltroDTO(turno, usuario, tipo, null, null, null, null, null, null, null);
  }

  private static GastoRequestDTO solicitud(String concepto, String documento, String ruta, String huella, String clave) {
    return new GastoRequestDTO(USUARIO, TURNO, TIPO_GASTO, concepto, new BigDecimal("15.50"),
        documento, ruta, huella, clave);
  }

  private static TipoMovimientoCaja gasto() {
    return new TipoMovimientoCaja(TIPO_GASTO, "GASTO", -1);
  }

  private static TurnoCaja turno(long idUsuario, boolean abierto) {
    var usuario = new Usuario(idUsuario, null, "usuario" + idUsuario, EstadoUsuario.ACTIVO);
    var turno = new TurnoCaja();
    turno.setIdTurno(TURNO);
    turno.setUsuario(usuario);
    turno.setEstadoSesion(abierto ? 1 : 0);
    turno.setFechaCierre(abierto ? null : LocalDateTime.now());
    return turno;
  }

  private static FlujoCaja flujo(TurnoCaja turno, TipoMovimientoCaja tipo, String concepto, String monto) {
    var flujo = new FlujoCaja();
    flujo.setIdFlujo(50L);
    flujo.setTurnoCaja(turno);
    flujo.setTipoMovimientoCaja(tipo);
    flujo.setConcepto(concepto);
    flujo.setMonto(new BigDecimal(monto));
    flujo.setFechaHora(LocalDateTime.now());
    return flujo;
  }
}
