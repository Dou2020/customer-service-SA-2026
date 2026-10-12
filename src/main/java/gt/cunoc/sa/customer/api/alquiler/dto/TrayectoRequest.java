package gt.cunoc.sa.customer.api.alquiler.dto;

import java.math.BigDecimal;

import gt.cunoc.sa.customer.domain.Trayecto;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Origen y destino marcados en el mapa: un nombre para mostrar y sus coordenadas (6 decimales). */
public record TrayectoRequest(
        @NotBlank @Size(max = 200) String origen,
        @NotNull @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 2, fraction = 6) BigDecimal origenLat,
        @NotNull @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 6) BigDecimal origenLng,
        @NotBlank @Size(max = 200) String destino,
        @NotNull @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 2, fraction = 6) BigDecimal destinoLat,
        @NotNull @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 6) BigDecimal destinoLng) {

    public Trayecto aDominio() {
        return new Trayecto(origen.strip(), origenLat, origenLng, destino.strip(), destinoLat, destinoLng);
    }
}
