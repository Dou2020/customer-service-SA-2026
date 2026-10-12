package gt.cunoc.sa.customer.api.alquiler.dto;

import java.time.LocalDate;

/** @param fechaPago la que elige quien paga; hoy si no se indica (README, "Fechas") */
public record PagoRequest(LocalDate fechaPago) {
}
