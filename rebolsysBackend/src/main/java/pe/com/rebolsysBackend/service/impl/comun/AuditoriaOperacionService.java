package pe.com.rebolsysBackend.service.impl.comun;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pe.com.rebolsysBackend.dto.accesos.SesionActualDTO;
import pe.com.rebolsysBackend.entity.*;
import pe.com.rebolsysBackend.enums.AccionAuditoria;

@Service
@RequiredArgsConstructor
public class AuditoriaOperacionService {
    private static final Logger log = LoggerFactory.getLogger(AuditoriaOperacionService.class);
    private final EntityManager em;
    private final ApplicationEventPublisher eventos;

    public record Evento(SesionActualDTO sesion, String tabla, Long id, String metodo, String ruta, String ip) {}

    public void publicarPostCommit(SesionActualDTO sesion, String tabla, Long id, String metodo, String ruta, String ip) {
        eventos.publishEvent(new Evento(sesion, tabla, id, metodo, ruta, ip));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void alConfirmarOperacion(Evento evento) {
        try {
            registrar(evento.sesion(), evento.tabla(), evento.id(), evento.metodo(), evento.ruta(), evento.ip());
        } catch (RuntimeException error) {
            log.error("No se pudo registrar la auditoría post-commit de {} {}", evento.metodo(), evento.ruta(), error);
        }
    }

    @Transactional
    public void registrar(SesionActualDTO sesion, String tabla, Long id, String metodo, String ruta, String ip) {
        var a = new AuditoriaGeneral();
        a.setNombreTabla(tabla);
        a.setClaveRegistro("{\"id\":" + id + "}");
        a.setUsuario(em.getReference(Usuario.class, sesion.idUsuario()));
        boolean alta = "POST".equals(metodo) && (
            ruta.matches("/api/v1/[^/]+(?:/confirmadas)?")
                || ruta.matches("/api/v1/seguridad/(empleados|usuarios|roles)")
                || ruta.matches("/api/v1/caja/turnos/\\d+/movimientos")
                || ruta.matches("/api/v1/limpieza/(habitaciones/\\d+/iniciar|informes/\\d+/items/\\d+/ticket)"));
        a.setAccion("DELETE".equals(metodo) ? AccionAuditoria.DELETE : alta ? AccionAuditoria.INSERT : AccionAuditoria.UPDATE);
        a.setFechaHora(java.time.LocalDateTime.now(java.time.ZoneId.of("America/Lima")));
        a.setDatosNuevos(metodo + " " + ruta);
        a.setIpOrigen(ip);
        em.persist(a);
        em.flush();
    }
}
