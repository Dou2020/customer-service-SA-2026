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

    /** El largo de las columnas que guardan ids de fleet, trip y sales (V3). */
    public static final int LARGO_ID = 64;

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

    /**
     * Un id obligatorio de fleet, trip o sales. Llega como texto (su id numérico, "123", o un
     * UUID) y se guarda tal cual, sin interpretarlo; si el productor lo manda como número JSON,
     * Jackson lo lee como el mismo texto. Uno vacío o más largo que la columna no identifica nada
     * y tampoco se arregla reintentando: directo a la DLQ, antes de que la base lo rechace con un
     * error que se reintentaría.
     */
    public static String exigirId(String id, Sobre sobre, String campo) {
        exigir(id != null, sobre, campo);
        if (id.isBlank() || id.length() > LARGO_ID) {
            throw new EventoNoProcesableException(sobre.type() + " con " + campo + " vacío o de más de "
                    + LARGO_ID + " caracteres");
        }
        return id;
    }

    /** Igual que exigirId, para un id que el evento puede no traer: null pasa, vacío no. */
    public static String idOpcional(String id, Sobre sobre, String campo) {
        return id == null ? null : exigirId(id, sobre, campo);
    }
}
