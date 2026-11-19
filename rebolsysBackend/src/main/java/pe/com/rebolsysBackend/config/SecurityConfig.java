package pe.com.rebolsysBackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.web.cors.CorsConfigurationSource;
import pe.com.rebolsysBackend.security.*;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
  @Bean
  org.springframework.security.authentication.AuthenticationManager authenticationManager(
      CredencialesAuthenticationProvider proveedor) {
    return new org.springframework.security.authentication.ProviderManager(proveedor);
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http,
      @org.springframework.beans.factory.annotation.Qualifier("corsConfigurationSource") CorsConfigurationSource cors,
      JwtService jwt, UsuarioDetailsService usuarios, PoliticaSeguridadService politica,
      RestAuthenticationEntryPoint autenticacion, RestAccessDeniedHandler acceso) throws Exception {
    http.formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        // La autenticación usa exclusivamente Bearer, nunca cookies.
        .csrf(AbstractHttpConfigurer::disable)
        .cors(config -> config.configurationSource(cors))
        .securityContext(config -> config.securityContextRepository(new NullSecurityContextRepository()))
        .sessionManagement(config -> config.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(config -> config.authenticationEntryPoint(autenticacion).accessDeniedHandler(acceso))
        .authorizeHttpRequests(auth -> {
          auth.requestMatchers("/api/health", "/error", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
              .requestMatchers(HttpMethod.POST, "/api/v1/sesion/iniciar").permitAll();
          auth.requestMatchers("/api/v1/**").authenticated();
          auth.anyRequest().denyAll();
        })
        .addFilterBefore(new JwtAuthenticationFilter(jwt, usuarios, politica, autenticacion),
            UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }
}
