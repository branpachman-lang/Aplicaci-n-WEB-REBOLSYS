package pe.com.rebolsysBackend.controller.reservas;

import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.com.rebolsysBackend.dto.mantenimiento.ClienteResumenResponseDTO;
import pe.com.rebolsysBackend.service.service.mantenimiento.ClienteService;

@RestController
@RequestMapping("/api/v1/recepcion/clientes")
public class ClienteController {

    private final ClienteService clienteService;

    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @PreAuthorize("hasRole('GERENTE') or hasAuthority('CLIENTES:VER')")
    @GetMapping
    public ResponseEntity<ClienteResumenResponseDTO> buscar(
        @RequestParam @Positive Long tipoDocumento,
        @RequestParam @NotBlank @Size(max = 50) String numeroDocumento
    ) {
        return ResponseEntity.ok(clienteService.buscar(tipoDocumento, numeroDocumento));
    }
}
