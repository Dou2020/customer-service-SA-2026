package gt.cunoc.sa.customer.domain;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * "Hoy" del negocio. El contenedor corre en UTC y Guatemala está en UTC−6: con la zona del
 * sistema, entre las 18:00 y la medianoche la fecha por defecto sería la de mañana.
 */
public final class Calendario {

    public static final ZoneId ZONA = ZoneId.of("America/Guatemala");

    private Calendario() {
    }

    public static LocalDate hoy() {
        return LocalDate.now(ZONA);
    }
}
