package gt.cunoc.sa.customer.infrastructure.messaging;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inbox (README, "Manejo de fallos"): se registra en la misma transacción que el efecto del
 * evento. Si el efecto falla, el registro se revierte con él y el reintento lo vuelve a aplicar.
 */
@Component
public class Inbox {

    public static final String CONSUMIDOR = "customer-service";

    private final ProcessedEventRepository repositorio;

    public Inbox(ProcessedEventRepository repositorio) {
        this.repositorio = repositorio;
    }

    /** @return false si el evento ya se aplicó (duplicado del relay o redelivery de RabbitMQ) */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean primeraVez(UUID eventId) {
        return repositorio.registrar(eventId, CONSUMIDOR) == 1;
    }
}
