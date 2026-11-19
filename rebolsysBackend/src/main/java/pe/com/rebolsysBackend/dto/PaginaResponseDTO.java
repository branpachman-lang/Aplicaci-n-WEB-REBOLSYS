package pe.com.rebolsysBackend.dto;

import java.util.List;

public record PaginaResponseDTO<T>(
    List<T> contenido,
    int pagina,
    int tamanio,
    long totalElementos,
    int totalPaginas,
    boolean ultima) {}
