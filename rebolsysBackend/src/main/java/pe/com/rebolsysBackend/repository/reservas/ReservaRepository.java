package pe.com.rebolsysBackend.repository.reservas;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import pe.com.rebolsysBackend.entity.*;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {
  @Override
  @EntityGraph(attributePaths = {"cliente", "usuario"})
  Page<Reserva> findAll(Pageable pageable);
  Optional<Reserva> findByClaveRegistro(String clave);

  interface NombrePerfil {
    Long getIdPerfil();
    String getNombre();
  }

  @Query("""
      select p.idPerfil as idPerfil,
        case when j.idPerfil is not null then j.razonSocial
          else concat(coalesce(n.nombres, 'null'), ' ', coalesce(n.apellidos, 'null')) end as nombre
      from Perfil p
      left join PersonaNatural n on n.idPerfil = p.idPerfil
      left join PersonaJuridica j on j.idPerfil = p.idPerfil
      where p.idPerfil in (select r.cliente.idPerfil from Reserva r where r.idReserva in :ids)
        or p.idPerfil in (select o.huesped.perfil.idPerfil from ReservaOcupante o
          where o.detalleReserva.reserva.idReserva in :ids)
      """)
  List<NombrePerfil> nombresDeReservas(Collection<Long> ids);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from Reserva r where r.idReserva=:id")
  Optional<Reserva> bloquear(@Param("id") Long id);
}
