package gt.cunoc.sa.customer.infrastructure.messaging;

import java.util.Map;

import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.autoconfigure.amqp.RabbitRetryTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.policy.SimpleRetryPolicy;

/**
 * Los reintentos del listener (spring.rabbitmq.listener.simple.retry: 1, 2, 4 y 8 s) solo
 * sirven contra fallos transitorios. Un evento ilegible o que viola una regla falla igual las
 * cinco veces: se marca no reintentable y va directo a la DLQ.
 */
@Configuration
public class ReintentosConfig {

    @Bean
    RabbitRetryTemplateCustomizer soloReintentarTransitorios(RabbitProperties propiedades) {
        return (destino, plantilla) -> {
            if (destino == RabbitRetryTemplateCustomizer.Target.LISTENER) {
                int intentos = propiedades.getListener().getSimple().getRetry().getMaxAttempts();
                // traverseCauses: la excepción del listener llega envuelta en
                // ListenerExecutionFailedException.
                plantilla.setRetryPolicy(new SimpleRetryPolicy(intentos, Map.of(
                        EventoNoProcesableException.class, false,
                        MessageConversionException.class, false), true, true));
            }
        };
    }
}
