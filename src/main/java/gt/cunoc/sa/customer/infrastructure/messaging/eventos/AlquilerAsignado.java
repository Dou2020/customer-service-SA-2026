package gt.cunoc.sa.customer.infrastructure.messaging.eventos;

import java.util.UUID;

/**
 * E15, lo publica trip al asignar (o reasignar) bus y chofer a un viaje de alquiler.
 * aggregateId = viajeId y aggregateVersion = versión del viaje: una reasignación vieja que llega
 * tarde no pisa a la nueva. La placa y el nombre se copian porque el cliente no consulta fleet.
 *
 * @param viajeId  id de trip, como texto ("101" o un UUID)
 * @param busId    id de fleet, como texto
 * @param choferId id de fleet, como texto
 */
public record AlquilerAsignado(
        UUID alquilerId,
        String viajeId,
        String busId,
        String placa,
        String choferId,
        String choferNombre) {
}
