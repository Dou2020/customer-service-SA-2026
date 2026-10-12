package gt.cunoc.sa.customer.infrastructure.messaging;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Sobre de cada evento (README, "Sobre de cada evento"). El payload va como JsonNode: cada
 * consumidor lo convierte al record de su evento, y el sobre no depende de ninguno.
 *
 * @param eventId          llave del inbox del consumidor
 * @param type             dominio.agregado.evento; también es la routing key
 * @param aggregateVersion para descartar un evento viejo que llega tarde
 * @param correlationId    el X-Correlation-Id del request que lo originó
 */
public record Sobre(
        UUID eventId,
        String type,
        int schemaVersion,
        String aggregateId,
        long aggregateVersion,
        Instant occurredAt,
        String producer,
        String correlationId,
        JsonNode payload) {
}
