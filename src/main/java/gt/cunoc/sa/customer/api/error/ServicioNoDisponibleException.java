package gt.cunoc.sa.customer.api.error;

/**
 * Otro servicio no respondió a tiempo o su circuito está abierto (llamadas S1–S7). Sale como 503
 * y la transacción se revierte. customer hoy no llama a nadie (solo recibe S4 y S5), pero
 * ManejadorErrores la traduce igual que en los demás servicios.
 */
public class ServicioNoDisponibleException extends RuntimeException {

    public ServicioNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
