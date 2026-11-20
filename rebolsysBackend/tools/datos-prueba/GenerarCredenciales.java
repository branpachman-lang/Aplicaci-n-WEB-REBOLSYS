import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Scanner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Ejecutable local: no conecta con Oracle ni incorpora cuentas al arranque de la aplicación. */
class GenerarCredenciales {
  public static void main(String[] args) {
    var aleatorio = new SecureRandom();
    var encoder = new BCryptPasswordEncoder(12);
    try (var entrada = new Scanner(System.in, StandardCharsets.UTF_8)) {
      while (entrada.hasNextLine()) {
        String usuario = entrada.nextLine().strip();
        if (usuario.isEmpty()) continue;
        var bytes = new byte[18];
        aleatorio.nextBytes(bytes);
        String clave = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash = encoder.encode(clave);
        if (!encoder.matches(clave, hash)) throw new IllegalStateException("No se pudo verificar BCrypt");
        System.out.println(usuario + "\t" + clave + "\t" + hash);
      }
    }
  }
}
