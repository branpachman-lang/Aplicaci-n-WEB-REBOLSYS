package pe.com.rebolsysBackend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ApisPeruRucResponseDTO(
    String ruc,
    String razonSocial,
    String direccion,
    String estado,
    String condicion) {}
