package gt.cunoc.sa.customer.infrastructure.messaging.eventos;

import java.util.UUID;

/**
 * E15, lo publica trip al asignar (o reasignar) bus y chofer a un viaje de alquiler.
 * aggregateId = viajeId y aggregateVersion = versión del viaje: una reasignación vieja que llega
 * tarde no pisa a la nueva. La placa y el nombre se copian porque el cliente no consulta fleet.
 */
public record AlquilerAsignado(
        UUID alquilerId,
        UUID viajeId,
        UUID busId,
        String placa,
        UUID choferId,
        String choferNombre) {
}
