package pe.com.rebolsysBackend.service.service.mantenimiento;

import java.util.List;
import pe.com.rebolsysBackend.dto.comun.PaginaResponseDTO;
import pe.com.rebolsysBackend.dto.mantenimiento.ProductoRequestDTO;
import pe.com.rebolsysBackend.dto.mantenimiento.ProductoResponseDTO;
import pe.com.rebolsysBackend.enums.EstadoProducto;
import pe.com.rebolsysBackend.service.generic.CrudService;

public interface ProductoService extends CrudService<ProductoRequestDTO, ProductoResponseDTO, Long> {
    PaginaResponseDTO<ProductoResponseDTO> listar(int pagina, int tamanio, String buscar, Long categoria, EstadoProducto estado);
    PaginaResponseDTO<ProductoResponseDTO> listar(int pagina, int tamanio, String buscar, Long categoria, EstadoProducto estado, Boolean stockBajo);
    List<ProductoResponseDTO> catalogo();
    ProductoResponseDTO ajustarStock(Long id, pe.com.rebolsysBackend.dto.mantenimiento.AjusteStockDTO r);
    java.util.List<pe.com.rebolsysBackend.dto.mantenimiento.MovimientoRespuestaDTO> movimientos(Long id);
}
