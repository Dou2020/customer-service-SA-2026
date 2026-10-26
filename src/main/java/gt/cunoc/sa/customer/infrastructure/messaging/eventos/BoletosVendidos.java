package gt.cunoc.sa.customer.infrastructure.messaging.eventos;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import gt.cunoc.sa.customer.domain.Canal;

/**
 * E11, lo publica sales al confirmar una venta, en línea o en mostrador: customer la agrega al
 * historial del cliente. Solo los campos que customer usa; el resto del payload se ignora.
 * aggregateId = ventaId y aggregateVersion = versión de la venta.
 *
 * @param ventaId   id de sales, como texto ("123" o un UUID)
 * @param clienteId el que resolvió S5; si después lo absorbió una cuenta, customer sigue la lápida
 * @param viajeId   id de trip, como texto
 * @param salida    fecha y hora de salida del viaje
 */
public record BoletosVendidos(
        String ventaId,
        UUID clienteId,
        Canal canal,
        String viajeId,
        String origen,
        String destino,
        Instant salida,
        List<Integer> asientos,
        BigDecimal total,
        LocalDate fechaPago) {
}
