package pe.com.rebolsysBackend.dto.comun.documentos;


public record DocumentoConsultaResponseDTO(
    String numeroDocumento,
    String nombres,
    String apellidos,
    String razonSocial,
    String direccionFiscal) {}
