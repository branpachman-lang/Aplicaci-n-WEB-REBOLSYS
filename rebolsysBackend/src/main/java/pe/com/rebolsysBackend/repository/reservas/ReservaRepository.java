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

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from Reserva r where r.idReserva=:id")
  Optional<Reserva> bloquear(@Param("id") Long id);
}
