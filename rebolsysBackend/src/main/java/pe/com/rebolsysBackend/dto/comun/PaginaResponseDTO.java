package pe.com.rebolsysBackend.dto.comun;

import java.util.List;

public record PaginaResponseDTO<T>(
    List<T> contenido,
    int pagina,
    int tamanio,
    long totalElementos,
    int totalPaginas,
    boolean ultima) {}
