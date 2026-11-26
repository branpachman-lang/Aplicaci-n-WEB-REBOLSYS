package pe.com.rebolsysBackend.repository.caja.cobros;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import pe.com.rebolsysBackend.entity.FormaPago;

public interface FormaPagoRepository extends JpaRepository<FormaPago, Long> {
    List<FormaPago> findByNombrePagoIgnoreCase(String nombre);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FormaPago f where f.idFormaPago = :id")
    Optional<FormaPago> bloquear(Long id);
}
