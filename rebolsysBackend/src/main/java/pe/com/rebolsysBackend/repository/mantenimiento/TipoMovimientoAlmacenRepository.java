package pe.com.rebolsysBackend.repository.mantenimiento;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.com.rebolsysBackend.entity.TipoMovimientoAlmacen;

public interface TipoMovimientoAlmacenRepository extends JpaRepository<TipoMovimientoAlmacen, Long> {
    Optional<TipoMovimientoAlmacen> findByCodigo(String codigo);
}
