package pe.com.rebolsysBackend.service.service.accesos;

import pe.com.rebolsysBackend.dto.accesos.AutenticacionResponseDTO;
import pe.com.rebolsysBackend.dto.accesos.SesionActualDTO;
import pe.com.rebolsysBackend.dto.accesos.CambioPasswordRequestDTO;
import pe.com.rebolsysBackend.dto.accesos.LoginRequestDTO;
import pe.com.rebolsysBackend.security.JwtService;

public interface AuthService {
  AutenticacionResponseDTO iniciar(LoginRequestDTO datos);
  SesionActualDTO actual();
  AutenticacionResponseDTO cambiar(CambioPasswordRequestDTO datos, JwtService.Verificado tokenActual);
  void cerrar(JwtService.Verificado tokenActual);
}
