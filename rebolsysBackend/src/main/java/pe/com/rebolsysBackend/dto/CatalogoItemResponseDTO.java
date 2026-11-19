package pe.com.rebolsysBackend.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"codigoSunat", "id", "nombre"})
public record CatalogoItemResponseDTO(
    Long id,
    String nombre,
    String codigoSunat) {

    public CatalogoItemResponseDTO(Long id, String nombre) {
        this(id, nombre, null);
    }
}
