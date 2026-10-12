package gt.cunoc.sa.customer.api.alquiler.dto;

import jakarta.validation.constraints.Size;

/** @param motivo opcional; llega al cliente en el correo de cancelación */
public record CancelacionRequest(@Size(max = 200) String motivo) {
}
