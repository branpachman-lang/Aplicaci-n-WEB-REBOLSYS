package pe.com.rebolsysBackend.config;

import pe.com.rebolsysBackend.security.PermisosEndpoint;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info =
        @Info(
            title = "REBOLSYS API",
            version = "v1",
            description =
                "Siete casos de uso del hospedaje con cliente unificado: reservas, estados de"
                    + " habitaciones, limpieza, mantenimiento, gastos, ventas y configuración de habitaciones, mobiliario y"
                    + " productos. Reservas asistidas con anticipos, ocupantes al ingreso,"
                    + " consumos, pagos y comprobantes internos con emisión electrónica beta"
                    + " mediante APIsPerú. La integración verifica el ambiente beta; producción bloqueada."))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
  @Bean
  OperationCustomizer proteccionOperativa() {
    return (operacion, handler) -> {
      var acceso = PermisosEndpoint.regla(handler);
      if (acceso != null && !"permitAll()".equals(acceso.value())) {
        operacion.addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
      }
      return operacion;
    };
  }
}
