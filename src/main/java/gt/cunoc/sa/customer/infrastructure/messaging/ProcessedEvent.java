package gt.cunoc.sa.customer.infrastructure.messaging;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Inbox: se inserta en la misma transacción que el efecto del evento. Si la PK ya
 * existe, el evento es un duplicado (reintento del relay o redelivery) y se descarta.
 */
@Entity
@Table(name = "processed_event")
public class ProcessedEvent {

    @EmbeddedId
    private Clave clave;

    @Column(name = "procesado_en", nullable = false, updatable = false)
    private Instant procesadoEn;

    protected ProcessedEvent() {
    }

    public ProcessedEvent(UUID eventId, String consumidor, Instant procesadoEn) {
        this.clave = new Clave(eventId, consumidor);
        this.procesadoEn = procesadoEn;
    }

    public Clave getClave() {
        return clave;
    }

    public Instant getProcesadoEn() {
        return procesadoEn;
    }

    @Embeddable
    public record Clave(
            @Column(name = "event_id", nullable = false) UUID eventId,
            @Column(nullable = false, length = 60) String consumidor) {
    }
}
