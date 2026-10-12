package gt.cunoc.sa.customer.api.alquiler.dto;

import java.time.LocalDate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * @param fechaSolicitud las tarifas que se aplican son las vigentes a esta fecha; hoy si no se indica
 * @param fechaRetorno   null si es solo de ida
 */
public record EstimacionRequest(
        @NotNull @Valid TrayectoRequest trayecto,
        LocalDate fechaSolicitud,
        @NotNull LocalDate fechaSalida,
        LocalDate fechaRetorno) {
}
