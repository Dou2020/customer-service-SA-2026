package gt.cunoc.sa.customer;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import gt.cunoc.sa.customer.infrastructure.messaging.Sobre;
import gt.cunoc.sa.customer.infrastructure.messaging.TopologiaRabbit;

/**
 * Una cola temporal enlazada a transporte.events: ve lo que el relay publica, igual que lo vería
 * un consumidor. Como todas las pruebas comparten el broker, cada búsqueda filtra por su agregado.
 */
public class ColaDePrueba implements AutoCloseable {

    private final AmqpAdmin admin;
    private final RabbitTemplate rabbit;
    private final ObjectMapper json;
    private final String nombre;
    private final List<Sobre> recibidos = new ArrayList<>();

    public ColaDePrueba(AmqpAdmin admin, RabbitTemplate rabbit, ObjectMapper json, String patron) {
        this.admin = admin;
        this.rabbit = rabbit;
        this.json = json;
        // Exclusiva pero no auto-delete: una auto-delete se borra en cuanto receive() cancela su
        // consumidor. La borra close() (o RabbitMQ al cerrar la conexión).
        this.nombre = admin.declareQueue(new Queue("prueba." + UUID.randomUUID(), false, true, false));
        admin.declareBinding(new Binding(nombre, Binding.DestinationType.QUEUE, TopologiaRabbit.EXCHANGE_EVENTOS,
                patron, null));
    }

    /** Espera hasta 10 s el primer evento del tipo que cumpla el filtro. */
    public Sobre esperar(String tipo, Predicate<Sobre> filtro) {
        Instant limite = Instant.now().plusSeconds(10);
        while (Instant.now().isBefore(limite)) {
            for (Sobre sobre : recibidos) {
                if (sobre.type().equals(tipo) && filtro.test(sobre)) {
                    return sobre;
                }
            }
            recibir(200);
        }
        throw new AssertionError("No llegó ningún " + tipo + " que cumpla el filtro");
    }

    /** Todo lo que llegue durante la ventana (más lo ya recibido). */
    public List<Sobre> durante(Duration ventana) {
        Instant limite = Instant.now().plus(ventana);
        while (Instant.now().isBefore(limite)) {
            recibir(100);
        }
        return List.copyOf(recibidos);
    }

    private void recibir(long esperaMs) {
        Message mensaje = rabbit.receive(nombre, esperaMs);
        while (mensaje != null) {
            try {
                recibidos.add(json.readValue(mensaje.getBody(), Sobre.class));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            mensaje = rabbit.receive(nombre);
        }
    }

    @Override
    public void close() {
        admin.deleteQueue(nombre);
    }
}
