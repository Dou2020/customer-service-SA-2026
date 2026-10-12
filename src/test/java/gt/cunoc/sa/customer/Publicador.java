package gt.cunoc.sa.customer;

import java.time.Instant;
import java.util.UUID;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import gt.cunoc.sa.customer.infrastructure.messaging.Sobre;
import gt.cunoc.sa.customer.infrastructure.messaging.TopologiaRabbit;

/**
 * Publica en transporte.events un sobre como lo haría identity, trip o sales, para probar los
 * manejadores de punta a punta: la cola, los bindings, el inbox, los reintentos y la DLQ.
 */
@TestComponent
public class Publicador {

    private final RabbitTemplate rabbit;
    private final AmqpAdmin admin;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;

    public Publicador(RabbitTemplate rabbit, AmqpAdmin admin, ObjectMapper json, JdbcTemplate jdbc) {
        this.rabbit = rabbit;
        this.admin = admin;
        this.json = json;
        this.jdbc = jdbc;
    }

    /** @return el eventId, para esperar a que el inbox lo registre */
    public UUID publicar(String tipo, String aggregateId, long version, Object payload) {
        return publicar(UUID.randomUUID(), tipo, aggregateId, version, payload);
    }

    public UUID publicar(UUID eventId, String tipo, String aggregateId, long version, Object payload) {
        Sobre sobre = new Sobre(eventId, tipo, 1, aggregateId, version, Instant.now(), "prueba",
                "prueba-" + eventId, json.valueToTree(payload));
        MessageProperties propiedades = new MessageProperties();
        propiedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        try {
            rabbit.send(TopologiaRabbit.EXCHANGE_EVENTOS, tipo, new Message(json.writeValueAsBytes(sobre), propiedades));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return eventId;
    }

    /** El inbox lo registró: el efecto ya está confirmado (misma transacción). */
    public boolean procesado(UUID eventId) {
        return jdbc.queryForObject("select count(*) from processed_event where event_id = ?", Integer.class,
                eventId) > 0;
    }

    public int mensajesEnDlq() {
        return ((Number) admin.getQueueProperties(TopologiaRabbit.COLA_DLQ).get(RabbitAdmin.QUEUE_MESSAGE_COUNT))
                .intValue();
    }
}
