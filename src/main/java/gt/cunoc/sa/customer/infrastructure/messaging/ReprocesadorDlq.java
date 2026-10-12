package gt.cunoc.sa.customer.infrastructure.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.GetResponse;

/**
 * Devuelve a customer.events lo que está en su DLQ, una vez corregida la causa (README,
 * "Manejo de fallos").
 *
 * Usa el canal directamente para confirmar (ack) cada mensaje de la DLQ solo después de que el
 * broker confirmó su reenvío: con receive() + send() el mensaje se perdería si el envío falla.
 * Mueve solo los que había al empezar: uno que vuelve a fallar regresa a la DLQ después de sus
 * reintentos y no se reprocesa en un ciclo sin fin.
 */
@Component
public class ReprocesadorDlq {

    private static final long ESPERA_CONFIRMACION_MS = 5_000;

    private final RabbitTemplate rabbit;

    public ReprocesadorDlq(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    /** @return cuántos mensajes se reencolaron */
    public int reprocesar() {
        Integer movidos = rabbit.execute(canal -> {
            long pendientes = canal.messageCount(TopologiaRabbit.COLA_DLQ);
            int n = 0;
            while (n < pendientes) {
                GetResponse respuesta = canal.basicGet(TopologiaRabbit.COLA_DLQ, false);
                if (respuesta == null) {
                    break;
                }
                // Exchange por defecto: la routing key es el nombre de la cola.
                canal.basicPublish("", TopologiaRabbit.COLA, respuesta.getProps(), respuesta.getBody());
                canal.waitForConfirmsOrDie(ESPERA_CONFIRMACION_MS);
                canal.basicAck(respuesta.getEnvelope().getDeliveryTag(), false);
                n++;
            }
            return n;
        });
        return movidos == null ? 0 : movidos;
    }
}
