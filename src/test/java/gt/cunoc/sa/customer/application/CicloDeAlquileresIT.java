package gt.cunoc.sa.customer.application;

import static gt.cunoc.sa.customer.Escenario.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import gt.cunoc.sa.customer.ColaDePrueba;
import gt.cunoc.sa.customer.Escenario;
import gt.cunoc.sa.customer.Escenario.Cuenta;
import gt.cunoc.sa.customer.PruebaDeIntegracion;
import gt.cunoc.sa.customer.Publicador;
import gt.cunoc.sa.customer.infrastructure.messaging.Sobre;
import gt.cunoc.sa.customer.infrastructure.messaging.eventos.TiposEvento;

/**
 * El alquiler sigue a su viaje: AlquilerAsignado (E15), ViajeIniciado (E8), ViajeFinalizado (E9) y
 * ViajeEliminado (E10) publicados como lo haría trip, con duplicados, desorden y el reembolso.
 */
@PruebaDeIntegracion
class CicloDeAlquileresIT {

    private static final Duration ESPERA = Duration.ofSeconds(10);

    @Autowired
    Escenario escenario;
    @Autowired
    Publicador publicador;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    AmqpAdmin admin;
    @Autowired
    RabbitTemplate rabbit;

    ColaDePrueba eventos;

    @BeforeEach
    void escuchar() {
        escenario.tarifasBase();
        eventos = new ColaDePrueba(admin, rabbit, json, "customer.alquiler.#");
    }

    @AfterEach
    void cerrar() {
        eventos.close();
    }

    @Test
    void laAsignacionLaSalidaYLaLlegadaAvanzanElAlquilerYUnaReasignacionViejaNoPisaALaNueva() {
        UUID alquiler = escenario.alquiler(escenario.cuenta(BigDecimal.ZERO).clienteId(), UUID.randomUUID(), "ONLINE",
                "PAGADO");
        UUID viaje = UUID.randomUUID();

        aplicar(TiposEvento.ALQUILER_ASIGNADO, viaje, 1, asignacion(alquiler, viaje, "P-100AAA", "Pedro Pérez"));
        assertThat(estado(alquiler)).containsEntry("estado", "ASIGNADO").containsEntry("bus_placa", "P-100AAA")
                .containsEntry("chofer_nombre", "Pedro Pérez").containsEntry("viaje_id", viaje);

        aplicar(TiposEvento.ALQUILER_ASIGNADO, viaje, 3, asignacion(alquiler, viaje, "P-300CCC", "Ana Gómez"));
        aplicar(TiposEvento.ALQUILER_ASIGNADO, viaje, 2, asignacion(alquiler, viaje, "P-200BBB", "Tarde"));
        assertThat(estado(alquiler)).containsEntry("bus_placa", "P-300CCC");

        aplicar(TiposEvento.VIAJE_INICIADO, viaje, 4, viaje(viaje, alquiler));
        assertThat(estado(alquiler)).containsEntry("estado", "EN_CURSO");
        aplicar(TiposEvento.VIAJE_FINALIZADO, viaje, 5, viaje(viaje, alquiler));
        // Un ViajeIniciado repetido (otro eventId) no lo regresa.
        aplicar(TiposEvento.VIAJE_INICIADO, viaje, 4, viaje(viaje, alquiler));
        assertThat(estado(alquiler)).containsEntry("estado", "FINALIZADO");
    }

    @Test
    void losEventosDeUnViajeRegularNoTienenEfectoNiVanALaDlq() {
        int antes = publicador.mensajesEnDlq();
        UUID viaje = UUID.randomUUID();
        Map<String, Object> regular = new HashMap<>(Map.of("viajeId", viaje, "tipo", "REGULAR"));
        publicador.publicar(TiposEvento.VIAJE_INICIADO, viaje.toString(), 1, regular);
        publicador.publicar(TiposEvento.VIAJE_ELIMINADO, viaje.toString(), 2, regular);

        // Uno de alquiler detrás: cuando se aplica, los anteriores ya pasaron (un solo consumidor).
        UUID alquiler = escenario.alquiler(escenario.cuenta(BigDecimal.ZERO).clienteId(), UUID.randomUUID(), "ONLINE",
                "ASIGNADO");
        aplicar(TiposEvento.VIAJE_INICIADO, UUID.randomUUID(), 1, viaje(UUID.randomUUID(), alquiler));
        assertThat(publicador.mensajesEnDlq()).isEqualTo(antes);
    }

    @Test
    void eliminarElViajeDeUnAlquilerPagadoConCarteraLoReembolsaUnaSolaVezYEmiteAlquilerCancelado()
            throws Exception {
        Cuenta cuenta = escenario.cuenta(new BigDecimal("5000"));
        UUID sucursal = UUID.randomUUID();
        UUID alquiler = pagadoConCartera(cuenta, sucursal);
        assertThat(escenario.saldo(cuenta.clienteId())).isEqualByComparingTo("3800");
        UUID viaje = UUID.randomUUID();

        aplicar(TiposEvento.VIAJE_ELIMINADO, viaje, 2, viaje(viaje, alquiler));
        // trip no lo reemite, pero un duplicado con otro eventId tampoco devuelve dos veces.
        aplicar(TiposEvento.VIAJE_ELIMINADO, viaje, 2, viaje(viaje, alquiler));

        assertThat(estado(alquiler)).containsEntry("estado", "CANCELADO");
        assertThat(escenario.saldo(cuenta.clienteId())).isEqualByComparingTo("5000");
        assertThat(jdbc.queryForObject("""
                select count(*) from movimiento_cartera where cliente_id = ? and tipo = 'REEMBOLSO'""",
                Integer.class, cuenta.clienteId())).isEqualTo(1);
        Sobre e16 = eventos.esperar(TiposEvento.ALQUILER_CANCELADO, s -> s.aggregateId().equals(alquiler.toString()));
        assertThat(e16.payload().get("reembolso").asText()).isEqualTo("CARTERA");
        assertThat(e16.payload().get("estabaPagado").asBoolean()).isTrue();
        assertThat(e16.payload().get("monto").decimalValue()).isEqualByComparingTo("1200");
        assertThat(e16.payload().get("sucursalId").asText()).isEqualTo(sucursal.toString());
    }

    @Test
    void siSePagoEnEfectivoLaDevolucionEsEnCaja() {
        UUID alquiler = escenario.alquiler(escenario.presencial(Escenario.nit()), UUID.randomUUID(), "PRESENCIAL",
                "ASIGNADO");
        UUID viaje = UUID.randomUUID();

        aplicar(TiposEvento.VIAJE_ELIMINADO, viaje, 3, viaje(viaje, alquiler));

        assertThat(estado(alquiler)).containsEntry("estado", "CANCELADO");
        Sobre e16 = eventos.esperar(TiposEvento.ALQUILER_CANCELADO, s -> s.aggregateId().equals(alquiler.toString()));
        assertThat(e16.payload().get("reembolso").asText()).isEqualTo("CAJA");
        assertThat(e16.payload().get("fechaPago").asText()).isEqualTo("2026-10-02");
    }

    @Test
    void unAlquilerQueNoExisteVaDirectoALaDlq() {
        int antes = publicador.mensajesEnDlq();
        UUID viaje = UUID.randomUUID();
        publicador.publicar(TiposEvento.VIAJE_INICIADO, viaje.toString(), 1, viaje(viaje, UUID.randomUUID()));

        await().atMost(Duration.ofSeconds(5)).until(() -> publicador.mensajesEnDlq() == antes + 1);
    }

    /** Por los endpoints, para que el débito quede en el libro con su clave ALQUILER:id. */
    private UUID pagadoConCartera(Cuenta cuenta, UUID sucursal) throws Exception {
        Map<String, Object> solicitud = Map.of("sucursalId", sucursal, "fechaSolicitud", "2026-10-01",
                "fechaSalida", "2026-10-10", "pasajeros", 20,
                "trayecto", Map.of("origen", "Quetzaltenango", "origenLat", "14.834999", "origenLng", "-91.518611",
                        "destino", "Retalhuleu", "destinoLat", "14.536111", "destinoLng", "-91.677778"));
        UUID id = UUID.fromString(json.readTree(mvc.perform(post("/api/alquileres")
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(solicitud))
                        .with(como(cuenta.actual())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText());
        mvc.perform(post("/api/alquileres/{id}/confirmacion", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"precioFinal\": 1200}").with(como(Escenario.adminDe(sucursal))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/alquileres/{id}/pago", id).with(como(cuenta.actual()))).andExpect(status().isOk());
        return id;
    }

    private void aplicar(String tipo, UUID viaje, long version, Map<String, Object> payload) {
        UUID evento = publicador.publicar(tipo, viaje.toString(), version, payload);
        await().atMost(ESPERA).until(() -> publicador.procesado(evento));
    }

    private Map<String, Object> estado(UUID alquiler) {
        return jdbc.queryForMap("select estado, bus_placa, chofer_nombre, viaje_id from alquiler where id = ?",
                alquiler);
    }

    private static Map<String, Object> asignacion(UUID alquiler, UUID viaje, String placa, String chofer) {
        return new HashMap<>(Map.of("alquilerId", alquiler, "viajeId", viaje, "busId", UUID.randomUUID(),
                "placa", placa, "choferId", UUID.randomUUID(), "choferNombre", chofer));
    }

    private static Map<String, Object> viaje(UUID viaje, UUID alquiler) {
        return new HashMap<>(Map.of("viajeId", viaje, "tipo", "ALQUILER", "alquilerId", alquiler,
                "kmFinal", 125_000));   // un campo de trip que customer no usa
    }
}
