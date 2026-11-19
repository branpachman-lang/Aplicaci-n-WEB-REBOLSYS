package pe.com.rebolsysBackend.config;

import pe.com.rebolsysBackend.security.OperacionUsuarioBodyAdvice;
import java.lang.annotation.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;

/** Ejecuta pruebas de reglas con sus propios actores. El flujo HTTP de seguridad se prueba en AccesoHttpTest. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@AutoConfigureMockMvc(addFilters = false)
@MockitoBean(types = {SesionWebConfig.class, OperacionUsuarioBodyAdvice.class})
@WithMockUser(roles = "GERENTE")
public @interface MockMvcReglasNegocio {}
