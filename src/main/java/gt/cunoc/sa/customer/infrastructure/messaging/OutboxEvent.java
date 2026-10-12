package gt.cunoc.sa.customer.infrastructure.messaging;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Fila de la outbox: se inserta en la misma transacción que el cambio de negocio y
 * un relay la publica en transporte.events. El código de mensajería se copia en cada
 * servicio (no es librería compartida) para no acoplar los despliegues.
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, updatable = false)
    private UUID eventId;

    // dominio.agregado.evento: también es la routing key.
    @Column(nullable = false, length = 80, updatable = false)
    private String tipo;

    @Column(name = "aggregate_id", nullable = false, length = 64, updatable = false)
    private String aggregateId;

    @Column(name = "aggregate_version", nullable = false, updatable = false)
    private long aggregateVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb", updatable = false)
    private String payload;

    @Column(name = "correlation_id", length = 64, updatable = false)
    private String correlationId;

    @Column(name = "ocurrido_en", nullable = false, updatable = false)
    private Instant ocurridoEn;

    @Column(name = "publicado_en")
    private Instant publicadoEn;

    protected OutboxEvent() {
    }

    public OutboxEvent(UUID eventId, String tipo, String aggregateId, long aggregateVersion, String payload,
            String correlationId, Instant ocurridoEn) {
        this.eventId = eventId;
        this.tipo = tipo;
        this.aggregateId = aggregateId;
        this.aggregateVersion = aggregateVersion;
        this.payload = payload;
        this.correlationId = correlationId;
        this.ocurridoEn = ocurridoEn;
    }

    public void marcarPublicado(Instant ahora) {
        this.publicadoEn = ahora;
    }

    public Long getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getTipo() {
        return tipo;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public long getAggregateVersion() {
        return aggregateVersion;
    }

    public String getPayload() {
        return payload;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getOcurridoEn() {
        return ocurridoEn;
    }

    public Instant getPublicadoEn() {
        return publicadoEn;
    }
}
