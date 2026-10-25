package gt.cunoc.sa.customer.domain;

/** NIT con formato inválido; sale como 422. */
public class NitInvalidoException extends RuntimeException {

    public NitInvalidoException(String mensaje) {
        super(mensaje);
    }
}
