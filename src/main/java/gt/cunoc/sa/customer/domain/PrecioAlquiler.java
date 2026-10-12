package gt.cunoc.sa.customer.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Precio estimado de un alquiler (supuesto 8):
 *
 *   distancia × tarifa por km × (2 si es ida y vuelta) + días × cargo diario
 *
 * con las tarifas vigentes a la fecha de la solicitud. Los días cuentan el de salida y el de
 * retorno (salir y volver el mismo día es 1); un viaje solo de ida es 1 día. El precio se guarda
 * en el alquiler, así que cambiar la tarifa después no lo toca.
 */
public final class PrecioAlquiler {

    private PrecioAlquiler() {
    }

    /** @param retorno null si es solo de ida */
    public static int dias(LocalDate salida, LocalDate retorno) {
        return retorno == null ? 1 : (int) ChronoUnit.DAYS.between(salida, retorno) + 1;
    }

    public static BigDecimal estimar(BigDecimal distanciaKm, boolean idaYVuelta, int dias, BigDecimal tarifaKm,
            BigDecimal cargoDia) {
        BigDecimal recorrido = distanciaKm.multiply(tarifaKm).multiply(BigDecimal.valueOf(idaYVuelta ? 2 : 1));
        return recorrido.add(cargoDia.multiply(BigDecimal.valueOf(dias))).setScale(2, RoundingMode.HALF_UP);
    }
}
