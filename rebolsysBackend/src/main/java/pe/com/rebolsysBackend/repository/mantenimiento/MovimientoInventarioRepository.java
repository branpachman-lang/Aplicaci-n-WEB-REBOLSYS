package pe.com.rebolsysBackend.repository.mantenimiento;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import pe.com.rebolsysBackend.entity.*;

public interface MovimientoInventarioRepository extends JpaRepository<MovimientoInventario, Long> {
  boolean existsByHabitacionIdHabitacion(Long id);
  Optional<MovimientoInventario> findByClaveOperacion(String clave);

  List<MovimientoInventario> findByProductoIdProductoOrderByIdMovimientoDesc(Long id);

  List<MovimientoInventario> findByItemIdItemOrderByIdMovimientoDesc(Long id);

  Optional<MovimientoInventario> findFirstByItemIdItemOrderByIdMovimientoDesc(Long id);
}
