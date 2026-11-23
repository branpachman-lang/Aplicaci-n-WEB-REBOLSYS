package pe.com.rebolsysBackend.repository.caja.cobros;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.rebolsysBackend.entity.FormaPago;

public interface FormaPagoRepository extends JpaRepository<FormaPago, Long> {
    List<FormaPago> findByNombrePagoIgnoreCase(String nombre);
}
