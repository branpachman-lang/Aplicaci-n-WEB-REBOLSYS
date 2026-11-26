package pe.com.rebolsysBackend.repository.reservas;

import jakarta.persistence.LockModeType;
import java.time.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import pe.com.rebolsysBackend.entity.*;

public interface DetalleReservaRepository extends JpaRepository<DetalleReserva, Long> {
  @Query("""
      select d.idDetalleReserva from DetalleReserva d
      where d.estadoDetalle = pe.com.rebolsysBackend.enums.EstadoReserva.EN_CURSO
        and d.fechaSalida <= :ahora and d.idDetalleReserva > :desde
      order by d.idDetalleReserva
      """)
  List<Long> vencidas(@Param("ahora") LocalDateTime ahora, @Param("desde") Long desde,
      org.springframework.data.domain.Pageable limite);

  List<DetalleReserva> findByReservaIdReservaOrderByIdDetalleReserva(Long id);

  @Query("""
      select d from DetalleReserva d join fetch d.habitacion
      where d.reserva.idReserva in :ids order by d.idDetalleReserva
      """)
  List<DetalleReserva> detallesDeReservas(Collection<Long> ids);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select d from DetalleReserva d where d.idDetalleReserva=:id")
  Optional<DetalleReserva> bloquear(@Param("id") Long id);

  @Query(
      "select count(d) from DetalleReserva d where d.habitacion.idHabitacion=:habitacion and"
          + " ((d.estadoDetalle=pe.com.rebolsysBackend.enums.EstadoReserva.CONFIRMADA and"
          + " d.fechaIngreso<:salida and d.fechaSalida>:ingreso) or"
          + " (d.estadoDetalle=pe.com.rebolsysBackend.enums.EstadoReserva.EN_CURSO and"
          + " d.fechaIngreso<:salida)) and (:excluir is null or d.idDetalleReserva<>:excluir)")
  long superpuestas(
      @Param("habitacion") Long habitacion,
      @Param("ingreso") LocalDateTime ingreso,
      @Param("salida") LocalDateTime salida,
      @Param("excluir") Long excluir);

  @Query(
      "select d from DetalleReserva d join fetch d.habitacion join fetch d.reserva r join fetch r.cliente"
          + " where (d.estadoDetalle=pe.com.rebolsysBackend.enums.EstadoReserva.CONFIRMADA"
          + " and d.fechaIngreso<:salida and d.fechaSalida>:ingreso) or"
          + " (d.estadoDetalle=pe.com.rebolsysBackend.enums.EstadoReserva.EN_CURSO and"
          + " d.fechaIngreso<:salida)")
  List<DetalleReserva> buscarEnPeriodo(
      @Param("ingreso") LocalDateTime ingreso, @Param("salida") LocalDateTime salida);

  @Query("""
      select distinct d.habitacion.idHabitacion from DetalleReserva d
      where d.estadoDetalle = pe.com.rebolsysBackend.enums.EstadoReserva.EN_CURSO
      """)
  Set<Long> habitacionesEnCurso();

  List<DetalleReserva> findByHabitacionIdHabitacion(Long id);

  @Query("""
      select d from DetalleReserva d
      where d.estadoDetalle in (pe.com.rebolsysBackend.enums.EstadoReserva.EN_CURSO,
        pe.com.rebolsysBackend.enums.EstadoReserva.FINALIZADA)
      and (:id is null or d.habitacion.idHabitacion = :id)
      and not exists (
        select x.idDetalleReserva from DetalleReserva x
        where x.habitacion = d.habitacion
        and x.estadoDetalle in (pe.com.rebolsysBackend.enums.EstadoReserva.EN_CURSO,
          pe.com.rebolsysBackend.enums.EstadoReserva.FINALIZADA)
        and (
          (x.estadoDetalle = pe.com.rebolsysBackend.enums.EstadoReserva.EN_CURSO
            and d.estadoDetalle = pe.com.rebolsysBackend.enums.EstadoReserva.FINALIZADA)
          or (x.estadoDetalle = d.estadoDetalle and (
            coalesce(x.fechaSalidaReal, x.fechaSalida) > coalesce(d.fechaSalidaReal, d.fechaSalida)
            or (coalesce(x.fechaSalidaReal, x.fechaSalida) = coalesce(d.fechaSalidaReal, d.fechaSalida)
              and x.idDetalleReserva > d.idDetalleReserva)
          ))
        )
      )
      """)
  List<DetalleReserva> ultimasSalidas(@Param("id") Long idHabitacion);
}
