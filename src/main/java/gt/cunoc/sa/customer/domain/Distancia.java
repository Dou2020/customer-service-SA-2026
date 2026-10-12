package gt.cunoc.sa.customer.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Distancia estimada de un alquiler: la línea recta entre los dos puntos del mapa (haversine) por
 * un factor de carretera, porque ningún camino de Guatemala va en línea recta.
 *
 * Se descartó pedir la distancia por carretera a un servicio de rutas (OSRM): la estimación
 * dependería de un servicio externo en plena demo, y el admin de la sucursal confirma o corrige
 * el precio de todos modos (supuesto 8).
 */
public final class Distancia {

    /** Radio medio de la Tierra (IUGG). */
    private static final double RADIO_KM = 6371.0088;

    private Distancia() {
    }

    /** @return km por carretera estimados, con dos decimales (numeric(8,2)) */
    public static BigDecimal estimadaKm(Trayecto trayecto, BigDecimal factorCarretera) {
        return BigDecimal.valueOf(lineaRectaKm(trayecto)).multiply(factorCarretera)
                .setScale(2, RoundingMode.HALF_UP);
    }

    static double lineaRectaKm(Trayecto t) {
        double lat1 = Math.toRadians(t.origenLat().doubleValue());
        double lat2 = Math.toRadians(t.destinoLat().doubleValue());
        double dLat = lat2 - lat1;
        double dLng = Math.toRadians(t.destinoLng().doubleValue() - t.origenLng().doubleValue());
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dLng / 2), 2);
        return 2 * RADIO_KM * Math.asin(Math.min(1, Math.sqrt(a)));
    }
}
