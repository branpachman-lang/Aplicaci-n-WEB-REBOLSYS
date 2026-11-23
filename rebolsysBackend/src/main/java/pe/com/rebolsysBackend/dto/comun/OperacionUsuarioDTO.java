package pe.com.rebolsysBackend.dto.comun;

import jakarta.validation.constraints.*;
import java.util.*;

public record OperacionUsuarioDTO(@NotNull @Positive Long idUsuario) {}
