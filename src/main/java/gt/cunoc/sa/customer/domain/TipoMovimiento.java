package gt.cunoc.sa.customer.domain;

/**
 * Tipos de fila del libro de la cartera. DEBITO_ANULADO es la lápida del reconciliador de sales:
 * monto 0, la misma Idempotency-Key del débito incierto, y el historial no la muestra.
 */
public enum TipoMovimiento {
    RECARGA,
    DEBITO_BOLETO,
    DEBITO_ALQUILER,
    DEBITO_ANULADO,
    REEMBOLSO
}
