package pe.com.rebolsysBackend.service.service.mantenimiento;

import java.util.List;
import pe.com.rebolsysBackend.dto.mantenimiento.ClienteDetalleDTO;
import pe.com.rebolsysBackend.dto.mantenimiento.ClienteRegistroRequestDTO;
import pe.com.rebolsysBackend.dto.mantenimiento.ClienteResumenResponseDTO;
import pe.com.rebolsysBackend.entity.Huesped;
import pe.com.rebolsysBackend.entity.Perfil;

public interface ClienteService {
  ClienteResumenResponseDTO buscar(Long tipoDocumento, String numeroDocumento);

  List<ClienteResumenResponseDTO> buscarTitulares(boolean empresa, Long tipo, String numero, String nombre);

  ClienteDetalleDTO registrarTitular(ClienteRegistroRequestDTO datos);

  ClienteRegistroRequestDTO datos(Perfil perfil);

  Perfil actualizarPersona(Long idPerfil, ClienteRegistroRequestDTO request);

  Perfil guardarPersona(ClienteRegistroRequestDTO request);

  Perfil guardarCliente(ClienteRegistroRequestDTO request);

  Huesped obtenerOCrearHuesped(Perfil perfil);

  String nombre(Perfil perfil);

  pe.com.rebolsysBackend.dto.mantenimiento.ClienteDetalleDTO obtener(Long id);

  pe.com.rebolsysBackend.dto.mantenimiento.ClienteDetalleDTO guardar(
      Long id, pe.com.rebolsysBackend.dto.mantenimiento.ClienteGuardarDTO r);
}
