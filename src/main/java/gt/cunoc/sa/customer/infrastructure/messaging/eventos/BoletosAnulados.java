package gt.cunoc.sa.customer.infrastructure.messaging.eventos;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import gt.cunoc.sa.customer.domain.Canal;

/**
 * E12, lo publica sales al anular una venta (se eliminó el viaje, o se inició mientras se cobraba).
 * Trae la venta completa, como E11, para que el historial quede bien aunque llegue antes que él.
 * customer marca la compra ANULADA y devuelve a la cartera lo que se debitó.
 *
 * @param ventaId id de sales, como texto; también es la clave del débito de S4 que se devuelve
 * @param viajeId id de trip, como texto
 */
public record BoletosAnulados(
        String ventaId,
        UUID clienteId,
        Canal canal,
        String viajeId,
        String origen,
        String destino,
        Instant salida,
        List<Integer> asientos,
        BigDecimal total,
        LocalDate fechaPago,
        String motivo) {
}
