package gt.cunoc.sa.customer.infrastructure.messaging.eventos;

import java.util.UUID;

/**
 * Lo que customer lee de ViajeIniciado (E8), ViajeFinalizado (E9) y ViajeEliminado (E10), que
 * publica trip para todos los viajes. Solo importan los de alquiler: trip pone el alquilerId del
 * AlquilerPagado (E14) que creó el viaje, y en uno REGULAR viene null.
 *
 * @param viajeId id de trip, como texto ("101" o un UUID)
 */
public record ViajeDeAlquiler(
        String viajeId,
        String tipo,
        UUID alquilerId) {
}
