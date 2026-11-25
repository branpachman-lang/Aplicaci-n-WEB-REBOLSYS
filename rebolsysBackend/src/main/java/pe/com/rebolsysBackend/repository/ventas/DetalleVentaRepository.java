package pe.com.rebolsysBackend.repository.ventas;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import pe.com.rebolsysBackend.entity.*;

public interface DetalleVentaRepository extends JpaRepository<DetalleVenta, Long> {
  List<DetalleVenta> findByVentaIdVentaOrderByIdDetalleVenta(Long id);

  @Query("select d from DetalleVenta d join fetch d.producto where d.venta.idVenta in :ids order by d.idDetalleVenta")
  List<DetalleVenta> findConProductoPorVentas(@Param("ids") Collection<Long> ids);
}
