package pe.com.rebolsysBackend.service.impl.limpieza;

import pe.com.rebolsysBackend.repository.caja.TurnoCajaRepository;
import pe.com.rebolsysBackend.repository.limpieza.DetalleInformeRepository;
import pe.com.rebolsysBackend.repository.limpieza.InformeRevisionRepository;
import pe.com.rebolsysBackend.repository.limpieza.TicketMantenimientoRepository;
import pe.com.rebolsysBackend.repository.mantenimiento.HabitacionRepository;
import pe.com.rebolsysBackend.repository.mantenimiento.ItemRepository;
import pe.com.rebolsysBackend.repository.reservas.DetalleReservaRepository;
import pe.com.rebolsysBackend.service.impl.comun.ReglasPropuesta;
import pe.com.rebolsysBackend.service.impl.mantenimiento.ItemServiceImpl;
import pe.com.rebolsysBackend.security.SesionOperacionService;
import pe.com.rebolsysBackend.security.AccesosService;
import static java.util.stream.Collectors.*;
import static pe.com.rebolsysBackend.enums.EstadoConservacion.BUENO;
import static pe.com.rebolsysBackend.enums.EstadoInforme.*;
import static pe.com.rebolsysBackend.service.impl.comun.ReglasPropuesta.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.rebolsysBackend.dto.limpieza.LimpiezaDTO.*;
import pe.com.rebolsysBackend.dto.comun.PaginaResponseDTO;
import pe.com.rebolsysBackend.dto.accesos.SesionActualDTO;
import pe.com.rebolsysBackend.entity.*;
import pe.com.rebolsysBackend.enums.*;
import pe.com.rebolsysBackend.exception.RecursoNoEncontradoException;
import pe.com.rebolsysBackend.service.service.limpieza.LimpiezaService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LimpiezaServiceImpl implements LimpiezaService {
  private static final Set<EstadoInforme> ACTIVOS = EnumSet.of(BORRADOR, POR_APROBAR);
  private static final Logger LOG = LoggerFactory.getLogger(LimpiezaServiceImpl.class);
  private static final String SIN_REGISTRAR = "SIN_REGISTRAR";

  private final HabitacionRepository habitaciones;
  private final DetalleReservaRepository estancias;
  private final ItemRepository mobiliario;
  private final InformeRevisionRepository informes;
  private final DetalleInformeRepository detalles;
  private final TicketMantenimientoRepository tickets;
  private final TurnoCajaRepository turnos;
  private final SesionOperacionService sesion;
  private final ReglasPropuesta reglas;
  private final ItemServiceImpl inventario;
  private final EvidenciaLimpiezaService evidencias;

  @Override
  public Inicio inicio() {
    var actual = sesion.actual();
    if (actual.gerente()) {
      long pendientes = informes.countByEstadoInforme(POR_APROBAR);
      long aprobados = informes.countByEstadoInforme(APROBADO);
      return new Inicio(null, null, pendientes + aprobados, pendientes, aprobados, tickets.count(), "GENERAL");
    }
    boolean recepcionista = actual.roles().contains(AccesosService.RECEPCIONISTA);
    if (recepcionista && actual.idTurno() == null) return new Inicio(null, null, 0, 0, 0, 0, "TURNO");
    var turno = recepcionista ? turnos.findById(actual.idTurno()).orElseThrow() : null;
    var desde = turno == null ? ahora().toLocalDate().atStartOfDay() : turno.getFechaApertura();
    var hasta = turno == null || turno.getFechaCierre() == null ? ahora() : turno.getFechaCierre();
    long pendientes = informes.countByUsuarioIdUsuarioAndEstadoInformeAndFechaHoraBetween(
        actual.idUsuario(), POR_APROBAR, desde, hasta);
    long aprobados = informes.countByUsuarioIdUsuarioAndEstadoInformeAndFechaHoraBetween(
        actual.idUsuario(), APROBADO, desde, hasta);
    long generados = tickets.countByEmpleadoIdEmpleadoAndFechaGeneracionBetween(
        usuarioActual().getEmpleado().getIdEmpleado(), desde, hasta);
    return new Inicio(turno == null ? null : turno.getIdTurno(), desde,
        pendientes + aprobados, pendientes, aprobados, generados, turno == null ? "DIA" : "TURNO");
  }

  @Override
  public List<HabitacionResumen> habitaciones(FiltroHabitaciones filtro) {
    var actual = sesion.actual();
    var activos = informes.findByEstadoInformeIn(ACTIVOS).stream()
        .collect(toMap(i -> i.getHabitacion().getIdHabitacion(), i -> i));
    var salidas = estancias.ultimasSalidas(null).stream()
        .collect(toMap(d -> d.getHabitacion().getIdHabitacion(), d -> d));
    String numero = filtro.numeroHabitacion() == null ? "" : filtro.numeroHabitacion().strip().toLowerCase(Locale.ROOT);
    return habitaciones.findAll(Sort.by("numeroCuarto")).stream()
        .map(h -> resumenHabitacion(h, activos.get(h.getIdHabitacion()), salidas.get(h.getIdHabitacion()), actual))
        .filter(h -> h.numeroHabitacion().toLowerCase(Locale.ROOT).contains(numero))
        .filter(h -> filtro.idTipoHabitacion() == null || filtro.idTipoHabitacion().equals(h.idTipoHabitacion()))
        .filter(h -> filtro.estado() == null || filtro.estado() == h.estado())
        .filter(h -> filtro.fechaSalida() == null
            || (h.fechaSalida() != null && filtro.fechaSalida().equals(h.fechaSalida().toLocalDate())))
        .toList();
  }

  @Override
  @Transactional
  public HabitacionDetalle habitacion(Long id) {
    var actual = sesion.actual();
    var h = bloquearHabitacion(id);
    var activo = activo(id).orElse(null);
    var resumen = resumenHabitacion(h, activo, ultimaSalida(id), actual);
    boolean ocultar = h.getEstadoHabitacion() == EstadoHabitacion.EN_LIMPIEZA || activo != null;
    if ((h.getEstadoHabitacion() == EstadoHabitacion.EN_LIMPIEZA) != (activo != null)) {
      LOG.warn("Estado de limpieza inconsistente en habitación {}: estado={}, informeActivo={}",
          id, h.getEstadoHabitacion(), activo == null ? null : activo.getIdInformeRevision());
    }
    var items = itemsHabitacion(id, ocultar);
    var ultimo = ocultar ? null : informes
        .findFirstByHabitacionIdHabitacionAndEstadoInformeOrderByFechaHoraDescIdInformeRevisionDesc(id, APROBADO)
        .map(this::detalleInforme).orElse(null);
    var asociados = ultimo == null ? List.<TicketResumen>of() : ultimo.tickets();
    boolean verInforme = actual.tiene("LIMPIEZA:VER_INFORMES");
    boolean verTickets = actual.tiene("LIMPIEZA:VER_TICKETS");
    String estadoInforme = ultimo == null ? "SIN_INFORME"
        : verInforme ? ultimo.informe().estado().name() : "ACCESO_RESTRINGIDO";
    String estadoTickets = asociados.isEmpty() ? "SIN_TICKET" : verTickets ? "CON_TICKETS" : "ACCESO_RESTRINGIDO";
    if (ultimo != null && verInforme && !verTickets) {
      ultimo = new InformeDetalle(ultimo.informe(), ultimo.idDetalleReserva(), ultimo.observaciones(),
          ultimo.items(), List.of());
    }
    return new HabitacionDetalle(resumen, items, estadoInforme, verInforme ? ultimo : null,
        estadoTickets, verTickets ? asociados : List.of());
  }

  @Override
  @Transactional
  public Borrador iniciar(Long idHabitacion) {
    var actual = sesion.actual();
    exigirAccion("INICIAR");
    var usuario = usuarioActual();
    var h = bloquearHabitacion(idHabitacion);
    var existente = activo(idHabitacion);
    exigir(h.getEstadoHabitacion() == EstadoHabitacion.POR_LIMPIAR ||
        (h.getEstadoHabitacion() == EstadoHabitacion.EN_LIMPIEZA && existente.isPresent()) ||
        (actual.gerente() && h.getEstadoHabitacion() == EstadoHabitacion.DISPONIBLE),
        "La habitación debe estar por limpiar o disponible para una revisión de gerencia");
    var estancia = ultimaSalida(idHabitacion);
    exigir(estancia == null || estancia.getEstadoDetalle() != EstadoReserva.EN_CURSO,
        "No se puede iniciar una revisión durante una estancia en curso");
    var informe = existente.orElseGet(() -> {
      var borrador = new InformeRevision();
      borrador.setHabitacion(h);
      borrador.setUsuario(usuario);
      borrador.setDetalleReserva(estancia);
      borrador.setFechaHora(ahora());
      borrador.setObservaciones("Sin observaciones");
      borrador.setEstadoInforme(BORRADOR);
      return informes.save(borrador);
    });
    exigir(informe.getEstadoInforme() == BORRADOR, "La habitación ya tiene un informe por aprobar");
    exigir(informe.getUsuario().getIdUsuario().equals(usuario.getIdUsuario()),
        "Otro usuario inició la limpieza de la habitación");
    h.setEstadoHabitacion(EstadoHabitacion.EN_LIMPIEZA);
    var items = mobiliario.actualesHabitacion(idHabitacion).stream().map(d -> itemHabitacion(d, null)).toList();
    return new Borrador(informe.getIdInformeRevision(), resumenHabitacion(h, informe, estancia, actual),
        items);
  }

  @Override
  @Transactional
  public void cancelarBorrador(Long idInforme) {
    exigirAccion("CANCELAR_BORRADOR");
    var usuario = usuarioActual();
    var informe = bloquearInforme(idInforme);
    validarBorrador(informe, usuario);
    informe.getHabitacion().setEstadoHabitacion(EstadoHabitacion.POR_LIMPIAR);
    informes.delete(informe);
  }

  @Override
  @Transactional
  public InformeDetalle enviar(Long idInforme, EnviarInforme datos) {
    exigirAccion("ENVIAR_INFORME");
    reglas.validar(datos);
    var usuario = usuarioActual();
    var informe = bloquearInforme(idInforme);
    validarBorrador(informe, usuario);
    var items = mobiliario.actualesHabitacion(informe.getHabitacion().getIdHabitacion());
    var entradas = indexarItems(datos.items());
    exigir(entradas.keySet().equals(items.stream().map(Item::getIdItem).collect(toSet())),
        "Se debe registrar exactamente una vez cada ítem actual de la habitación");
    var lineas = new ArrayList<DetalleInforme>();
    for (var item : items) {
      var linea = new DetalleInforme();
      linea.setInformeRevision(informe);
      linea.setItem(item);
      linea.setHabitacion(informe.getHabitacion());
      linea.setCondicionAnterior(item.getCondicionItem());
      guardarItem(linea, entradas.get(item.getIdItem()));
      lineas.add(linea);
    }
    informe.setObservaciones(Objects.requireNonNullElse(limpiar(datos.observaciones()), "Sin observaciones"));
    informe.setFechaHora(ahora());
    informe.setEstadoInforme(POR_APROBAR);
    return detalleInforme(informe, detalles.saveAllAndFlush(lineas), Map.of());
  }

  @Override
  public PaginaResponseDTO<InformeResumen> informes(FiltroInformes filtro, Pageable pagina) {
    sesion.actual();
    var paginaInformes = informes.buscar(patronBusqueda(filtro.nombre()), patronBusqueda(filtro.numeroHabitacion()),
        filtro.estado(), desde(filtro.fechaEmision()), hasta(filtro.fechaEmision()), pagina);
    var ids = paginaInformes.stream().map(InformeRevision::getIdInformeRevision).toList();
    var lineas = ids.isEmpty() ? Map.<Long, List<DetalleInforme>>of()
        : detalles.findByInformeRevisionIdInformeRevisionInOrderByIdDetalleInforme(ids).stream()
            .collect(groupingBy(d -> d.getInformeRevision().getIdInformeRevision()));
    var asociados = ids.isEmpty() ? Map.<Long, TicketMantenimiento>of() : ticketsPorDetalle(ids);
    return paginar(paginaInformes.map(i ->
        resumenInforme(i, lineas.getOrDefault(i.getIdInformeRevision(), List.of()), asociados)));
  }

  @Override
  public InformeDetalle informe(Long id) {
    sesion.actual();
    var informe = buscarInforme(id);
    if (informe.getEstadoInforme() == BORRADOR) throw noExiste("El informe enviado");
    return detalleInforme(informe);
  }

  @Override
  @Transactional
  public InformeDetalle editarInforme(Long id, EditarInforme datos) {
    exigirAccion("EDITAR_INFORME");
    reglas.validar(datos);
    var informe = bloquearInforme(id);
    validarPendiente(informe);
    var lineas = detallesInforme(id);
    var asociados = ticketsPorDetalle(List.of(id));
    var entradas = indexarItems(datos.items());
    exigir(!entradas.isEmpty(), "Indica al menos un ítem para editar");
    var idsInforme = lineas.stream().map(d -> d.getItem().getIdItem()).collect(toSet());
    exigir(idsInforme.containsAll(entradas.keySet()), "Solo se pueden editar ítems del informe");
    for (var linea : lineas) {
      var entrada = entradas.get(linea.getItem().getIdItem());
      if (entrada == null) continue;
      exigir(!asociados.containsKey(linea.getIdDetalleInforme()) || linea.getEstadoEncontrado() == entrada.estadoEncontrado(),
          "Elimina el ticket asociado antes de modificar el estado encontrado del ítem");
      guardarItem(linea, entrada);
    }
    return detalleInforme(informe, lineas, asociados);
  }

  @Override
  @Transactional
  public void eliminarInforme(Long id) {
    exigirAccion("ELIMINAR_INFORME");
    var usuario = usuarioActual();
    var informe = bloquearInforme(id);
    validarPendiente(informe);
    var asociados = tickets.findByDetalleInformeInformeRevisionIdInformeRevisionInOrderByIdTicket(List.of(id));
    if (!asociados.isEmpty()) exigirAccion("ELIMINAR_TICKET");
    asociados.forEach(ticket -> revertirTicket(ticket, usuario));
    tickets.deleteAll(asociados);
    tickets.flush();
    informe.getHabitacion().setEstadoHabitacion(EstadoHabitacion.POR_LIMPIAR);
    informes.delete(informe);
  }

  @Override
  @Transactional
  public InformeDetalle aprobar(Long id) {
    exigirAccion("APROBAR_INFORME");
    var usuario = usuarioActual();
    var informe = bloquearInforme(id);
    if (informe.getEstadoInforme() == APROBADO) return detalleInforme(informe);
    validarPendiente(informe);
    var lineas = detallesInforme(id);
    var asociados = ticketsPorDetalle(List.of(id));
    exigir(lineas.stream().noneMatch(d -> pendiente(d, asociados.get(d.getIdDetalleInforme()))),
        "Se debe resolver cada incidencia con un ticket antes de aprobar el informe");
    var habitacion = informe.getHabitacion();
    var actuales = mobiliario.actualesHabitacion(habitacion.getIdHabitacion()).stream()
        .collect(toMap(Item::getIdItem, d -> d));
    exigir(actuales.keySet().equals(lineas.stream().map(d -> d.getItem().getIdItem()).collect(toSet())),
        "El mobiliario actual no coincide con el informe");
    for (var linea : lineas) {
      var actual = actuales.get(linea.getItem().getIdItem());
      cambiarConservacion(actual, BUENO, usuario, "Aprobación de informe INF-" + id,
          "informe:" + linea.getIdDetalleInforme());
    }
    informe.setEstadoInforme(APROBADO);
    informe.setUsuarioAprobador(usuario);
    habitacion.setEstadoHabitacion(EstadoHabitacion.DISPONIBLE);
    return detalleInforme(informe, lineas, asociados);
  }

  @Override
  public PaginaResponseDTO<TicketResumen> tickets(FiltroTickets filtro, Pageable pagina) {
    sesion.actual();
    return paginar(tickets.buscar(patronBusqueda(filtro.nombre()), patronBusqueda(filtro.numeroHabitacion()),
        filtro.tipoAccion(), desde(filtro.fechaEmision()), hasta(filtro.fechaEmision()), pagina).map(this::resumenTicket));
  }

  @Override
  public TicketDetalle ticket(Long id) {
    sesion.actual();
    return detalleTicket(buscarTicket(id));
  }

  @Override
  @Transactional
  public TicketDetalle crearTicket(Long idInforme, Long idDetalle, CrearTicket datos) {
    exigirAccion("CREAR_TICKET");
    reglas.validar(datos);
    var usuario = usuarioActual();
    var informe = bloquearInforme(idInforme);
    validarPendiente(informe);
    var linea = detalles.findById(idDetalle)
        .filter(d -> d.getInformeRevision().getIdInformeRevision().equals(idInforme))
        .orElseThrow(() -> noExiste("El ítem del informe"));
    exigir(linea.getEstadoEncontrado() != BUENO, "El ítem no presenta una incidencia");
    exigir(tickets.findByDetalleInformeIdDetalleInforme(idDetalle).isEmpty(), "El ítem ya tiene un ticket");
    evidencias.verificar(datos.evidencia());
    var ticket = new TicketMantenimiento();
    ticket.setDetalleInforme(linea);
    ticket.setEmpleado(usuario.getEmpleado());
    ticket.setFechaGeneracion(ahora());
    ticket.setFechaResolucion(ticket.getFechaGeneracion());
    ticket.setEstadoTicket(EstadoTicket.RESUELTO);
    ticket.setEvidenciaRuta(datos.evidencia().evidenciaRuta().strip());
    ticket.setEvidenciaSha256(datos.evidencia().evidenciaSha256().toLowerCase(Locale.ROOT));
    guardarAccion(ticket, datos.accion());
    tickets.saveAndFlush(ticket);
    cambiarConservacion(itemActual(linea), BUENO, usuario, "Resolución de ticket TKT-" + ticket.getIdTicket(),
        "ticket:" + ticket.getIdTicket());
    return detalleTicket(ticket);
  }

  @Override
  @Transactional
  public TicketDetalle editarTicket(Long id, DatosTicket datos) {
    exigirAccion("EDITAR_TICKET");
    reglas.validar(datos);
    var ticket = bloquearTicket(id);
    guardarAccion(ticket, datos);
    return detalleTicket(ticket);
  }

  @Override
  @Transactional
  public void eliminarTicket(Long id) {
    exigirAccion("ELIMINAR_TICKET");
    var usuario = usuarioActual();
    var ticket = bloquearTicket(id);
    revertirTicket(ticket, usuario);
    tickets.delete(ticket);
  }

  private void revertirTicket(TicketMantenimiento ticket, Usuario usuario) {
    var linea = ticket.getDetalleInforme();
    cambiarConservacion(itemActual(linea), linea.getCondicionAnterior(), usuario,
        "Eliminación de ticket TKT-" + ticket.getIdTicket(), "eliminar-ticket:" + ticket.getIdTicket());
  }

  private void exigirAccion(String accion) {
    PoliticaAccionesLimpieza.exigirPermiso(sesion.actual(), accion);
  }

  private void cambiarConservacion(Item item, EstadoConservacion estado, Usuario usuario,
      String observacion, String clave) {
    exigir(estado != null, "Falta la condición anterior del ítem en el informe");
    if (item.getCondicionItem() != estado) {
      inventario.registrarConservacion(item, estado, usuario, observacion, hash("limpieza:" + clave));
    }
  }

  private Item itemActual(DetalleInforme linea) {
    return mobiliario.actualesHabitacion(linea.getHabitacion().getIdHabitacion()).stream()
        .filter(item -> item.getIdItem().equals(linea.getItem().getIdItem()))
        .findFirst().orElseThrow(() -> noExiste("El ítem actual de la habitación"));
  }

  private void guardarItem(DetalleInforme detalle, ItemInforme datos) {
    var evidencia = datos.evidencia();
    exigir(datos.estadoEncontrado() == BUENO || evidencia != null, "La incidencia requiere evidencia fotográfica");
    if (evidencia != null) evidencias.verificar(evidencia);
    detalle.setEstadoEncontrado(datos.estadoEncontrado());
    var observacion = limpiar(datos.observaciones());
    detalle.setObservaciones(observacion == null && datos.estadoEncontrado() != BUENO
        ? "Incidencia registrada con evidencia" : observacion);
    detalle.setEvidenciaRuta(evidencia == null ? null : evidencia.evidenciaRuta().strip());
    detalle.setEvidenciaSha256(evidencia == null ? null : evidencia.evidenciaSha256().toLowerCase(Locale.ROOT));
  }

  private void guardarAccion(TicketMantenimiento ticket, DatosTicket datos) {
    exigir(datos.tipoAccion() == TipoAccionMantenimiento.REPARACION || datos.costoReparacion() == null,
        "El costo de reparación solo corresponde a una reparación");
    ticket.setTipoAccion(datos.tipoAccion());
    ticket.setObservacionTecnica(limpiar(datos.observacionTecnica()));
    ticket.setCostoReparacion(datos.costoReparacion());
  }

  private Map<Long, ItemInforme> indexarItems(List<ItemInforme> items) {
    var entradas = new HashMap<Long, ItemInforme>();
    for (var item : items) {
      exigir(entradas.putIfAbsent(item.idItem(), item) == null,
          "No se puede registrar el mismo ítem más de una vez");
    }
    return entradas;
  }

  private Usuario usuarioActual() {
    return reglas.usuario(sesion.actual().idUsuario());
  }

  private Habitacion bloquearHabitacion(Long id) {
    return habitaciones.bloquear(id).orElseThrow(() -> noExiste("La habitación"));
  }

  private InformeRevision buscarInforme(Long id) {
    return informes.findById(id).orElseThrow(() -> noExiste("El informe"));
  }

  private InformeRevision bloquearInforme(Long id) {
    var idHabitacion = informes.habitacionDe(id).orElseThrow(() -> noExiste("El informe"));
    bloquearHabitacion(idHabitacion);
    return buscarInforme(id);
  }

  private TicketMantenimiento buscarTicket(Long id) {
    return tickets.findById(id).orElseThrow(() -> noExiste("El ticket"));
  }

  private TicketMantenimiento bloquearTicket(Long id) {
    var idInforme = tickets.informeDe(id).orElseThrow(() -> noExiste("El ticket"));
    validarPendiente(bloquearInforme(idInforme));
    return buscarTicket(id);
  }

  private void validarBorrador(InformeRevision informe, Usuario usuario) {
    exigir(informe.getHabitacion().getEstadoHabitacion() == EstadoHabitacion.EN_LIMPIEZA,
        "La habitación debe estar en limpieza");
    exigir(informe.getEstadoInforme() == BORRADOR, "El informe ya fue enviado");
    exigir(informe.getUsuario().getIdUsuario().equals(usuario.getIdUsuario()),
        "Solo el responsable de la limpieza puede completar o cancelar su borrador");
  }

  private void validarPendiente(InformeRevision informe) {
    exigir(PoliticaAccionesLimpieza.pendiente(informe),
        "El informe debe estar por aprobar y la habitación en limpieza");
  }

  private Optional<InformeRevision> activo(Long id) {
    return informes.findByHabitacionIdHabitacionAndEstadoInformeIn(id, ACTIVOS);
  }

  private DetalleReserva ultimaSalida(Long idHabitacion) {
    return estancias.ultimasSalidas(idHabitacion).stream().findFirst().orElse(null);
  }

  private static LocalDateTime salida(DetalleReserva d) {
    return d.getFechaSalidaReal() == null ? d.getFechaSalida() : d.getFechaSalidaReal();
  }

  private HabitacionResumen resumenHabitacion(Habitacion h, InformeRevision activo, DetalleReserva estancia, SesionActualDTO actual) {
    var estado = EstadoHabitacionLimpieza.valueOf(h.getEstadoHabitacion().name());
    var tipo = h.getTipoHabitacion();
    boolean retomar = PoliticaAccionesLimpieza.permite(actual, "INICIAR")
        && PoliticaAccionesLimpieza.borradorPropio(activo, actual);
    boolean puedeIniciar = PoliticaAccionesLimpieza.permite(actual, "INICIAR") && activo == null
        && (estancia == null || estancia.getEstadoDetalle() != EstadoReserva.EN_CURSO)
        && (estado == EstadoHabitacionLimpieza.POR_LIMPIAR ||
            (actual.gerente() && estado == EstadoHabitacionLimpieza.DISPONIBLE));
    return new HabitacionResumen(h.getIdHabitacion(), h.getNumeroCuarto(), tipo.getIdTipoHabitacion(),
        tipo.getNombreTipo(), estancia == null ? null : salida(estancia), estado,
        activo == null ? null : responsable(activo.getUsuario()), puedeIniciar, retomar);
  }

  private List<ItemHabitacion> itemsHabitacion(Long id, boolean ocultar) {
    return mobiliario.actualesHabitacion(id).stream()
        .map(d -> itemHabitacion(d, ocultar ? SIN_REGISTRAR : d.getCondicionItem().name())).toList();
  }

  private ItemHabitacion itemHabitacion(Item d, String estado) {
    return new ItemHabitacion(d.getIdItem(), d.getNombreItem(), estado);
  }

  private List<DetalleInforme> detallesInforme(Long id) {
    return detalles.findByInformeRevisionIdInformeRevisionInOrderByIdDetalleInforme(List.of(id));
  }

  private Map<Long, TicketMantenimiento> ticketsPorDetalle(List<Long> ids) {
    return tickets.findByDetalleInformeInformeRevisionIdInformeRevisionInOrderByIdTicket(ids).stream()
        .collect(toMap(t -> t.getDetalleInforme().getIdDetalleInforme(), t -> t, (a, b) -> a, LinkedHashMap::new));
  }

  private InformeDetalle detalleInforme(InformeRevision informe) {
    var id = informe.getIdInformeRevision();
    return detalleInforme(informe, detallesInforme(id), ticketsPorDetalle(List.of(id)));
  }

  private InformeDetalle detalleInforme(InformeRevision informe, List<DetalleInforme> lineas,
      Map<Long, TicketMantenimiento> asociados) {
    var resumen = resumenInforme(informe, lineas, asociados);
    var items = lineas.stream().map(d -> {
      var ticket = asociados.get(d.getIdDetalleInforme());
      var item = d.getItem();
      return new DetalleItem(d.getIdDetalleInforme(), item.getIdItem(),
          item.getNombreItem(), d.getEstadoEncontrado(), resuelto(ticket) ? BUENO : d.getEstadoEncontrado(),
          d.getObservaciones(), evidencia(d.getEvidenciaRuta(), d.getEvidenciaSha256()),
          ticket == null ? null : ticket.getIdTicket(), PoliticaAccionesLimpieza.pendiente(informe) && ticket == null && pendiente(d, null)
              && PoliticaAccionesLimpieza.permite(sesion.actual(), "CREAR_TICKET"));
    }).toList();
    var estancia = informe.getDetalleReserva();
    return new InformeDetalle(resumen, estancia == null ? null : estancia.getIdDetalleReserva(), informe.getObservaciones(),
        items, asociados.values().stream().map(this::resumenTicket).toList());
  }

  private InformeResumen resumenInforme(InformeRevision informe, List<DetalleInforme> lineas,
      Map<Long, TicketMantenimiento> asociados) {
    boolean editable = PoliticaAccionesLimpieza.pendiente(informe);
    var actual = sesion.actual();
    long pendientes = lineas.stream().filter(d -> pendiente(d, asociados.get(d.getIdDetalleInforme()))).count();
    boolean incidencias = lineas.stream().anyMatch(d -> d.getEstadoEncontrado() != BUENO);
    boolean tieneTickets = lineas.stream().anyMatch(d -> asociados.containsKey(d.getIdDetalleInforme()));
    var id = informe.getIdInformeRevision();
    var habitacion = informe.getHabitacion();
    return new InformeResumen(id, "INF-" + id, habitacion.getIdHabitacion(), habitacion.getNumeroCuarto(),
        informe.getFechaHora(), informe.getEstadoInforme(), responsable(informe.getUsuario()), incidencias, pendientes,
        tieneTickets, editable && pendientes == 0 && PoliticaAccionesLimpieza.permite(actual, "APROBAR_INFORME"),
        editable && !lineas.isEmpty() && PoliticaAccionesLimpieza.permite(actual, "EDITAR_INFORME"),
        editable && PoliticaAccionesLimpieza.permite(actual, "ELIMINAR_INFORME")
            && (!tieneTickets || PoliticaAccionesLimpieza.permite(actual, "ELIMINAR_TICKET")));
  }

  private boolean pendiente(DetalleInforme linea, TicketMantenimiento ticket) {
    return linea.getEstadoEncontrado() != BUENO && !resuelto(ticket);
  }

  private boolean resuelto(TicketMantenimiento ticket) {
    return ticket != null && ticket.getEstadoTicket() == EstadoTicket.RESUELTO;
  }

  private TicketResumen resumenTicket(TicketMantenimiento ticket) {
    var detalle = ticket.getDetalleInforme();
    var informe = detalle.getInformeRevision();
    var habitacion = informe.getHabitacion();
    var item = detalle.getItem();
    boolean editable = PoliticaAccionesLimpieza.pendiente(informe);
    var actual = sesion.actual();
    var id = ticket.getIdTicket();
    return new TicketResumen(id, "TKT-" + id, informe.getIdInformeRevision(), detalle.getIdDetalleInforme(),
        habitacion.getIdHabitacion(), habitacion.getNumeroCuarto(), item.getIdItem(), item.getNombreItem(),
        ticket.getTipoAccion(), ticket.getEstadoTicket(), ticket.getFechaGeneracion(),
        editable && PoliticaAccionesLimpieza.permite(actual, "EDITAR_TICKET"),
        editable && PoliticaAccionesLimpieza.permite(actual, "ELIMINAR_TICKET"));
  }

  private TicketDetalle detalleTicket(TicketMantenimiento ticket) {
    var empleado = ticket.getEmpleado();
    return new TicketDetalle(resumenTicket(ticket), empleado == null ? null : empleado.getIdEmpleado(),
        ticket.getFechaResolucion(), ticket.getObservacionTecnica(), ticket.getCostoReparacion(),
        evidencia(ticket.getEvidenciaRuta(), ticket.getEvidenciaSha256()));
  }

  private Responsable responsable(Usuario usuario) {
    return new Responsable(usuario.getIdUsuario(), usuario.getUsername());
  }

  private Evidencia evidencia(String ruta, String sha) {
    return ruta == null && sha == null ? null : new Evidencia(ruta, sha == null ? null : sha.strip());
  }

  private static LocalDateTime desde(LocalDate fecha) {
    return fecha == null ? null : fecha.atStartOfDay();
  }

  private static LocalDateTime hasta(LocalDate fecha) {
    return fecha == null ? null : fecha.plusDays(1).atStartOfDay();
  }

  private <T> PaginaResponseDTO<T> paginar(Page<T> pagina) {
    return new PaginaResponseDTO<>(pagina.getContent(), pagina.getNumber(), pagina.getSize(),
        pagina.getTotalElements(), pagina.getTotalPages(), pagina.isLast());
  }

  private RecursoNoEncontradoException noExiste(String recurso) {
    return new RecursoNoEncontradoException(recurso + " no existe");
  }
}
