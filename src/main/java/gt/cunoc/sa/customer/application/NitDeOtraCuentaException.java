package gt.cunoc.sa.customer.application;

/**
 * El NIT de un PerfilGuardado sigue enlazado a otra cuenta. identity no deja que dos cuentas
 * tengan el mismo NIT, así que solo pasa mientras el cambio de NIT de la otra va en camino: es
 * reintentable (1, 2, 4 y 8 s) y, si no se resuelve, el evento queda en la DLQ (README,
 * "Carreras y su árbitro").
 */
public class NitDeOtraCuentaException extends RuntimeException {

    public NitDeOtraCuentaException(String nit) {
        super("El NIT " + nit + " sigue enlazado a otra cuenta");
    }
}
