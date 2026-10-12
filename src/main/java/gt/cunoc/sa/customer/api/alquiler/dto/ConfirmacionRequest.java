package gt.cunoc.sa.customer.api.alquiler.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

/** @param precioFinal el que fija la sucursal; null para aceptar el estimado */
public record ConfirmacionRequest(
        @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal precioFinal) {
}
