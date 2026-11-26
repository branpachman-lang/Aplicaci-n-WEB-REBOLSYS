package pe.com.rebolsysBackend.service.impl.comun;

import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import pe.com.rebolsysBackend.config.RequestIdFilter;
import pe.com.rebolsysBackend.dto.accesos.SesionActualDTO;
import pe.com.rebolsysBackend.entity.*;
import pe.com.rebolsysBackend.enums.AccionAuditoria;

@Service
public class AuditoriaOperacionService {
    private static final Logger log = LoggerFactory.getLogger(AuditoriaOperacionService.class);
    private final EntityManager em;
    private final ApplicationEventPublisher eventos;
    private final TransactionTemplate transaccion;

    public AuditoriaOperacionService(EntityManager em, ApplicationEventPublisher eventos,
        PlatformTransactionManager gestorTransacciones) {
        this.em = em;
        this.eventos = eventos;
        this.transaccion = new TransactionTemplate(gestorTransacciones);
        this.transaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public record Evento(SesionActualDTO sesion, String tabla, Long id, String metodo, String ruta, String ip,
        String requestId) {}

    public void publicarPostCommit(SesionActualDTO sesion, String tabla, Long id, String metodo, String ruta, String ip) {
        eventos.publishEvent(new Evento(sesion, tabla, id, metodo, ruta, ip, MDC.get(RequestIdFilter.CLAVE_MDC)));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void alConfirmarOperacion(Evento evento) {
        try {
            transaccion.executeWithoutResult(estado -> registrar(evento));
        } catch (RuntimeException error) {
            log.error("No se pudo registrar la auditoría de {} {} (requestId={})",
                evento.metodo(), evento.ruta(), evento.requestId(), error);
        }
    }

    @Transactional
    public void registrar(SesionActualDTO sesion, String tabla, Long id, String metodo, String ruta, String ip) {
        registrar(new Evento(sesion, tabla, id, metodo, ruta, ip, MDC.get(RequestIdFilter.CLAVE_MDC)));
        em.flush();
    }

    private void registrar(Evento evento) {
        String metodo = evento.metodo();
        String ruta = evento.ruta();
        var a = new AuditoriaGeneral();
        a.setNombreTabla(evento.tabla());
        a.setClaveRegistro("{\"id\":" + evento.id() + "}");
        a.setUsuario(em.getReference(Usuario.class, evento.sesion().idUsuario()));
        boolean alta = "POST".equals(metodo) && (
            ruta.matches("/api/v1/[^/]+(?:/confirmadas)?")
                || ruta.matches("/api/v1/seguridad/(empleados|usuarios|roles)")
                || ruta.matches("/api/v1/caja/turnos/\\d+/movimientos")
                || ruta.matches("/api/v1/limpieza/(habitaciones/\\d+/iniciar|informes/\\d+/items/\\d+/ticket)"));
        a.setAccion("DELETE".equals(metodo) ? AccionAuditoria.DELETE : alta ? AccionAuditoria.INSERT : AccionAuditoria.UPDATE);
        a.setFechaHora(java.time.LocalDateTime.now(java.time.ZoneId.of("America/Lima")));
        String operacion = metodo + " " + ruta;
        a.setDatosNuevos(evento.requestId() == null ? operacion : operacion + " [requestId=" + evento.requestId() + "]");
        a.setIpOrigen(evento.ip());
        em.persist(a);
    }
}
