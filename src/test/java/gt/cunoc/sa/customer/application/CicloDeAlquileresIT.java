package gt.cunoc.sa.customer.application;

import static gt.cunoc.sa.customer.Escenario.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
 * ViajeEliminado (E10) publicados como lo haría trip, con duplicados, desorden y el reembolso. Los
 * ids de trip y fleet son texto: los numéricos de hoy ("101") y un UUID en texto.
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
        String viaje = UUID.randomUUID().toString();

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
        Map<String, Object> regular = new HashMap<>(Map.of("viajeId", "555", "tipo", "REGULAR"));
        publicador.publicar(TiposEvento.VIAJE_INICIADO, "555", 1, regular);
        publicador.publicar(TiposEvento.VIAJE_ELIMINADO, "555", 2, regular);
        // De un REGULAR no se lee nada más que la falta de alquilerId: ni un viajeId vacío lo manda a la DLQ.
        publicador.publicar(TiposEvento.VIAJE_FINALIZADO, "", 3, new HashMap<>(Map.of("viajeId", "", "tipo", "REGULAR")));

        // Uno de alquiler detrás: cuando se aplica, los anteriores ya pasaron (un solo consumidor).
        UUID alquiler = escenario.alquiler(escenario.cuenta(BigDecimal.ZERO).clienteId(), UUID.randomUUID(), "ONLINE",
                "ASIGNADO");
        String otro = UUID.randomUUID().toString();
        aplicar(TiposEvento.VIAJE_INICIADO, otro, 1, viaje(otro, alquiler));
        assertThat(publicador.mensajesEnDlq()).isEqualTo(antes);
    }

    @Test
    void eliminarElViajeDeUnAlquilerPagadoConCarteraLoReembolsaUnaSolaVezYEmiteAlquilerCancelado()
            throws Exception {
        Cuenta cuenta = escenario.cuenta(new BigDecimal("5000"));
        UUID sucursal = UUID.randomUUID();
        UUID alquiler = pagadoConCartera(cuenta, sucursal);
        assertThat(escenario.saldo(cuenta.clienteId())).isEqualByComparingTo("3800");
        String viaje = UUID.randomUUID().toString();

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
        String viaje = "303";

        aplicar(TiposEvento.VIAJE_ELIMINADO, viaje, 3, viaje(viaje, alquiler));

        assertThat(estado(alquiler)).containsEntry("estado", "CANCELADO");
        Sobre e16 = eventos.esperar(TiposEvento.ALQUILER_CANCELADO, s -> s.aggregateId().equals(alquiler.toString()));
        assertThat(e16.payload().get("reembolso").asText()).isEqualTo("CAJA");
        assertThat(e16.payload().get("fechaPago").asText()).isEqualTo("2026-10-02");
    }

    @Test
    void unAlquilerQueNoExisteVaDirectoALaDlq() {
        int antes = publicador.mensajesEnDlq();
        publicador.publicar(TiposEvento.VIAJE_INICIADO, "404", 1, viaje("404", UUID.randomUUID()));

        await().atMost(Duration.ofSeconds(5)).until(() -> publicador.mensajesEnDlq() == antes + 1);
    }

    @Test
    void losIdsNumericosDeTripYFleetEntranComoTextoYSalenIgualEnElAlquiler() throws Exception {
        Cuenta cuenta = escenario.cuenta(BigDecimal.ZERO);
        UUID alquiler = escenario.alquiler(cuenta.clienteId(), UUID.randomUUID(), "ONLINE", "PAGADO");

        aplicar(TiposEvento.ALQUILER_ASIGNADO, "101", 1,
                asignacion(alquiler, "101", "7", "P-101AAA", "3", "Pedro Pérez"));

        assertThat(estado(alquiler)).containsEntry("estado", "ASIGNADO").containsEntry("viaje_id", "101")
                .containsEntry("bus_id", "7").containsEntry("chofer_id", "3");
        mvc.perform(get("/api/alquileres/{id}", alquiler).with(como(cuenta.actual())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viajeId").value("101"))
                .andExpect(jsonPath("$.busId").value("7"))
                .andExpect(jsonPath("$.busPlaca").value("P-101AAA"))
                .andExpect(jsonPath("$.choferId").value("3"));
    }

    /** Sin E15 (trip lo emite en la fase 5), E8 deja en el alquiler el viajeId numérico. */
    @Test
    void laSalidaYLaLlegadaConViajeIdNumericoAvanzanElAlquilerYDejanSuViaje() {
        UUID alquiler = escenario.alquiler(escenario.cuenta(BigDecimal.ZERO).clienteId(), UUID.randomUUID(), "ONLINE",
                "PAGADO");

        aplicar(TiposEvento.VIAJE_INICIADO, "101", 1, viaje("101", alquiler));
        assertThat(estado(alquiler)).containsEntry("estado", "EN_CURSO").containsEntry("viaje_id", "101");
        aplicar(TiposEvento.VIAJE_FINALIZADO, "101", 2, viaje("101", alquiler));
        assertThat(estado(alquiler)).containsEntry("estado", "FINALIZADO").containsEntry("viaje_id", "101");
    }

    @Test
    void eliminarUnViajeConIdNumericoCancelaElAlquilerYDevuelveElPago() throws Exception {
        Cuenta cuenta = escenario.cuenta(new BigDecimal("5000"));
        UUID alquiler = pagadoConCartera(cuenta, UUID.randomUUID());
        aplicar(TiposEvento.ALQUILER_ASIGNADO, "101", 1,
                asignacion(alquiler, "101", "7", "P-101AAA", "3", "Pedro Pérez"));

        aplicar(TiposEvento.VIAJE_ELIMINADO, "101", 2, viaje("101", alquiler));

        assertThat(estado(alquiler)).containsEntry("estado", "CANCELADO");
        assertThat(escenario.saldo(cuenta.clienteId())).isEqualByComparingTo("5000");
        // El reembolso lleva la referencia del débito (el alquiler), ya como texto.
        assertThat(jdbc.queryForObject("select referencia_id from movimiento_cartera where idempotency_key = ?",
                String.class, "REEMBOLSO-ALQUILER:" + alquiler)).isEqualTo(alquiler.toString());
        Sobre e16 = eventos.esperar(TiposEvento.ALQUILER_CANCELADO, s -> s.aggregateId().equals(alquiler.toString()));
        assertThat(e16.payload().get("reembolso").asText()).isEqualTo("CARTERA");
    }

    /** Si trip manda el id como número JSON (su Long), entra como el mismo texto. */
    @Test
    void unIdQueLlegaComoNumeroJsonSeGuardaComoElMismoTexto() {
        UUID alquiler = escenario.alquiler(escenario.cuenta(BigDecimal.ZERO).clienteId(), UUID.randomUUID(), "ONLINE",
                "PAGADO");
        Map<String, Object> numerica = new HashMap<>(Map.of("alquilerId", alquiler, "viajeId", 101, "busId", 7L,
                "placa", "P-101AAA", "choferId", 3, "choferNombre", "Pedro Pérez"));

        aplicar(TiposEvento.ALQUILER_ASIGNADO, "101", 1, numerica);

        assertThat(estado(alquiler)).containsEntry("viaje_id", "101").containsEntry("bus_id", "7")
                .containsEntry("chofer_id", "3");
    }

    @Test
    void unIdVacioODeMasDe64VaDirectoALaDlqPeroElViajeIdDeE10NoSeRevisa() {
        UUID alquiler = escenario.alquiler(escenario.cuenta(BigDecimal.ZERO).clienteId(), UUID.randomUUID(), "ONLINE",
                "PAGADO");
        int antes = publicador.mensajesEnDlq();

        publicador.publicar(TiposEvento.ALQUILER_ASIGNADO, "101", 1, asignacion(alquiler, "101", "", "P-1", "3", "X"));
        publicador.publicar(TiposEvento.ALQUILER_ASIGNADO, "101", 2,
                asignacion(alquiler, "101", "7", "P-1", "3".repeat(65), "X"));
        publicador.publicar(TiposEvento.VIAJE_INICIADO, " ", 3, viaje(" ", alquiler));

        await().atMost(Duration.ofSeconds(5)).until(() -> publicador.mensajesEnDlq() == antes + 3);
        assertThat(estado(alquiler)).containsEntry("estado", "PAGADO").containsEntry("bus_id", null);
        // 64 todavía cabe.
        aplicar(TiposEvento.ALQUILER_ASIGNADO, "101", 4, asignacion(alquiler, "101", "7", "P-1", "3".repeat(64), "X"));
        assertThat(estado(alquiler)).containsEntry("estado", "ASIGNADO");

        // E10 no guarda el viajeId: uno vacío no detiene la cancelación.
        aplicar(TiposEvento.VIAJE_ELIMINADO, "", 5, viaje("", alquiler));
        assertThat(estado(alquiler)).containsEntry("estado", "CANCELADO");
        assertThat(publicador.mensajesEnDlq()).isEqualTo(antes + 3);
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

    private void aplicar(String tipo, String viaje, long version, Map<String, Object> payload) {
        UUID evento = publicador.publicar(tipo, viaje, version, payload);
        await().atMost(ESPERA).until(() -> publicador.procesado(evento));
    }

    private Map<String, Object> estado(UUID alquiler) {
        return jdbc.queryForMap("select estado, bus_placa, chofer_nombre, viaje_id, bus_id, chofer_id "
                + "from alquiler where id = ?", alquiler);
    }

    /** Bus y chofer como UUID en texto: que los ids sean texto no obliga a que sean numéricos. */
    private static Map<String, Object> asignacion(UUID alquiler, String viaje, String placa, String chofer) {
        return asignacion(alquiler, viaje, UUID.randomUUID().toString(), placa, UUID.randomUUID().toString(), chofer);
    }

    private static Map<String, Object> asignacion(UUID alquiler, String viaje, String bus, String placa,
            String choferId, String chofer) {
        return new HashMap<>(Map.of("alquilerId", alquiler, "viajeId", viaje, "busId", bus,
                "placa", placa, "choferId", choferId, "choferNombre", chofer));
    }

    private static Map<String, Object> viaje(String viaje, UUID alquiler) {
        return new HashMap<>(Map.of("viajeId", viaje, "tipo", "ALQUILER", "alquilerId", alquiler,
                "kmFinal", 125_000));   // un campo de trip que customer no usa
    }
}
