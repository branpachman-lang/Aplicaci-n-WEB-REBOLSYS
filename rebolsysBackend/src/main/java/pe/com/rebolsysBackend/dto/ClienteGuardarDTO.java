package pe.com.rebolsysBackend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;

public record ClienteGuardarDTO(
    @NotNull
    @Valid ClienteRegistroRequestDTO persona,
    @Size(max = 500)
    String restriccionesMedicas) {}
