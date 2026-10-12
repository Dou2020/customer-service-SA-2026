package gt.cunoc.sa.customer.api.cartera.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/**
 * @param monto quetzales, con centavos; el tope por recarga evita un error de dedo de varios ceros
 * @param fecha la de la recarga; hoy si no se indica (README, "Fechas")
 */
public record RecargaRequest(
        @NotNull @DecimalMin("0.01") @DecimalMax("10000.00") @Digits(integer = 5, fraction = 2) BigDecimal monto,
        LocalDate fecha) {
}
