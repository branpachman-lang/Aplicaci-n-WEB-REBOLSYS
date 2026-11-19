package pe.com.rebolsysBackend.dto;


public record DocumentoConsultaResponseDTO(
    String numeroDocumento,
    String nombres,
    String apellidos,
    String razonSocial,
    String direccionFiscal) {}
