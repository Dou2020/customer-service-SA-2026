package gt.cunoc.sa.customer.api.historial.dto;

import java.util.List;
import java.util.UUID;

import gt.cunoc.sa.customer.api.alquiler.dto.AlquilerResponse;

/**
 * Lo que la cuenta compró y alquiló, en línea y en mostrador, incluido lo anterior a crear la
 * cuenta si se hizo con su NIT (README, "Historial").
 *
 * @param nit null si la cuenta tiene NIT CF
 */
public record HistorialResponse(
        UUID clienteId,
        String nit,
        String nombre,
        List<CompraResponse> boletos,
        List<AlquilerResponse> alquileres) {
}
