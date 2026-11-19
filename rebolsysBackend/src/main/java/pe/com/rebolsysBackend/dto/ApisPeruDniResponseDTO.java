package pe.com.rebolsysBackend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ApisPeruDniResponseDTO(
    String dni,
    String nombres,
    String apellidoPaterno,
    String apellidoMaterno) {}
