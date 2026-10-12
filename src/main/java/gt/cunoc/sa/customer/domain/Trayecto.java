package gt.cunoc.sa.customer.domain;

import java.math.BigDecimal;

/** Origen y destino de un alquiler tal como se marcan en el mapa: nombre y coordenadas. */
public record Trayecto(
        String origen,
        BigDecimal origenLat,
        BigDecimal origenLng,
        String destino,
        BigDecimal destinoLat,
        BigDecimal destinoLng) {
}
