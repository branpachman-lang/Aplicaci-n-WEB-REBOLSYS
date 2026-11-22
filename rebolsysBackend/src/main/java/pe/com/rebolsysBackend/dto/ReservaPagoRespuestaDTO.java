package pe.com.rebolsysBackend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReservaPagoRespuestaDTO(
    Long idPago,
    Long idFormaPago,
    String medioPago,
    Long idTurno,
    BigDecimal monto,
    String numeroOperacion,
    LocalDateTime fechaPago,
    boolean verificado) {}
