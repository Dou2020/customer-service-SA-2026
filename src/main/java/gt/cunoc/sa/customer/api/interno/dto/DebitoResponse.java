package gt.cunoc.sa.customer.api.interno.dto;

import java.math.BigDecimal;

import gt.cunoc.sa.customer.domain.MovimientoCarteraEntity;

/** @param saldo el saldo que dejó el débito (el del libro, también en un reintento) */
public record DebitoResponse(
        ResultadoDebito resultado,
        Long movimientoId,
        BigDecimal monto,
        BigDecimal saldo) {

    public static DebitoResponse aplicado(MovimientoCarteraEntity m) {
        return new DebitoResponse(ResultadoDebito.APLICADO, m.getId(), m.getMonto(), m.getSaldoResultante());
    }
}
