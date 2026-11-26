package pe.com.rebolsysBackend.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "app.reservas.checkout-automatico.habilitado", havingValue = "true")
public class CheckoutAutomaticoConfig {}
