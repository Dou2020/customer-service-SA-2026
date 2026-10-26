package gt.cunoc.sa.customer.api.cartera.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import gt.cunoc.sa.customer.domain.MovimientoCarteraEntity;
import gt.cunoc.sa.customer.domain.TipoMovimiento;

/**
 * @param fecha        la que eligió el usuario (o la de pago de la venta)
 * @param referenciaId la venta (el id de sales, como texto) o el alquiler que lo originó; null en una recarga
 */
public record MovimientoResponse(
        Long id,
        TipoMovimiento tipo,
        BigDecimal monto,
        LocalDate fecha,
        String referenciaId,
        BigDecimal saldoResultante,
        Instant registradoEn) {

    public static MovimientoResponse de(MovimientoCarteraEntity m) {
        return new MovimientoResponse(m.getId(), m.getTipo(), m.getMonto(), m.getFecha(), m.getReferenciaId(),
                m.getSaldoResultante(), m.getRegistradoEn());
    }
}
