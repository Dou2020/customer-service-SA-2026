package gt.cunoc.sa.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import gt.cunoc.sa.customer.PruebaDeIntegracion;
import gt.cunoc.sa.customer.Publicador;
import gt.cunoc.sa.customer.infrastructure.messaging.eventos.TiposEvento;

/** ConfiguracionActualizada (E2) publicado como lo haría identity. */
@PruebaDeIntegracion
class ReplicaTarifasIT {

    private static final Duration ESPERA = Duration.ofSeconds(10);

    @Autowired
    Publicador publicador;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void guardaCadaVersionDeLasTarifasDeAlquilerUnaSolaVez() {
        long tarifaId = ThreadLocalRandom.current().nextLong(1_000_000, 800_000_000);
        String vigente = "2040-0" + (1 + ThreadLocalRandom.current().nextInt(9)) + "-1"
                + ThreadLocalRandom.current().nextInt(10);
        Map<String, Object> tarifa = Map.of("tarifaId", tarifaId, "tipo", "ALQUILER_KM", "valor", "7.2500",
                "vigenteDesde", vigente);

        UUID primero = publicador.publicar(TiposEvento.CONFIGURACION_ACTUALIZADA, "ALQUILER_KM", tarifaId, tarifa);
        // La misma versión otra vez, con otro eventId (identity no la reemite, pero un respaldo restaurado sí).
        UUID segundo = publicador.publicar(TiposEvento.CONFIGURACION_ACTUALIZADA, "ALQUILER_KM", tarifaId, tarifa);
        await().atMost(ESPERA).until(() -> publicador.procesado(primero) && publicador.procesado(segundo));

        assertThat(jdbc.queryForMap("select tipo, valor, vigente_desde::text as desde from tarifa_alquiler where id = ?",
                tarifaId)).containsEntry("tipo", "ALQUILER_KM").containsEntry("desde", vigente);
        assertThat(jdbc.queryForObject("select count(*) from tarifa_alquiler where id = ?", Integer.class, tarifaId))
                .isEqualTo(1);
    }

    @Test
    void laTarifaDeDepreciacionEsDeFinanceYSeDescarta() {
        long tarifaId = ThreadLocalRandom.current().nextLong(1_000_000, 800_000_000);
        UUID evento = publicador.publicar(TiposEvento.CONFIGURACION_ACTUALIZADA, "DEPRECIACION_KM", tarifaId,
                Map.of("tarifaId", tarifaId, "tipo", "DEPRECIACION_KM", "valor", "1.2500", "vigenteDesde", "2041-01-01"));

        await().atMost(ESPERA).until(() -> publicador.procesado(evento));
        assertThat(jdbc.queryForObject("select count(*) from tarifa_alquiler where id = ?", Integer.class, tarifaId))
                .isZero();
    }
}
