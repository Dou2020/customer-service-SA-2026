package gt.cunoc.sa.customer.infrastructure.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import gt.cunoc.sa.customer.infrastructure.web.CorrelationIdFilter;

/**
 * Publica la outbox en transporte.events. Entrega at-least-once: si el broker confirma pero el
 * commit que marca las filas falla, el lote se vuelve a publicar y el inbox de cada consumidor
 * descarta el duplicado.
 */
@Component
public class RelayOutbox {

    private static final Logger log = LoggerFactory.getLogger(RelayOutbox.class);

    public static final String PRODUCTOR = "customer-service";
    private static final int LOTE = 50;
    private static final long ESPERA_CONFIRMACION_MS = 5_000;

    private final OutboxRepository repositorio;
    private final RabbitTemplate rabbit;
    private final ObjectMapper json;

    public RelayOutbox(OutboxRepository repositorio, RabbitTemplate rabbit, ObjectMapper json) {
        this.repositorio = repositorio;
        this.rabbit = rabbit;
        this.json = json;
    }

    @Scheduled(fixedDelayString = "${mensajeria.relay.intervalo:1000}")
    @Transactional
    public void publicarPendientes() {
        List<OutboxEvent> pendientes = repositorio.bloquearPendientes(LOTE);
        if (pendientes.isEmpty()) {
            return;
        }
        try {
            // Todo el lote en un canal y una sola espera de confirmaciones: si una falla, no se
            // marca ninguna y la siguiente pasada lo reintenta en el mismo orden.
            rabbit.invoke(canal -> {
                for (OutboxEvent evento : pendientes) {
                    canal.send(TopologiaRabbit.EXCHANGE_EVENTOS, evento.getTipo(), mensaje(evento));
                }
                canal.waitForConfirmsOrDie(ESPERA_CONFIRMACION_MS);
                return null;
            });
        } catch (AmqpException e) {
            // Sin relanzar: no hay nada que revertir, y el scheduler registraría un ERROR por
            // segundo mientras RabbitMQ esté caído.
            log.warn("No se pudo publicar la outbox ({} pendientes): {}", pendientes.size(), e.getMessage());
            return;
        }
        Instant ahora = Instant.now();
        pendientes.forEach(e -> e.marcarPublicado(ahora));
    }

    private Message mensaje(OutboxEvent evento) {
        Sobre sobre;
        byte[] cuerpo;
        try {
            sobre = new Sobre(evento.getEventId(), evento.getTipo(), 1, evento.getAggregateId(),
                    evento.getAggregateVersion(), evento.getOcurridoEn(), PRODUCTOR, evento.getCorrelationId(),
                    json.readTree(evento.getPayload()));
            cuerpo = json.writeValueAsBytes(sobre);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Payload ilegible en la outbox " + evento.getId(), e);
        }
        MessageProperties propiedades = new MessageProperties();
        propiedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        propiedades.setContentEncoding(StandardCharsets.UTF_8.name());
        propiedades.setMessageId(evento.getEventId().toString());
        propiedades.setType(evento.getTipo());
        propiedades.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        if (evento.getCorrelationId() != null) {
            propiedades.setHeader(CorrelationIdFilter.ENCABEZADO, evento.getCorrelationId());
        }
        return new Message(cuerpo, propiedades);
    }
}
