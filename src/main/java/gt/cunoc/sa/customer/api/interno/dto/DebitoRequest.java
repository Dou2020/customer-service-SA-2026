package gt.cunoc.sa.customer.api.interno.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/**
 * S4: el pago de boletos en línea.
 *
 * @param fecha        la fecha de pago que eligió el comprador; hoy si no se indica
 * @param referenciaId la venta (queda en el movimiento)
 */
public record DebitoRequest(
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal monto,
        LocalDate fecha,
        UUID referenciaId) {
}
