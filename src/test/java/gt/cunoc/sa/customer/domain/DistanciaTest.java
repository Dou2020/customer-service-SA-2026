package gt.cunoc.sa.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class DistanciaTest {

    @Test
    void unGradoDeLatitudSonUnos111Km() {
        Trayecto trayecto = trayecto("0", "0", "1", "0");

        assertThat(Distancia.lineaRectaKm(trayecto)).isCloseTo(111.195, within(0.01));
        assertThat(Distancia.estimadaKm(trayecto, BigDecimal.ONE)).isEqualByComparingTo("111.20");
    }

    @Test
    void deQuetzaltenangoAHuehuetenangoConElFactorDeCarretera() {
        Trayecto trayecto = trayecto("14.834999", "-91.518611", "15.319722", "-91.470833");

        assertThat(Distancia.lineaRectaKm(trayecto)).isCloseTo(54.1, within(0.5));
        BigDecimal estimada = Distancia.estimadaKm(trayecto, new BigDecimal("1.3"));
        assertThat(estimada).isBetween(new BigDecimal("69.7"), new BigDecimal("71.0"));
        assertThat(estimada.scale()).isEqualTo(2);
    }

    @Test
    void elMismoPuntoEsCero() {
        assertThat(Distancia.estimadaKm(trayecto("14.8", "-91.5", "14.8", "-91.5"), new BigDecimal("1.3")))
                .isEqualByComparingTo("0");
    }

    private static Trayecto trayecto(String lat1, String lng1, String lat2, String lng2) {
        return new Trayecto("A", new BigDecimal(lat1), new BigDecimal(lng1), "B", new BigDecimal(lat2),
                new BigDecimal(lng2));
    }
}
