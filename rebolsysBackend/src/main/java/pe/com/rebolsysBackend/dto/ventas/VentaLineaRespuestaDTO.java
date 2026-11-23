package pe.com.rebolsysBackend.dto.ventas;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;

public record VentaLineaRespuestaDTO(
    Long idDetalleVenta,
    Long idProducto,
    String descripcion,
    int cantidad,
    BigDecimal valorUnitario,
    BigDecimal igv,
    BigDecimal subtotal,
    BigDecimal total,
    String estadoLinea) {}
