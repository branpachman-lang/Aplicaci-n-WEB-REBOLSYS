package pe.com.rebolsysBackend.dto.accesos;

public record AutenticacionResponseDTO(
    String token,
    String tipo,
    long expiraEnSegundos,
    SesionActualDTO sesion) {
  @Override
  public String toString() {
    return "Autenticación Bearer con vencimiento en " + expiraEnSegundos + " segundos";
  }
}
