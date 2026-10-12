package gt.cunoc.sa.customer.api.alquiler.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Solicitud en línea: el cliente sale de la cuenta (X-User-Id), nunca del body.
 *
 * @param sucursalId     la que atiende el alquiler, elegida de GET /api/sucursales (identity)
 * @param fechaSolicitud hoy si no se indica (README, "Fechas")
 * @param fechaRetorno   null si es solo de ida
 */
public record SolicitudAlquilerRequest(
        @NotNull UUID sucursalId,
        @NotNull @Valid TrayectoRequest trayecto,
        LocalDate fechaSolicitud,
        @NotNull LocalDate fechaSalida,
        LocalDate fechaRetorno,
        @NotNull @Min(1) @Max(100) Integer pasajeros) {
}
