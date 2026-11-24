package pe.com.rebolsysBackend.service.impl.limpieza;

import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import pe.com.rebolsysBackend.dto.accesos.SesionActualDTO;
import pe.com.rebolsysBackend.entity.InformeRevision;
import pe.com.rebolsysBackend.enums.EstadoHabitacion;
import pe.com.rebolsysBackend.enums.EstadoInforme;
import pe.com.rebolsysBackend.security.AccesosService;


public final class PoliticaAccionesLimpieza {
  private static final Set<String> MANTENIMIENTO = Set.of(
      "APROBAR_INFORME", "CREAR_TICKET", "EDITAR_TICKET", "ELIMINAR_TICKET");

  private PoliticaAccionesLimpieza() {}

  public static boolean permite(SesionActualDTO actual, String accion) {
    return actual.tiene("LIMPIEZA:" + accion)
        && (!MANTENIMIENTO.contains(accion)
            || actual.roles().contains(AccesosService.MANTENIMIENTO));
  }

  public static void exigirPermiso(SesionActualDTO actual, String accion) {
    if (!permite(actual, accion)) {
      throw new AccessDeniedException("No tiene autorización para la acción de limpieza: " + accion);
    }
  }

  public static boolean pendiente(InformeRevision informe) {
    return informe.getEstadoInforme() == EstadoInforme.POR_APROBAR
        && informe.getHabitacion().getEstadoHabitacion() == EstadoHabitacion.EN_LIMPIEZA;
  }

  public static boolean borradorPropio(InformeRevision informe, SesionActualDTO actual) {
    return informe != null && informe.getEstadoInforme() == EstadoInforme.BORRADOR
        && informe.getHabitacion().getEstadoHabitacion() == EstadoHabitacion.EN_LIMPIEZA
        && informe.getUsuario().getIdUsuario().equals(actual.idUsuario());
  }
}
