package pe.com.rebolsysBackend.dto.caja;

import pe.com.rebolsysBackend.dto.comun.PaginaResponseDTO;

public record GastoHistorialDTO(
    PaginaResponseDTO<GastoResponseDTO> gastos, GastoResumenDTO resumen) {}
