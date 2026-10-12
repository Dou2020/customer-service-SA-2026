package gt.cunoc.sa.customer.api.cartera.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import gt.cunoc.sa.customer.domain.MovimientoCarteraEntity;
import gt.cunoc.sa.customer.domain.TipoMovimiento;

/**
 * @param fecha        la que eligió el usuario (o la de pago de la venta)
 * @param referenciaId la venta o el alquiler que lo originó; null en una recarga
 */
public record MovimientoResponse(
        Long id,
        TipoMovimiento tipo,
        BigDecimal monto,
        LocalDate fecha,
        UUID referenciaId,
        BigDecimal saldoResultante,
        Instant registradoEn) {

    public static MovimientoResponse de(MovimientoCarteraEntity m) {
        return new MovimientoResponse(m.getId(), m.getTipo(), m.getMonto(), m.getFecha(), m.getReferenciaId(),
                m.getSaldoResultante(), m.getRegistradoEn());
    }
}
