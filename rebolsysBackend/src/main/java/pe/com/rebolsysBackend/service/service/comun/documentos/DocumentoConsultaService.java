package pe.com.rebolsysBackend.service.service.comun.documentos;

import pe.com.rebolsysBackend.dto.comun.documentos.DocumentoConsultaResponseDTO;

public interface DocumentoConsultaService {
    DocumentoConsultaResponseDTO consultarDni(String numero);
    DocumentoConsultaResponseDTO consultarRuc(String numero);
}
