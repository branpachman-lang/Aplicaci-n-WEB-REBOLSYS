package pe.com.rebolsysBackend.service.service;

import pe.com.rebolsysBackend.dto.AutenticacionResponseDTO;
import pe.com.rebolsysBackend.dto.SesionActualDTO;
import pe.com.rebolsysBackend.dto.auth.CambioPasswordRequestDTO;
import pe.com.rebolsysBackend.dto.auth.LoginRequestDTO;
import pe.com.rebolsysBackend.security.JwtService;

public interface AuthService {
  AutenticacionResponseDTO iniciar(LoginRequestDTO datos);
  SesionActualDTO actual();
  AutenticacionResponseDTO cambiar(CambioPasswordRequestDTO datos, JwtService.Verificado tokenActual);
  void cerrar(JwtService.Verificado tokenActual);
}
