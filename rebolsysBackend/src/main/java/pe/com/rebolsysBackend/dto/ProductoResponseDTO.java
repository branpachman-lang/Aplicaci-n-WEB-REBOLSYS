package pe.com.rebolsysBackend.dto;

import java.math.BigDecimal;
import pe.com.rebolsysBackend.enums.EstadoProducto;

public record ProductoResponseDTO(
    Long idProducto,
    String nombreProducto,
    BigDecimal precioActual,
    Integer stock,
    Integer stockMinimo,
    Long idCategoria,
    EstadoProducto estadoProducto,
    String imagenRuta,
    String categoriaNombre,
    String descripcion) {

    public boolean isStockBajo() {
        return stock != null && stockMinimo != null && stock <= stockMinimo;
    }
}
