package gt.cunoc.sa.customer.api.alquiler.dto;

import java.time.LocalDate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Solicitud en mostrador: la sucursal es la del cajero o admin (del token).
 *
 * @param nit    el del cliente; vacío o "CF" es el cliente genérico
 * @param nombre para un NIT que todavía no es cliente
 */
public record SolicitudMostradorRequest(
        @Size(max = 20) String nit,
        @Size(max = 150) String nombre,
        @NotNull @Valid TrayectoRequest trayecto,
        LocalDate fechaSolicitud,
        @NotNull LocalDate fechaSalida,
        LocalDate fechaRetorno,
        @NotNull @Min(1) @Max(100) Integer pasajeros) {
}
