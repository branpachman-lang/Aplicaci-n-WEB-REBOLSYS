package pe.com.rebolsysBackend.service.impl.reservas;

import static pe.com.rebolsysBackend.service.impl.comun.ReglasPropuesta.*;

import java.time.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.rebolsysBackend.dto.caja.cobros.PagoCuentaDTO;
import pe.com.rebolsysBackend.dto.comun.comprobantes.ComprobanteCuentaDTO;
import pe.com.rebolsysBackend.dto.comun.OperacionUsuarioDTO;
import pe.com.rebolsysBackend.dto.comun.PaginaResponseDTO;
import pe.com.rebolsysBackend.dto.reservas.ReservaDetalleRegistroDTO;
import pe.com.rebolsysBackend.dto.reservas.ReservaDetalleRespuestaDTO;
import pe.com.rebolsysBackend.dto.reservas.ReservaOcupanteRegistroDTO;
import pe.com.rebolsysBackend.dto.reservas.ReservaOcupanteRespuestaDTO;
import pe.com.rebolsysBackend.dto.reservas.ReservaOcupantesGuardarDTO;
import pe.com.rebolsysBackend.dto.reservas.ReservaRegistroRequestDTO;
import pe.com.rebolsysBackend.dto.reservas.ReservaRespuestaDTO;
import pe.com.rebolsysBackend.dto.reservas.ReservaSalidaDTO;
import pe.com.rebolsysBackend.repository.caja.cobros.PagoRepository;
import pe.com.rebolsysBackend.repository.mantenimiento.HabitacionRepository;
import pe.com.rebolsysBackend.repository.mantenimiento.PerfilRepository;
import pe.com.rebolsysBackend.repository.mantenimiento.PersonaJuridicaRepository;
import pe.com.rebolsysBackend.repository.mantenimiento.PersonaNaturalRepository;
import pe.com.rebolsysBackend.repository.reservas.DetalleReservaRepository;
import pe.com.rebolsysBackend.repository.reservas.ListaNegraRepository;
import pe.com.rebolsysBackend.repository.reservas.ReservaOcupanteRepository;
import pe.com.rebolsysBackend.repository.reservas.ReservaRepository;
import pe.com.rebolsysBackend.service.impl.caja.cobros.CuentaReservaGestion;
import pe.com.rebolsysBackend.service.impl.comun.ReglasPropuesta;
import pe.com.rebolsysBackend.service.service.caja.cobros.CuentaCobroService;
import pe.com.rebolsysBackend.service.service.mantenimiento.ClienteService;
import pe.com.rebolsysBackend.service.service.reservas.ReservaService;
import pe.com.rebolsysBackend.entity.*;
import pe.com.rebolsysBackend.enums.*;
import pe.com.rebolsysBackend.exception.*;

@Service
@RequiredArgsConstructor
public class ReservaServiceImpl implements ReservaService {
    private static final ZoneId ZONA_HOSPEDAJE = ZoneId.of("America/Lima");

    private final jakarta.persistence.EntityManager entityManager;
    private final ReservaRepository reservas;
    private final DetalleReservaRepository detalles;
    private final ReservaOcupanteRepository ocupantes;
    private final HabitacionRepository habitaciones;
    private final PerfilRepository clientes;
    private final PersonaNaturalRepository naturales;
    private final PersonaJuridicaRepository juridicas;
    private final ListaNegraRepository listaNegra;
    private final ClienteService personas;
    private final ReglasPropuesta reglas;
    private final CuentaReservaGestion cuenta;
    private final CuentaCobroService cobros;
    private final PagoRepository pagos;
    private final SalidaReservaGestion salida;

    @Override
    @Transactional
    public ReservaRespuestaDTO registrar(ReservaRegistroRequestDTO r) {
        reglas.validar(r);
        var usuario = reglas.bloquearUsuario(r.idUsuario());
        String firma = reglas.firma(r);
        var anterior = reservas.findByClaveRegistro(r.claveRegistro());
        if (anterior.isPresent()) {
            exigir(firma.equals(anterior.get().getFirmaSolicitud()), "La clave de registro ya corresponde a otra solicitud");
            return respuesta(anterior.get());
        }
        var titular = cliente(r.idCliente());
        var ids = r.detalles().stream().map(ReservaDetalleRegistroDTO::idHabitacion).sorted().toList();
        exigir(new HashSet<>(ids).size() == ids.size(), "Cada habitación debe aparecer una sola vez en la reserva");
        Map<Long, Habitacion> bloqueadas = new HashMap<>();
        for (Long id : ids) {
            var h = habitaciones.bloquear(id).orElseThrow(() -> new RecursoNoEncontradoException("La habitación no existe"));
            entityManager.refresh(h);
            bloqueadas.put(id, h);
        }
        // Bloquear también clientes en orden: dos operadores no pueden alojar a la misma persona a la
        // vez.
        var clientesIds = r.detalles().stream()
                .flatMap(d -> d.ocupantes().stream())
                .map(ReservaOcupanteRegistroDTO::idCliente)
                .distinct()
                .sorted()
                .toList();
        for (Long id : clientesIds)
            clientes.bloquearCliente(id).orElseThrow(() -> new RecursoNoEncontradoException("El ocupante no existe"));
        var reserva = new Reserva();
        reserva.setCliente(titular);
        reserva.setUsuario(usuario);
        reserva.setClaveRegistro(r.claveRegistro());
        reserva.setFirmaSolicitud(firma);
        Set<Long> enSolicitud = new HashSet<>();
        for (var s : r.detalles()) {
            exigir(s.fechaSalida().isAfter(s.fechaIngreso()), "La salida debe ser posterior al ingreso");
            exigir(s.fechaSalida().isAfter(ahora()), "La salida de una nueva reserva debe estar en el futuro");
            exigir(!s.fechaIngreso().isBefore(ahora()), "La fecha de ingreso de una nueva reserva no puede estar en el pasado");
            validarTitular(titular, s.fechaIngreso().toLocalDate());
            var h = bloqueadas.get(s.idHabitacion());
            exigir(h.getEstadoHabitacion() == EstadoHabitacion.DISPONIBLE, "La habitación no está disponible físicamente");
            exigir(detalles.superpuestas(h.getIdHabitacion(), s.fechaIngreso(), s.fechaSalida(), null) == 0, "La habitación ya tiene una reserva en ese periodo o una estancia en curso");
            validarOcupantes(s, h);
            for (var o : s.ocupantes())
                exigir(enSolicitud.add(o.idCliente()), "El ocupante aparece en más de una habitación de la solicitud");
            var d = new DetalleReserva();
            d.setHabitacion(h);
            d.setFechaIngreso(s.fechaIngreso());
            d.setFechaSalida(s.fechaSalida());
            var tarifa = h.getPrecioPorNoche() == null ? h.getTipoHabitacion().getPrecioBase() : h.getPrecioPorNoche();
            exigir(tarifa != null && tarifa.signum() > 0, "Configura una tarifa válida para la habitación antes de reservar");
            var duracion = Duration.between(
                s.fechaIngreso().atZone(ZONA_HOSPEDAJE),
                s.fechaSalida().atZone(ZONA_HOSPEDAJE));
            var noches = BigDecimal.valueOf(duracion.getSeconds()).add(BigDecimal.valueOf(duracion.getNano(), 9))
                .divide(BigDecimal.valueOf(86400), 0, RoundingMode.CEILING);
            var costo = tarifa.multiply(noches).setScale(2, RoundingMode.HALF_UP);
            exigir(costo.compareTo(s.costoEstadia()) == 0,
                "La tarifa o las fechas cambiaron; actualiza el importe de la habitación antes de pagar");
            d.setCostoEstadia(costo);
            d.setEstadoDetalle(EstadoReserva.CONFIRMADA);
            reserva.agregarDetalle(d);
        }
        reservas.saveAndFlush(reserva);
        for (int i = 0; i < r.detalles().size(); i++)
            guardarOcupantes(reserva.getDetalles().get(i), r.detalles().get(i).ocupantes());
        if (r.cobro() != null) {
            var turno = cuenta.turnoOperador(r.idUsuario());
            var c = cuenta.principal(reserva, turno);
            cobros.pagar(c, new PagoCuentaDTO("COBRO-INMEDIATO-" + c.getIdComprobante(),
                r.idUsuario(), turno.getIdTurno(), r.cobro().idFormaPago(),
                r.cobro().monto(), r.cobro().numeroOperacion()));
        }
        return respuesta(reserva);
    }

    private Perfil cliente(Long id) {
        var c = clientes.findByIdPerfilAndEsCliente(id, 1).orElseThrow(() -> new RecursoNoEncontradoException("El cliente no existe"));
        exigir(!listaNegra.existsByPerfilIdPerfil(c.getIdPerfil()), "La persona está registrada en la lista negra");
        return c;
    }

    private void validarTitular(Perfil c, LocalDate ingreso) {
        if (naturales.existsById(c.getIdPerfil()))
            exigir(edad(c, ingreso) >= 18, "El titular de la reserva debe ser mayor de edad");
        else
            exigir(
                    juridicas.existsById(c.getIdPerfil()),
                    "El titular debe tener identidad registrada");
    }

    private int edad(Perfil c, LocalDate fecha) {
        var n = naturales.findById(c.getIdPerfil()).orElseThrow(() -> new ReglaNegocioException("Los ocupantes deben ser personas naturales"));
        exigir(
                n.getFechaNacimiento() != null && !n.getFechaNacimiento().isAfter(fecha),
                "Se requiere una fecha de nacimiento válida del ocupante");
        return Period.between(n.getFechaNacimiento(), fecha).getYears();
    }

    private void validarOcupantes(ReservaDetalleRegistroDTO d, Habitacion h) {
        validarOcupantes(d, h, null);
    }

    private void validarOcupantes(ReservaDetalleRegistroDTO d, Habitacion h, Long excluir) {
        Map<Long, Integer> edades = new HashMap<>();
        for (var o : d.ocupantes()) {
            exigir(
                    !edades.containsKey(o.idCliente()),
                    "Un cliente no puede ocupar dos plazas en la misma habitación");
            edades.put(o.idCliente(), edad(cliente(o.idCliente()), d.fechaIngreso().toLocalDate()));
            exigir(
                    ocupantes.superposicionesCliente(
                            o.idCliente(), d.fechaIngreso(), d.fechaSalida(), excluir)
                            == 0,
                    "El ocupante ya está registrado en otra estancia durante ese periodo");
        }
        exigir(
                d.ocupantes().size() <= h.getCapacidadPersonas(),
                "Se supera la capacidad de personas de la habitación");
        long camas = d.ocupantes().stream().filter(o -> o.ocupaPlazaCama()).count();
        exigir(camas <= h.getPlazasCama(), "Se superan las plazas de cama de la habitación");
        exigir(
                d.ocupantes().size() - camas <= h.getMaxMenoresSinCama(),
                "Se supera el límite de menores sin cama");
        for (var o : d.ocupantes()) {
            int edad = edades.get(o.idCliente());
            if (!o.ocupaPlazaCama())
                exigir(edad < 7, "Solo un menor de siete años puede alojarse sin plaza de cama");
            if (edad < 18) {
                exigir(
                        o.idClienteResponsable() != null
                                && !o.idCliente().equals(o.idClienteResponsable())
                                && o.parentescoResponsable() != null,
                        "Todo menor necesita un responsable y parentesco");
                exigir(
                        edades.getOrDefault(o.idClienteResponsable(), -1) >= 18,
                        "El responsable debe ser adulto y ocupar la misma habitación");
            } else
                exigir(
                        o.idClienteResponsable() == null && o.parentescoResponsable() == null,
                        "Un ocupante adulto no necesita responsable");
        }
    }

    private void guardarOcupantes(DetalleReserva d, List<ReservaOcupanteRegistroDTO> solicitudes) {
        Map<Long, ReservaOcupante> porCliente = new HashMap<>();
        // Insertar responsables primero; el CHECK exige guardar completos los vínculos del menor.
        for (var r :
                solicitudes.stream()
                        .sorted(Comparator.comparing(o -> o.idClienteResponsable() != null))
                        .toList()) {
            var o = new ReservaOcupante();
            o.setDetalleReserva(d);
            o.setHuesped(personas.obtenerOCrearHuesped(cliente(r.idCliente())));
            o.setOcupaPlazaCama(r.ocupaPlazaCama() ? 1 : 0);
            if (r.idClienteResponsable() != null) {
                o.setIdOcupanteResponsable(porCliente.get(r.idClienteResponsable()).getIdReservaOcupante());
                o.setParentescoResponsable(r.parentescoResponsable());
            }
            ocupantes.saveAndFlush(o);
            porCliente.put(r.idCliente(), o);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ReservaRespuestaDTO obtener(Long id) {
        return respuesta(
                reservas
                        .findById(id)
                        .orElseThrow(() -> new RecursoNoEncontradoException("La reserva no existe")));
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaResponseDTO<ReservaRespuestaDTO> listar(int pagina, int tamanio) {
        var p = reservas.findAll(PageRequest.of(pagina, tamanio, Sort.by("idReserva").descending()));
        var listaReservas = p.getContent();
        if (listaReservas.isEmpty()) {
            return new PaginaResponseDTO<>(List.of(), pagina, tamanio, p.getTotalElements(), p.getTotalPages(), p.isLast());
        }
        var reservaIds = listaReservas.stream().map(Reserva::getIdReserva).toList();
        var detallesPorReserva = detalles.findConHabitacionPorReservas(reservaIds).stream()
            .collect(java.util.stream.Collectors.groupingBy(d -> d.getReserva().getIdReserva()));
        var detalleIds = detallesPorReserva.values().stream()
            .flatMap(List::stream).map(DetalleReserva::getIdDetalleReserva).toList();
        var ocupantesPorDetalle = detalleIds.isEmpty()
            ? Map.<Long, List<ReservaOcupante>>of()
            : ocupantes.findConHuespedPorDetalles(detalleIds).stream()
                .collect(java.util.stream.Collectors.groupingBy(o -> o.getDetalleReserva().getIdDetalleReserva()));
        var perfilIds = new HashSet<Long>();
        listaReservas.forEach(r -> perfilIds.add(r.getCliente().getIdPerfil()));
        ocupantesPorDetalle.values().stream().flatMap(List::stream)
            .forEach(o -> perfilIds.add(o.getHuesped().getPerfil().getIdPerfil()));
        var nombres = nombresPorPerfil(perfilIds);
        var contenido = listaReservas.stream()
            .map(r -> respuesta(r, detallesPorReserva.getOrDefault(r.getIdReserva(), List.of()),
                ocupantesPorDetalle, nombres))
            .toList();
        return new PaginaResponseDTO<>(
                contenido,
                pagina,
                tamanio,
                p.getTotalElements(),
                p.getTotalPages(),
                p.isLast());
    }

    @Override
    @Transactional
    public ReservaDetalleRespuestaDTO cambiarEstado(Long id, String accion, OperacionUsuarioDTO r) {
        reglas.validar(r);
        reglas.usuario(r.idUsuario());
        var d = bloquearDetalle(id);
        var h = d.getHabitacion();
        var ahora = ahora();
        switch (accion) {
            case "ingreso" -> {
                if (d.getEstadoDetalle() == EstadoReserva.EN_CURSO) return respuestaDetalle(d);
                exigir(
                        d.getEstadoDetalle() == EstadoReserva.CONFIRMADA,
                        "Solo puede ingresar una estancia confirmada");
                var registrados =
                        ocupantes.findByDetalleReservaIdDetalleReservaOrderByIdReservaOcupante(id);
                exigir(!registrados.isEmpty(), "Completa los ocupantes antes del ingreso");
                Map<Long, Long> clientesPorOcupante = new HashMap<>();
                registrados.forEach(
                        o -> clientesPorOcupante.put(o.getIdReservaOcupante(), o.getHuesped().getPerfil().getIdPerfil()));
                var lista = registrados.stream().map(o -> new ReservaOcupanteRegistroDTO(o.getHuesped().getPerfil().getIdPerfil(),
                                                        clientesPorOcupante.get(o.getIdOcupanteResponsable()),
                                                        o.getParentescoResponsable(),
                                                        o.getOcupaPlazaCama() == 1)).toList();
                validarOcupantes(
                        new ReservaDetalleRegistroDTO(
                                h.getIdHabitacion(), ahora, d.getFechaSalida(), d.getCostoEstadia(), lista),
                        h,
                        id);
                exigir(
                        !ahora.isBefore(d.getFechaIngreso()) && ahora.isBefore(d.getFechaSalida()),
                        "El ingreso debe estar dentro del periodo reservado");
                exigir(
                        h.getEstadoHabitacion() == EstadoHabitacion.DISPONIBLE,
                        "La habitación debe estar disponible");
                exigir(
                        detalles.superpuestas(h.getIdHabitacion(), ahora, d.getFechaSalida(), id) == 0,
                        "Otra estancia impide el ingreso");
                d.setFechaIngresoReal(ahora);
                d.setEstadoDetalle(EstadoReserva.EN_CURSO);
                h.setEstadoHabitacion(EstadoHabitacion.OCUPADO);
            }
            case "salida" -> {
                if (d.getEstadoDetalle() == EstadoReserva.FINALIZADA) return respuestaDetalle(d);
                salida.finalizar(d, ahora);
            }
            case "cancelacion" -> {
                if (d.getEstadoDetalle() == EstadoReserva.CANCELADA) return respuestaDetalle(d);
                exigir(
                        d.getEstadoDetalle() == EstadoReserva.CONFIRMADA,
                        "Solo puede cancelarse una estancia sin ingreso");
                cuenta.cancelarDetalle(d);
                d.setEstadoDetalle(EstadoReserva.CANCELADA);
            }
            default -> throw new ReglaNegocioException("La acción solicitada no es válida");
        }
        detalles.flush();
        return respuestaDetalle(d);
    }

    @Override
    @Transactional
    public ReservaDetalleRespuestaDTO registrarSalida(Long id, ReservaSalidaDTO r) {
        reglas.validar(r);
        reglas.usuario(r.idUsuario());
        boolean financiera = r.cobro() != null || r.comprobante() != null;
        exigir(!financiera || r.claveOperacion() != null, "Indica la clave de operación del check-out");
        var detalle = bloquearDetalle(id);
        var firma = r.claveOperacion() == null ? null : reglas.firma(r);
        if (detalle.getEstadoDetalle() == EstadoReserva.FINALIZADA && !financiera && firma == null)
            return respuestaDetalle(detalle);
        if (detalle.getClaveSalida() != null) {
            exigir(Objects.equals(detalle.getClaveSalida(), r.claveOperacion())
                && Objects.equals(detalle.getFirmaSalida(), firma)
                && Objects.equals(detalle.getIdUsuarioSalida(), r.idUsuario()),
                "La clave de salida o sus datos no coinciden con el check-out registrado");
            return respuestaDetalle(detalle).conLiquidacion(detalle.getIdPagoSalida(), detalle.getIdComprobanteSalida());
        }
        exigir(r.claveOperacion() == null || !detalles.existsByClaveSalida(r.claveOperacion()),
            "La clave de salida ya fue usada en otra estancia");
        exigir(detalle.getEstadoDetalle() == EstadoReserva.EN_CURSO, "Solo puede salir una estancia en curso");
        var reserva = detalle.getReserva();
        var turno = financiera ? cuenta.turnoOperador(r.idUsuario()) : null;
        exigir(r.idTurno() == null || (turno != null && turno.getIdTurno().equals(r.idTurno())),
            "El turno debe pertenecer a la sesión actual");
        Long idPago = null;
        Long idComprobante = null;
        if (r.cobro() != null) {
            var c = cuenta.principal(reserva, turno);
            exigir(r.cobro().monto().signum() > 0 && cuenta.saldo(c).compareTo(r.cobro().monto()) == 0,
                "El importe del check-out debe cubrir exactamente el saldo vigente de la cuenta principal");
            var clavePago = hash("SALIDA-RESERVA:" + r.claveOperacion());
            cobros.pagar(c, new PagoCuentaDTO(clavePago, r.idUsuario(), turno.getIdTurno(),
                r.cobro().idFormaPago(), r.cobro().monto(), r.cobro().numeroOperacion()));
            idPago = pagos.findByClaveIdempotencia(clavePago).orElseThrow().getIdPago();
        }
        salida.finalizar(detalle, ahora().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        if (r.comprobante() != null) {
            cuenta.validarEmisionFinal(reserva);
            var c = cuenta.principal(reserva, turno);
            idComprobante = cobros.emitir(c, new ComprobanteCuentaDTO(r.idUsuario(), turno.getIdTurno(),
                r.comprobante().tipo(), r.comprobante().idClienteReceptor())).getIdComprobante();
        }
        if (firma != null) {
            detalle.setClaveSalida(r.claveOperacion());
            detalle.setFirmaSalida(firma);
            detalle.setIdUsuarioSalida(r.idUsuario());
            detalle.setIdPagoSalida(idPago);
            detalle.setIdComprobanteSalida(idComprobante);
        }
        detalles.flush();
        return respuestaDetalle(detalle).conLiquidacion(idPago, idComprobante);
    }

    private DetalleReserva bloquearDetalle(Long id) {
        var actual = detalles.findById(id)
            .orElseThrow(() -> new RecursoNoEncontradoException("La estancia no existe"));
        cuenta.bloquear(actual.getReserva().getIdReserva());
        var habitacion = habitaciones.bloquear(actual.getHabitacion().getIdHabitacion()).orElseThrow();
        entityManager.refresh(habitacion);
        var detalle = detalles.bloquear(id).orElseThrow();
        entityManager.refresh(detalle);
        return detalle;
    }

    @Override
    @Transactional
    public ReservaDetalleRespuestaDTO guardarOcupantes(Long id, ReservaOcupantesGuardarDTO r) {
        reglas.validar(r);
        reglas.usuario(r.idUsuario());
        var previo =
                detalles
                        .findById(id)
                        .orElseThrow(() -> new RecursoNoEncontradoException("La estancia no existe"));
        reservas.bloquear(previo.getReserva().getIdReserva()).orElseThrow();
        var h = habitaciones.bloquear(previo.getHabitacion().getIdHabitacion()).orElseThrow();
        entityManager.refresh(h);
        var d = detalles.bloquear(id).orElseThrow();
        entityManager.refresh(d);
        exigir(
                d.getEstadoDetalle() == EstadoReserva.CONFIRMADA,
                "Los ocupantes solo se pueden completar o corregir antes del ingreso");
        for (var clienteId :
                r.ocupantes().stream()
                        .map(ReservaOcupanteRegistroDTO::idCliente)
                        .distinct()
                        .sorted()
                        .toList())
            clientes.bloquearCliente(clienteId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("El ocupante no existe"));
        validarOcupantes(
                new ReservaDetalleRegistroDTO(
                        h.getIdHabitacion(),
                        d.getFechaIngreso(),
                        d.getFechaSalida(),
                        d.getCostoEstadia(),
                        r.ocupantes()),
                h,
                id);
        ocupantes.eliminarDeDetalle(id);
        ocupantes.flush();
        guardarOcupantes(d, r.ocupantes());
        return respuestaDetalle(d);
    }

    private Map<Long, String> nombresPorPerfil(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<Long, String> resultado = new HashMap<>();
        naturales.findAllById(ids).forEach(n -> resultado.put(n.getIdPerfil(), n.getNombres() + " " + n.getApellidos()));
        juridicas.findAllById(ids).forEach(j -> resultado.put(j.getIdPerfil(), j.getRazonSocial()));
        return resultado;
    }

    private ReservaRespuestaDTO respuesta(
            Reserva r,
            List<DetalleReserva> detallesReserva,
            Map<Long, List<ReservaOcupante>> ocupantesPorDetalle,
            Map<Long, String> nombres) {
        return new ReservaRespuestaDTO(
                r.getIdReserva(),
                r.getCliente().getIdPerfil(),
                r.getUsuario().getIdUsuario(),
                nombres.getOrDefault(r.getCliente().getIdPerfil(), personas.nombre(r.getCliente())),
                r.getFechaRegistro(),
                r.getClaveRegistro(),
                detallesReserva.stream()
                    .map(d -> respuestaDetalle(d, ocupantesPorDetalle.getOrDefault(d.getIdDetalleReserva(), List.of()), nombres))
                    .toList());
    }

    private ReservaDetalleRespuestaDTO respuestaDetalle(
            DetalleReserva d, List<ReservaOcupante> ocupantesDetalle, Map<Long, String> nombres) {
        return new ReservaDetalleRespuestaDTO(
                d.getIdDetalleReserva(),
                d.getHabitacion().getIdHabitacion(),
                d.getHabitacion().getNumeroCuarto(),
                d.getFechaIngreso(),
                d.getFechaSalida(),
                d.getFechaIngresoReal(),
                d.getFechaSalidaReal(),
                d.getCostoEstadia(),
                d.getEstadoDetalle(),
                ocupantesDetalle.stream()
                    .map(o -> new ReservaOcupanteRespuestaDTO(
                        o.getIdReservaOcupante(),
                        o.getHuesped().getPerfil().getIdPerfil(),
                        nombres.getOrDefault(o.getHuesped().getPerfil().getIdPerfil(),
                            personas.nombre(o.getHuesped().getPerfil())),
                        o.getIdOcupanteResponsable(),
                        o.getParentescoResponsable(),
                        o.getOcupaPlazaCama() == 1))
                    .toList());
    }

    private ReservaRespuestaDTO respuesta(Reserva r) {
        return new ReservaRespuestaDTO(
                r.getIdReserva(),
                r.getCliente().getIdPerfil(),
                r.getUsuario().getIdUsuario(),
                personas.nombre(r.getCliente()),
                r.getFechaRegistro(),
                r.getClaveRegistro(),
                r.getDetalles().stream().map(this::respuestaDetalle).toList());
    }

    private ReservaDetalleRespuestaDTO respuestaDetalle(DetalleReserva d) {
        return new ReservaDetalleRespuestaDTO(
                d.getIdDetalleReserva(),
                d.getHabitacion().getIdHabitacion(),
                d.getHabitacion().getNumeroCuarto(),
                d.getFechaIngreso(),
                d.getFechaSalida(),
                d.getFechaIngresoReal(),
                d.getFechaSalidaReal(),
                d.getCostoEstadia(),
                d.getEstadoDetalle(),
                ocupantes
                        .findByDetalleReservaIdDetalleReservaOrderByIdReservaOcupante(d.getIdDetalleReserva())
                        .stream()
                        .map(
                                o ->
                                        new ReservaOcupanteRespuestaDTO(
                                                o.getIdReservaOcupante(),
                                                o.getHuesped().getPerfil().getIdPerfil(),
                                                personas.nombre(o.getHuesped().getPerfil()),
                                                o.getIdOcupanteResponsable(),
                                                o.getParentescoResponsable(),
                                                o.getOcupaPlazaCama() == 1))
                        .toList());
    }
}
