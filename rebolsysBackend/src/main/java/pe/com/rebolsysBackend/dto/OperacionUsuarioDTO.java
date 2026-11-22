package pe.com.rebolsysBackend.dto;

import jakarta.validation.constraints.*;
import java.util.*;

public record OperacionUsuarioDTO(@NotNull @Positive Long idUsuario) {}
