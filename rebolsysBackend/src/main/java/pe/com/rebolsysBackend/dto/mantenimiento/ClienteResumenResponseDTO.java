package pe.com.rebolsysBackend.dto.mantenimiento;


public record ClienteResumenResponseDTO(
    Long idCliente,
    ClienteRegistroRequestDTO datos) {}
