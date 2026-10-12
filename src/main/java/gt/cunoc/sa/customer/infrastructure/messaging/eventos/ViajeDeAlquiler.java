package gt.cunoc.sa.customer.infrastructure.messaging.eventos;

import java.util.UUID;

/**
 * Lo que customer lee de ViajeIniciado (E8), ViajeFinalizado (E9) y ViajeEliminado (E10), que
 * publica trip para todos los viajes. Solo importan los de alquiler: trip pone el alquilerId del
 * AlquilerPagado (E14) que creó el viaje, y en uno REGULAR viene null.
 */
public record ViajeDeAlquiler(
        UUID viajeId,
        String tipo,
        UUID alquilerId) {
}
