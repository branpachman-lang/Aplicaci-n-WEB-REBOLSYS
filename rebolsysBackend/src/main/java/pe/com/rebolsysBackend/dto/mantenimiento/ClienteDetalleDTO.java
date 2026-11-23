package pe.com.rebolsysBackend.dto.mantenimiento;

import jakarta.validation.constraints.*;
import java.util.*;

public record ClienteDetalleDTO(
    Long idCliente,
    ClienteRegistroRequestDTO persona,
    String restriccionesMedicas) {}
