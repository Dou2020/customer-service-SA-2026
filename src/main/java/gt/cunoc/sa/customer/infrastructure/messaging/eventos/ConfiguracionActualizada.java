package gt.cunoc.sa.customer.infrastructure.messaging.eventos;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * E2, lo publica identity con cada versión nueva de una tarifa. El tipo va como texto: también
 * llegan las de depreciación (DEPRECIACION_KM), que son de finance y customer descarta.
 */
public record ConfiguracionActualizada(
        long tarifaId,
        String tipo,
        BigDecimal valor,
        LocalDate vigenteDesde) {
}
