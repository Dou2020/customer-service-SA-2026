package gt.cunoc.sa.customer.domain;

/**
 * Un cambio de estado que el ciclo de vida no permite (pagar un alquiler sin confirmar, cancelar
 * uno pagado…). ManejadorErrores la responde como 409 con este mensaje: el dominio no conoce HTTP.
 */
public class TransicionInvalidaException extends RuntimeException {

    public TransicionInvalidaException(String mensaje) {
        super(mensaje);
    }
}
