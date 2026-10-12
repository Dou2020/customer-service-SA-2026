package gt.cunoc.sa.customer.infrastructure.messaging;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import gt.cunoc.sa.customer.infrastructure.web.CorrelationIdFilter;

/**
 * Registra un evento en la outbox, dentro de la transacción del cambio de negocio: o se
 * guardan los dos o ninguno. Lo publica después RelayOutbox (README, "Manejo de fallos").
 */
@Component
public class Outbox {

    private final OutboxRepository repositorio;
    private final ObjectMapper json;

    public Outbox(OutboxRepository repositorio, ObjectMapper json) {
        this.repositorio = repositorio;
        this.json = json;
    }

    /** MANDATORY: un evento fuera de una transacción de negocio sería justo el dual write. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(String tipo, String aggregateId, long aggregateVersion, Object payload) {
        try {
            repositorio.save(new OutboxEvent(UUID.randomUUID(), tipo, aggregateId, aggregateVersion,
                    json.writeValueAsString(payload), MDC.get(CorrelationIdFilter.MDC_CLAVE), Instant.now()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el evento " + tipo, e);
        }
    }
}
