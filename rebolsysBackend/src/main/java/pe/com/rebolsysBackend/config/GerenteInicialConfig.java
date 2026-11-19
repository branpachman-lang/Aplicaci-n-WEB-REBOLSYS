package pe.com.rebolsysBackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pe.com.rebolsysBackend.security.CredencialesService;

@Configuration
public class GerenteInicialConfig {
  @Bean
  ApplicationRunner gerenteInicial(CredencialesService credenciales,
      @Value("${REBOLSYS_GERENTE_USUARIO:}") String usuario,
      @Value("${REBOLSYS_GERENTE_PASSWORD_TEMPORAL:}") String contrasena) {
    return args -> {
      if (usuario.isBlank() && contrasena.isEmpty()) return;
      if (usuario.isBlank() || contrasena.isEmpty()) {
        throw new IllegalStateException("Configura el usuario y la contraseña temporal de gerencia");
      }
      credenciales.prepararGerente(usuario, contrasena);
    };
  }
}
