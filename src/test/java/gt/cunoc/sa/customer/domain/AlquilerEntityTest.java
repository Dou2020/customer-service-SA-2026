package gt.cunoc.sa.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Las transiciones del ciclo de vida (README, "Ciclo de vida de un alquiler"). */
class AlquilerEntityTest {

    @Test
    void confirmarSinPrecioAceptaElEstimadoYSoloSeConfirmaUnaVez() {
        AlquilerEntity alquiler = solicitado();
        alquiler.confirmar(null);

        assertThat(alquiler.getEstado()).isEqualTo(EstadoAlquiler.CONFIRMADO);
        assertThat(alquiler.getPrecioFinal()).isEqualByComparingTo("850.00");
        assertThatThrownBy(() -> alquiler.confirmar(new BigDecimal("1")))
                .isInstanceOf(TransicionInvalidaException.class)
                .hasMessage("Solo se confirma un alquiler solicitado, y este está confirmado");
    }

    @Test
    void noSePagaSinPrecioConfirmadoNiDosVeces() {
        AlquilerEntity alquiler = solicitado();
        assertThatThrownBy(() -> alquiler.pagar(LocalDate.of(2026, 10, 5)))
                .hasMessage("La sucursal todavía no confirma el precio de este alquiler");

        alquiler.confirmar(new BigDecimal("1200.00"));
        alquiler.pagar(LocalDate.of(2026, 10, 5));
        assertThat(alquiler.getFechaPago()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThatThrownBy(() -> alquiler.pagar(LocalDate.of(2026, 10, 6))).hasMessage("Este alquiler ya está pagado");
    }

    @Test
    void despuesDelPagoSoloSeCancelaEliminandoElViaje() {
        AlquilerEntity alquiler = pagado();

        assertThatThrownBy(() -> alquiler.cancelar(null)).isInstanceOf(TransicionInvalidaException.class)
                .hasMessageContaining("eliminando su viaje");
        assertThat(alquiler.cancelarPorViajeEliminado("Viaje eliminado")).isTrue();
        assertThat(alquiler.getEstado()).isEqualTo(EstadoAlquiler.CANCELADO);
        assertThat(alquiler.cancelarPorViajeEliminado("otra vez")).isFalse();
    }

    @Test
    void unaReasignacionViejaNoPisaALaNueva() {
        AlquilerEntity alquiler = pagado();
        UUID viaje = UUID.randomUUID();

        assertThat(alquiler.asignar(viaje, UUID.randomUUID(), "P-111AAA", UUID.randomUUID(), "Uno", 1)).isTrue();
        assertThat(alquiler.getEstado()).isEqualTo(EstadoAlquiler.ASIGNADO);
        assertThat(alquiler.asignar(viaje, UUID.randomUUID(), "P-333CCC", UUID.randomUUID(), "Tres", 3)).isTrue();
        assertThat(alquiler.asignar(viaje, UUID.randomUUID(), "P-222BBB", UUID.randomUUID(), "Dos", 2)).isFalse();
        assertThat(alquiler.getBusPlaca()).isEqualTo("P-333CCC");
    }

    @Test
    void sinPagarNoSeAsigna() {
        AlquilerEntity alquiler = solicitado();
        assertThat(alquiler.asignar(UUID.randomUUID(), UUID.randomUUID(), "P-1", UUID.randomUUID(), "X", 1)).isFalse();
        assertThat(alquiler.getEstado()).isEqualTo(EstadoAlquiler.SOLICITADO);
    }

    @Test
    void losEstadosDelViajeSoloAvanzan() {
        AlquilerEntity alquiler = pagado();
        UUID viaje = UUID.randomUUID();

        // El E9 llegó antes que el E8: el alquiler termina igual.
        assertThat(alquiler.finalizar(viaje)).isTrue();
        assertThat(alquiler.getEstado()).isEqualTo(EstadoAlquiler.FINALIZADO);
        assertThat(alquiler.getViajeId()).isEqualTo(viaje);
        assertThat(alquiler.iniciar(viaje)).isFalse();
        assertThat(alquiler.cancelarPorViajeEliminado("tarde")).isFalse();
        assertThat(alquiler.estaPagado()).isTrue();
    }

    @Test
    void soloSeRechazaLoSolicitado() {
        AlquilerEntity alquiler = solicitado();
        alquiler.rechazar("No hay buses para esa fecha");

        assertThat(alquiler.getEstado()).isEqualTo(EstadoAlquiler.RECHAZADO);
        assertThat(alquiler.getMotivo()).isEqualTo("No hay buses para esa fecha");
        assertThatThrownBy(() -> alquiler.cancelar(null)).hasMessage("El alquiler ya está rechazado");
    }

    private static AlquilerEntity solicitado() {
        return AlquilerEntity.solicitado(UUID.randomUUID(), UUID.randomUUID(), Canal.ONLINE,
                new Trayecto("Xela", new BigDecimal("14.834999"), new BigDecimal("-91.518611"), "Huehue",
                        new BigDecimal("15.319722"), new BigDecimal("-91.470833")),
                new BigDecimal("70.00"), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10), null, 30,
                new BigDecimal("850.00"), Instant.now());
    }

    private static AlquilerEntity pagado() {
        AlquilerEntity alquiler = solicitado();
        alquiler.confirmar(null);
        alquiler.pagar(LocalDate.of(2026, 10, 2));
        return alquiler;
    }
}
