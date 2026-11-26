package pe.com.rebolsysBackend.repository.comun.comprobantes;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import pe.com.rebolsysBackend.entity.*;

public interface ComprobanteDetalleRepository extends JpaRepository<ComprobanteDetalle, Long> {
  List<ComprobanteDetalle> findByComprobanteIdComprobanteOrderByNumeroLinea(Long id);

  boolean existsByDetalleVentaVentaIdVenta(Long id);

  boolean existsByComprobanteIdComprobanteAndDetalleVentaIdDetalleVenta(Long cuenta, Long linea);

  List<ComprobanteDetalle> findByDetalleReservaIdDetalleReserva(Long id);

  @Query("""
      select l from ComprobanteDetalle l join fetch l.comprobante c join fetch c.reserva
      where c.reserva.idReserva in :ids
        and l.detalleReserva.estadoDetalle = pe.com.rebolsysBackend.enums.EstadoReserva.CANCELADA
      """)
  List<ComprobanteDetalle> cargosCancelados(Collection<Long> ids);
}
