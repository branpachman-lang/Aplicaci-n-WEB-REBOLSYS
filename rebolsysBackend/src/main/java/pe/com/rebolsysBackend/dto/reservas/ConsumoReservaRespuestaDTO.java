package pe.com.rebolsysBackend.dto.reservas;

import pe.com.rebolsysBackend.dto.ventas.VentaResponseDTO;

public record ConsumoReservaRespuestaDTO(
    Long idReserva, String numeroReserva, Long idCuenta, VentaResponseDTO venta) {}
