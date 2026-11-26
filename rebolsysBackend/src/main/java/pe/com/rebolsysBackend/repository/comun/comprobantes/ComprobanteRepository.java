package pe.com.rebolsysBackend.repository.comun.comprobantes;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import pe.com.rebolsysBackend.entity.*;

public interface ComprobanteRepository extends JpaRepository<Comprobante, Long> {
  boolean existsByVentaIdVenta(Long id);

  Optional<Comprobante> findByClaveIdempotencia(String clave);

  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from Comprobante c where c.idComprobante=:id")
  Optional<Comprobante> bloquear(@org.springframework.data.repository.query.Param("id") Long id);

  @Query(
      "select c from Comprobante c left join c.venta v left join v.detalleReserva d where"
          + " (c.reserva.idReserva=:id or d.reserva.idReserva=:id) and"
          + " c.estadoComprobante<>pe.com.rebolsysBackend.enums.EstadoComprobante.ANULADO order by"
          + " c.idComprobante")
  List<Comprobante> cuentasReserva(@org.springframework.data.repository.query.Param("id") Long id);

  interface TotalesCuenta {
    Long getIdReserva();
    Long getIdVenta();
    BigDecimal getTotal();
    BigDecimal getPagado();
  }

  @Query("""
      select r.idReserva as idReserva, v.idVenta as idVenta, c.totalPagar as total,
        coalesce(sum(p.monto), 0) as pagado
      from Reserva r, Comprobante c left join c.venta v left join v.detalleReserva d
        left join Pago p on p.comprobante = c and p.verificado = 1
      where r.idReserva in :ids and (c.reserva = r or d.reserva = r)
        and c.estadoComprobante <> pe.com.rebolsysBackend.enums.EstadoComprobante.ANULADO
      group by r.idReserva, c.idComprobante, v.idVenta, c.totalPagar
      order by c.idComprobante
      """)
  List<TotalesCuenta> totalesCuentasReserva(Collection<Long> ids);

  @Query("select c.claveIdempotencia from Comprobante c where c.claveIdempotencia in :claves")
  Set<String> clavesExistentes(Collection<String> claves);
}
