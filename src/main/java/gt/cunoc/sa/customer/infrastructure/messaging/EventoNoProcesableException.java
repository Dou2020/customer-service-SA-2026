package gt.cunoc.sa.customer.infrastructure.messaging;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;

/**
 * Un evento que nunca va a funcionar (payload inválido, regla violada): va directo a la DLQ
 * sin gastar reintentos (ReintentosConfig lo marca como no reintentable). Extiende
 * AmqpRejectAndDontRequeueException para que se rechace sin reencolar aunque el retry esté apagado.
 */
public class EventoNoProcesableException extends AmqpRejectAndDontRequeueException {

    public EventoNoProcesableException(String mensaje) {
        super(mensaje);
    }

    public EventoNoProcesableException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
