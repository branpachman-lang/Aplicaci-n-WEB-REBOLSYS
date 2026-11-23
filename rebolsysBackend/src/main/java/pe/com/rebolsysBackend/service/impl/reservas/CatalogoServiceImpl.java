package pe.com.rebolsysBackend.service.impl.reservas;

import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.com.rebolsysBackend.dto.reservas.CatalogoItemResponseDTO;
import pe.com.rebolsysBackend.dto.reservas.CatalogosResponseDTO;
import pe.com.rebolsysBackend.dto.caja.TurnoDisponibleDTO;
import pe.com.rebolsysBackend.enums.EstadoUsuario;
import pe.com.rebolsysBackend.repository.caja.TurnoCajaRepository;
import pe.com.rebolsysBackend.repository.accesos.UsuarioRepository;
import pe.com.rebolsysBackend.service.service.reservas.CatalogoService;
import pe.com.rebolsysBackend.service.service.configuracion.TipoHabitacionService;
import pe.com.rebolsysBackend.service.service.mantenimiento.TipoItemService;

@Service
@RequiredArgsConstructor
public class CatalogoServiceImpl implements CatalogoService {
  private final CatalogosEstaticosService referencias;
  private final TipoHabitacionService habitaciones;
  private final TipoItemService items;
  private final UsuarioRepository usuarios;
  private final TurnoCajaRepository turnos;

  @Override
  @Transactional(readOnly = true)
  public CatalogosResponseDTO catalogos() {
    return new CatalogosResponseDTO(
        referencias.documentos(),
        referencias.pagos(),
        StreamSupport.stream(habitaciones.readAll().spliterator(), false)
            .map(t -> new CatalogoItemResponseDTO(t.idTipoHabitacion(), t.nombreTipo())).toList(),
        StreamSupport.stream(items.readAll().spliterator(), false)
            .map(t -> new CatalogoItemResponseDTO(t.idTipoItem(), t.nombreItem())).toList(),
        referencias.gastos(),
        usuarios.findAll().stream()
            .filter(t -> t.getEstadoUsuario() == EstadoUsuario.ACTIVO)
            .map(t -> new CatalogoItemResponseDTO(t.getIdUsuario(), t.getUsername()))
            .toList(),
        turnos.findAll().stream()
            .filter(t -> t.getEstadoSesion() == 1 && t.getFechaCierre() == null)
            .map(
                t ->
                    new TurnoDisponibleDTO(
                        t.getIdTurno(),
                        t.getUsuario().getIdUsuario(),
                        t.getUsuario().getUsername(),
                        t.getFechaApertura()))
            .toList());
  }
}
