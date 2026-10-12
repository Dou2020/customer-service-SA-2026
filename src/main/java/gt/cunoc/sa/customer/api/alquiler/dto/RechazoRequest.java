package gt.cunoc.sa.customer.api.alquiler.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param motivo obligatorio: es lo que el cliente ve en su historial */
public record RechazoRequest(@NotBlank @Size(max = 200) String motivo) {
}
