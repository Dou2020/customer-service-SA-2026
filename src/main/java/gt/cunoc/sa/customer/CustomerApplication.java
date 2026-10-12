package gt.cunoc.sa.customer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Sin UserDetailsServiceAutoConfiguration: el servicio no autentica con usuario y
 * contraseña de Spring Security (la identidad llega en los X-User-* del gateway), y
 * esa configuración solo crearía un usuario en memoria con contraseña generada.
 *
 * EnableScheduling: el relay de la outbox.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableScheduling
public class CustomerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomerApplication.class, args);
    }
}
