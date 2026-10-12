package gt.cunoc.sa.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/** Supuesto 8: distancia × tarifa por km × (2 si es ida y vuelta) + días × cargo diario. */
class PrecioAlquilerTest {

    private static final BigDecimal KM = new BigDecimal("5.0000");
    private static final BigDecimal DIA = new BigDecimal("500.0000");

    @Test
    void soloIdaEsUnDiaYUnRecorrido() {
        int dias = PrecioAlquiler.dias(LocalDate.of(2026, 10, 10), null);

        assertThat(dias).isEqualTo(1);
        assertThat(PrecioAlquiler.estimar(new BigDecimal("100.00"), false, dias, KM, DIA)).isEqualByComparingTo("1000.00");
    }

    @Test
    void idaYVueltaCobraElRecorridoDosVecesYCadaDiaIncluidoElDeRetorno() {
        int dias = PrecioAlquiler.dias(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 12));

        assertThat(dias).isEqualTo(3);
        assertThat(PrecioAlquiler.estimar(new BigDecimal("100.00"), true, dias, KM, DIA)).isEqualByComparingTo("2500.00");
    }

    @Test
    void volverElMismoDiaEsUnDia() {
        assertThat(PrecioAlquiler.dias(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 10))).isEqualTo(1);
    }

    @Test
    void redondeaAlCentavo() {
        BigDecimal precio = PrecioAlquiler.estimar(new BigDecimal("70.37"), false, 1, new BigDecimal("5.1234"), DIA);

        assertThat(precio).isEqualByComparingTo("860.53");   // 360.533658 + 500
        assertThat(precio.scale()).isEqualTo(2);
    }
}
