package gt.cunoc.sa.customer.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Convierte el payload del sobre al record del evento. Un payload que no se puede leer nunca se
 * va a poder leer: va directo a la DLQ, sin gastar reintentos. Los campos que el record no tiene
 * se ignoran (Spring Boot configura el ObjectMapper así): el productor puede agregar campos sin
 * romper a este consumidor.
 */
public final class Payloads {

    private Payloads() {
    }

    public static <T> T leer(ObjectMapper json, Sobre sobre, Class<T> tipo) {
        try {
            return json.treeToValue(sobre.payload(), tipo);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new EventoNoProcesableException("Payload de " + sobre.type() + " ilegible: " + e.getMessage(), e);
        }
    }

    /** Un dato obligatorio que falta: tampoco se arregla reintentando. */
    public static void exigir(boolean condicion, Sobre sobre, String falta) {
        if (!condicion) {
            throw new EventoNoProcesableException(sobre.type() + " sin " + falta);
        }
    }
}
