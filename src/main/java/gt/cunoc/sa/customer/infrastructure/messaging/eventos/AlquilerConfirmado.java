package gt.cunoc.sa.customer.infrastructure.messaging.eventos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * E13: la sucursal confirmó el precio y el cliente ya puede pagar (notification le avisa).
 * aggregateId = alquilerId y aggregateVersion = versión del alquiler.
 *
 * @param correo null en un cliente de mostrador sin correo: entonces no hay a quién avisar
 */
public record AlquilerConfirmado(
        UUID alquilerId,
        UUID sucursalId,
        String correo,
        String nombre,
        String origen,
        String destino,
        LocalDate fechaSalida,
        LocalDate fechaRetorno,
        BigDecimal precioFinal) {
}
