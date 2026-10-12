package gt.cunoc.sa.customer.domain;

/** Ciclo de vida del alquiler (README, "Ciclo de vida de un alquiler"). */
public enum EstadoAlquiler {
    SOLICITADO,
    CONFIRMADO,
    PAGADO,
    ASIGNADO,
    EN_CURSO,
    FINALIZADO,
    RECHAZADO,
    CANCELADO
}
