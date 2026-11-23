package pe.com.rebolsysBackend.dto.comun.comprobantes;

public record ResultadoProveedorCpeDTO(String xml, String hash, String cdrZip,
    String codigo, String mensaje, Boolean aceptado) {}
