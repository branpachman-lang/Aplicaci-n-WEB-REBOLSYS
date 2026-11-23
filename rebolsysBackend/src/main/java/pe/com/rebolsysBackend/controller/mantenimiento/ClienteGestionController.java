package pe.com.rebolsysBackend.controller.mantenimiento;

import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import pe.com.rebolsysBackend.dto.mantenimiento.ClienteDetalleDTO;
import pe.com.rebolsysBackend.dto.mantenimiento.ClienteGuardarDTO;
import pe.com.rebolsysBackend.service.service.mantenimiento.ClienteService;

@RestController
@RequestMapping("/api/v1/clientes")
@RequiredArgsConstructor
public class ClienteGestionController {
  private final ClienteService servicio;

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('CLIENTES:VER')")
  @GetMapping("/{id}")
  public ClienteDetalleDTO obtener(@PathVariable Long id) {
      return servicio.obtener(id);
  }

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('CLIENTES:CREAR')")
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ClienteDetalleDTO crear(@Valid @RequestBody ClienteGuardarDTO r) {
      return servicio.guardar(null, r);
  }

  @PreAuthorize("hasRole('GERENTE') or hasAuthority('CLIENTES:EDITAR')")
  @PutMapping("/{id}")
  public ClienteDetalleDTO editar(@PathVariable Long id, @Valid @RequestBody ClienteGuardarDTO r) {
      return servicio.guardar(id, r);
  }
}
