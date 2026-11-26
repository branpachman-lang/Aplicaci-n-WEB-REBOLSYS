package pe.com.rebolsysBackend.dto.reservas;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import pe.com.rebolsysBackend.enums.*;

public record ReservaDetalleRespuestaDTO(
    Long idDetalleReserva,
    Long idHabitacion,
    String numeroCuarto,
    LocalDateTime fechaIngreso,
    LocalDateTime fechaSalida,
    LocalDateTime fechaIngresoReal,
    LocalDateTime fechaSalidaReal,
    BigDecimal costoEstadia,
    EstadoReserva estadoDetalle,
    List<ReservaOcupanteRespuestaDTO> ocupantes,
    @JsonInclude(JsonInclude.Include.NON_NULL) BigDecimal saldoPendiente,
    @JsonInclude(JsonInclude.Include.NON_NULL) Long idPago,
    @JsonInclude(JsonInclude.Include.NON_NULL) Long idComprobante) {

  public ReservaDetalleRespuestaDTO(Long idDetalleReserva, Long idHabitacion, String numeroCuarto,
      LocalDateTime fechaIngreso, LocalDateTime fechaSalida, LocalDateTime fechaIngresoReal,
      LocalDateTime fechaSalidaReal, BigDecimal costoEstadia, EstadoReserva estadoDetalle,
      List<ReservaOcupanteRespuestaDTO> ocupantes) {
    this(idDetalleReserva, idHabitacion, numeroCuarto, fechaIngreso, fechaSalida, fechaIngresoReal,
        fechaSalidaReal, costoEstadia, estadoDetalle, ocupantes, null, null, null);
  }

  public ReservaDetalleRespuestaDTO conLiquidacion(Long idPago, Long idComprobante) {
    return new ReservaDetalleRespuestaDTO(idDetalleReserva, idHabitacion, numeroCuarto,
        fechaIngreso, fechaSalida, fechaIngresoReal, fechaSalidaReal, costoEstadia, estadoDetalle,
        ocupantes, BigDecimal.ZERO, idPago, idComprobante);
  }
}
