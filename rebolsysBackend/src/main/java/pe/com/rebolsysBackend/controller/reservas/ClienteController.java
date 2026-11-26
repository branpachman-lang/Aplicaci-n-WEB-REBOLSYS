package pe.com.rebolsysBackend.controller.reservas;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.com.rebolsysBackend.dto.mantenimiento.ClienteResumenResponseDTO;
import pe.com.rebolsysBackend.dto.mantenimiento.ClienteDetalleDTO;
import pe.com.rebolsysBackend.dto.mantenimiento.ClienteRegistroRequestDTO;
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

    @PreAuthorize("hasRole('GERENTE') or hasAuthority('CLIENTES:VER')")
    @GetMapping("/titulares")
    public List<ClienteResumenResponseDTO> buscarTitulares(
        @RequestParam boolean empresa,
        @RequestParam(required = false) @Positive Long tipoDocumento,
        @RequestParam(required = false) @Size(max = 50) String numeroDocumento,
        @RequestParam(required = false) @Size(max = 250) String nombre
    ) {
        return clienteService.buscarTitulares(empresa, tipoDocumento, numeroDocumento, nombre);
    }

    @PreAuthorize("hasRole('GERENTE') or hasAuthority('CLIENTES:CREAR')")
    @PostMapping("/titulares")
    @ResponseStatus(HttpStatus.OK)
    public ClienteDetalleDTO registrarTitular(@Valid @RequestBody ClienteRegistroRequestDTO datos) {
        return clienteService.registrarTitular(datos);
    }
}
