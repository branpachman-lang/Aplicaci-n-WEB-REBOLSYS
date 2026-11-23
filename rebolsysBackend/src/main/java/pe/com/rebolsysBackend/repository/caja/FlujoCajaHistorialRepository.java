package pe.com.rebolsysBackend.repository.caja;

import org.springframework.data.jpa.domain.Specification;
import pe.com.rebolsysBackend.dto.caja.GastoResumenDTO;
import pe.com.rebolsysBackend.entity.FlujoCaja;

public interface FlujoCajaHistorialRepository {
  GastoResumenDTO resumir(Specification<FlujoCaja> filtro);
}
