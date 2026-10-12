package gt.cunoc.sa.customer;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

/**
 * PostgreSQL y RabbitMQ reales (las mismas imágenes que el compose): Flyway, el
 * ddl-auto=validate, las restricciones que arbitran la cartera y la DLQ se prueban contra los
 * motores de producción, no contra sustitutos en memoria.
 *
 * Las pruebas que usan @PruebaDeIntegracion comparten el contexto de Spring y, con él, un solo
 * juego de contenedores.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import({ Escenario.class, Publicador.class })
public class ContenedoresDePrueba {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:16-alpine");
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbit() {
        return new RabbitMQContainer("rabbitmq:4-management-alpine");
    }
}
