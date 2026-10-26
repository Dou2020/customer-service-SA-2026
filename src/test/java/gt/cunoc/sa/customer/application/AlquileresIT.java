package gt.cunoc.sa.customer.application;

import static gt.cunoc.sa.customer.Escenario.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import gt.cunoc.sa.customer.ColaDePrueba;
import gt.cunoc.sa.customer.Escenario;
import gt.cunoc.sa.customer.Escenario.Cuenta;
import gt.cunoc.sa.customer.PruebaDeIntegracion;
import gt.cunoc.sa.customer.infrastructure.messaging.Sobre;
import gt.cunoc.sa.customer.infrastructure.messaging.eventos.TiposEvento;
import gt.cunoc.sa.customer.infrastructure.security.UsuarioActual;

/**
 * El alquiler por sus endpoints: solicitud en línea y en mostrador, confirmación, pago con cartera y
 * en efectivo, cancelación y rechazo, con sus eventos (E13, E14, E16) y la autorización por rol,
 * dueño y sucursal.
 */
@PruebaDeIntegracion
class AlquileresIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    Escenario escenario;
    @Autowired
    AmqpAdmin admin;
    @Autowired
    RabbitTemplate rabbit;

    ColaDePrueba eventos;
    UUID sucursal;
    UsuarioActual jefe;

    @BeforeEach
    void preparar() {
        escenario.tarifasBase();
        eventos = new ColaDePrueba(admin, rabbit, json, "customer.alquiler.#");
        sucursal = UUID.randomUUID();
        jefe = Escenario.adminDe(sucursal);
    }

    @AfterEach
    void cerrar() {
        eventos.close();
    }

    @Test
    void elClienteSolicitaLaSucursalConfirmaYElClientePagaConSuCartera() throws Exception {
        Cuenta cuenta = escenario.cuenta(new BigDecimal("5000"));

        JsonNode solicitado = leer(enviar(post("/api/alquileres"), solicitud(true), cuenta.actual())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("SOLICITADO"))
                .andExpect(jsonPath("$.canal").value("ONLINE"))
                .andExpect(jsonPath("$.idaYVuelta").value(true))
                .andExpect(jsonPath("$.clienteNit").value(cuenta.nit())));
        String id = solicitado.get("id").asText();
        // 3 días (del 10 al 12) con tarifas de 5/km y 500/día: distancia × 5 × 2 + 3 × 500.
        BigDecimal distancia = solicitado.get("distanciaKm").decimalValue();
        assertThat(solicitado.get("precioEstimado").decimalValue()).isEqualByComparingTo(
                distancia.multiply(new BigDecimal("10")).add(new BigDecimal("1500")).setScale(2, RoundingMode.HALF_UP));

        enviar(post("/api/alquileres/{id}/confirmacion", id), Map.of("precioFinal", "2000.00"), jefe)
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CONFIRMADO"));
        Sobre e13 = eventos.esperar(TiposEvento.ALQUILER_CONFIRMADO, s -> s.aggregateId().equals(id));
        assertThat(e13.payload().get("precioFinal").decimalValue()).isEqualByComparingTo("2000");
        assertThat(e13.payload().get("correo").asText()).endsWith("@prueba.gt");

        enviar(post("/api/alquileres/{id}/pago", id), Map.of("fechaPago", "2026-10-05"), cuenta.actual())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PAGADO"))
                .andExpect(jsonPath("$.fechaPago").value("2026-10-05"));
        assertThat(escenario.saldo(cuenta.clienteId())).isEqualByComparingTo("3000");
        Sobre e14 = eventos.esperar(TiposEvento.ALQUILER_PAGADO, s -> s.aggregateId().equals(id));
        assertThat(e14.aggregateVersion()).isGreaterThan(e13.aggregateVersion());
        assertThat(e14.payload().get("metodoPago").asText()).isEqualTo("CARTERA");
        assertThat(e14.payload().get("monto").decimalValue()).isEqualByComparingTo("2000");
        assertThat(e14.payload().get("sucursalId").asText()).isEqualTo(sucursal.toString());
        assertThat(e14.payload().get("fechaPago").asText()).isEqualTo("2026-10-05");
        assertThat(e14.payload().get("origenLat").decimalValue()).isEqualByComparingTo("14.834999");

        // Lo ve su cliente y el personal de su sucursal; nadie más.
        mvc.perform(get("/api/alquileres/{id}", id).with(como(cuenta.actual()))).andExpect(status().isOk());
        mvc.perform(get("/api/alquileres/{id}", id).with(como(Escenario.cajeroDe(sucursal)))).andExpect(status().isOk());
        mvc.perform(get("/api/alquileres/{id}", id).with(como(Escenario.adminDe(UUID.randomUUID()))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/alquileres/{id}", id).with(como(escenario.cuenta(BigDecimal.ZERO).actual())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/alquileres/{id}", UUID.randomUUID()).with(como(cuenta.actual())))
                .andExpect(status().isNotFound());
    }

    @Test
    void sinSaldoElPagoResponde422YNoCambiaNada() throws Exception {
        Cuenta cuenta = escenario.cuenta(new BigDecimal("10"));
        String id = solicitarYConfirmar(cuenta);

        enviar(post("/api/alquileres/{id}/pago", id), Map.of(), cuenta.actual())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(LibroCartera.SALDO_INSUFICIENTE));

        mvc.perform(get("/api/alquileres/{id}", id).with(como(cuenta.actual())))
                .andExpect(jsonPath("$.estado").value("CONFIRMADO"));
        assertThat(escenario.saldo(cuenta.clienteId())).isEqualByComparingTo("10");
    }

    @Test
    void soloSuClientePagaEnLineaYNoDosVeces() throws Exception {
        Cuenta cuenta = escenario.cuenta(new BigDecimal("5000"));
        String id = solicitarYConfirmar(cuenta);

        enviar(post("/api/alquileres/{id}/pago", id), Map.of(), escenario.cuenta(new BigDecimal("5000")).actual())
                .andExpect(status().isForbidden());
        enviar(post("/api/alquileres/{id}/pago", id), Map.of(), cuenta.actual()).andExpect(status().isOk());
        enviar(post("/api/alquileres/{id}/pago", id), Map.of(), cuenta.actual())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Este alquiler ya está pagado"));
        assertThat(escenario.saldo(cuenta.clienteId())).isEqualByComparingTo("3800");   // 5000 - 1200
        // La confirmación ya no aplica a un alquiler pagado.
        enviar(post("/api/alquileres/{id}/confirmacion", id), Map.of(), jefe).andExpect(status().isConflict());
    }

    @Test
    void enMostradorElCajeroSolicitaPorNitYCobraEnEfectivo() throws Exception {
        UsuarioActual cajero = Escenario.cajeroDe(sucursal);
        String nit = Escenario.nit();
        Map<String, Object> cuerpo = solicitud(false);
        cuerpo.remove("sucursalId");
        cuerpo.put("nit", nit);
        cuerpo.put("nombre", "Iglesia El Calvario");

        String id = leer(enviar(post("/api/alquileres/mostrador"), cuerpo, cajero)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.canal").value("PRESENCIAL"))
                .andExpect(jsonPath("$.sucursalId").value(sucursal.toString()))
                .andExpect(jsonPath("$.clienteNombre").value("Iglesia El Calvario"))
                .andExpect(jsonPath("$.idaYVuelta").value(false))).get("id").asText();
        assertThat(jdbc.queryForObject("select usuario_id from cliente where nit = ?", UUID.class, nit)).isNull();

        // El cajero no confirma precios: eso es del admin de la sucursal.
        enviar(post("/api/alquileres/{id}/confirmacion", id), Map.of(), cajero).andExpect(status().isForbidden());
        enviar(post("/api/alquileres/{id}/confirmacion", id), Map.of(), jefe).andExpect(status().isOk());
        enviar(post("/api/alquileres/{id}/pago-mostrador", id), Map.of(), Escenario.cajeroDe(UUID.randomUUID()))
                .andExpect(status().isForbidden());
        enviar(post("/api/alquileres/{id}/pago-mostrador", id), Map.of("fechaPago", "2026-10-06"), cajero)
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("PAGADO"));

        Sobre e14 = eventos.esperar(TiposEvento.ALQUILER_PAGADO, s -> s.aggregateId().equals(id));
        assertThat(e14.payload().get("metodoPago").asText()).isEqualTo("EFECTIVO");
        assertThat(e14.payload().get("nit").asText()).isEqualTo(nit);
        assertThat(jdbc.queryForObject("select count(*) from movimiento_cartera where referencia_id = ?",
                Integer.class, id)).isZero();
    }

    @Test
    void elClienteCancelaAntesDePagarPeroNoDespues() throws Exception {
        Cuenta cuenta = escenario.cuenta(new BigDecimal("5000"));
        String porCancelar = solicitarYConfirmar(cuenta);

        enviar(post("/api/alquileres/{id}/cancelacion", porCancelar), Map.of("motivo", "Cambié de planes"),
                cuenta.actual()).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CANCELADO"));
        Sobre e16 = eventos.esperar(TiposEvento.ALQUILER_CANCELADO, s -> s.aggregateId().equals(porCancelar));
        assertThat(e16.payload().get("reembolso").asText()).isEqualTo("NINGUNO");
        assertThat(e16.payload().get("estabaPagado").asBoolean()).isFalse();
        assertThat(e16.payload().get("motivo").asText()).isEqualTo("Cambié de planes");

        String pagado = solicitarYConfirmar(cuenta);
        enviar(post("/api/alquileres/{id}/pago", pagado), Map.of(), cuenta.actual()).andExpect(status().isOk());
        enviar(post("/api/alquileres/{id}/cancelacion", pagado), Map.of(), cuenta.actual())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "Un alquiler pagado ya no se cancela aquí: la sucursal lo cancela eliminando su viaje"));
    }

    @Test
    void laSucursalRechazaConUnMotivo() throws Exception {
        Cuenta cuenta = escenario.cuenta(BigDecimal.ZERO);
        String id = leer(enviar(post("/api/alquileres"), solicitud(false), cuenta.actual())).get("id").asText();

        enviar(post("/api/alquileres/{id}/rechazo", id), Map.of(), jefe).andExpect(status().isBadRequest());
        enviar(post("/api/alquileres/{id}/rechazo", id), Map.of("motivo", "No hay buses ese día"),
                Escenario.adminDe(UUID.randomUUID())).andExpect(status().isForbidden());
        enviar(post("/api/alquileres/{id}/rechazo", id), Map.of("motivo", "No hay buses ese día"), jefe)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECHAZADO"))
                .andExpect(jsonPath("$.motivo").value("No hay buses ese día"));
        enviar(post("/api/alquileres/{id}/pago", id), Map.of(), cuenta.actual()).andExpect(status().isConflict());
    }

    @Test
    void cadaQuienVeLosSuyosYLaSucursalLosDeSuSucursal() throws Exception {
        Cuenta cuenta = escenario.cuenta(BigDecimal.ZERO);
        UUID otraSucursal = UUID.randomUUID();
        escenario.alquiler(cuenta.clienteId(), sucursal, "ONLINE", "SOLICITADO");
        escenario.alquiler(cuenta.clienteId(), sucursal, "ONLINE", "PAGADO");
        escenario.alquiler(cuenta.clienteId(), otraSucursal, "ONLINE", "SOLICITADO");

        mvc.perform(get("/api/alquileres").param("estado", "SOLICITADO").with(como(jefe)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].sucursalId").value(sucursal.toString()));
        // El sucursalId de la URL no amplía el alcance de un BRANCH_ADMIN.
        mvc.perform(get("/api/alquileres").param("sucursalId", otraSucursal.toString()).with(como(jefe)))
                .andExpect(jsonPath("$.page.totalElements").value(2));
        mvc.perform(get("/api/alquileres").param("sucursalId", otraSucursal.toString())
                        .with(como(Escenario.sysAdmin())))
                .andExpect(jsonPath("$.page.totalElements").value(1));
        mvc.perform(get("/api/alquileres/mios").with(como(cuenta.actual())))
                .andExpect(jsonPath("$.page.totalElements").value(3));
        mvc.perform(get("/api/alquileres").with(como(cuenta.actual()))).andExpect(status().isForbidden());
        mvc.perform(get("/api/alquileres")).andExpect(status().isUnauthorized());
    }

    @Test
    void laEstimacionValidaFechasLugarYTarifas() throws Exception {
        Cuenta cuenta = escenario.cuenta(BigDecimal.ZERO);
        Map<String, Object> cuerpo = solicitud(true);

        enviar(post("/api/alquileres/estimacion"), cuerpo, cuenta.actual())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dias").value(3))
                .andExpect(jsonPath("$.tarifaKm").value(5.0))
                .andExpect(jsonPath("$.cargoDia").value(500.0));

        cuerpo.put("fechaRetorno", "2026-10-09");
        enviar(post("/api/alquileres/estimacion"), cuerpo, cuenta.actual())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("La fecha de retorno no puede ser anterior a la de salida"));

        Map<String, Object> mismoLugar = solicitud(false);
        mismoLugar.put("trayecto", trayecto("14.834999", "-91.518611", "14.834999", "-91.518611"));
        enviar(post("/api/alquileres/estimacion"), mismoLugar, cuenta.actual())
                .andExpect(jsonPath("$.detail").value("El origen y el destino están en el mismo lugar"));

        Map<String, Object> sinTarifas = solicitud(false);
        sinTarifas.put("fechaSolicitud", "1990-01-01");
        enviar(post("/api/alquileres/estimacion"), sinTarifas, cuenta.actual())
                .andExpect(status().isUnprocessableEntity());

        Map<String, Object> sinPasajeros = solicitud(false);
        sinPasajeros.remove("pasajeros");
        enviar(post("/api/alquileres"), sinPasajeros, cuenta.actual())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.pasajeros").exists());
    }

    @Test
    void elReporteDeIngresosCuentaLosPagadosNoCanceladosPorFechaDePago() throws Exception {
        Cuenta cuenta = escenario.cuenta(BigDecimal.ZERO);
        escenario.alquiler(cuenta.clienteId(), sucursal, "ONLINE", "FINALIZADO");
        escenario.alquiler(cuenta.clienteId(), sucursal, "PRESENCIAL", "PAGADO");
        escenario.alquiler(cuenta.clienteId(), sucursal, "ONLINE", "CANCELADO");
        escenario.alquiler(cuenta.clienteId(), sucursal, "ONLINE", "CONFIRMADO");
        escenario.alquiler(cuenta.clienteId(), UUID.randomUUID(), "ONLINE", "FINALIZADO");

        mvc.perform(get("/api/alquileres/reporte-ingresos").with(como(jefe)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cantidad").value(2))
                .andExpect(jsonPath("$.total").value(2000.0))
                .andExpect(jsonPath("$.sucursalId").value(sucursal.toString()))
                .andExpect(jsonPath("$.filas[0].nit").value(cuenta.nit()))
                .andExpect(jsonPath("$.filas[0].fechaPago").value("2026-10-02"));
        mvc.perform(get("/api/alquileres/reporte-ingresos").param("desde", "2026-10-03").with(como(jefe)))
                .andExpect(jsonPath("$.cantidad").value(0))
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/alquileres/reporte-ingresos").param("sucursalId", sucursal.toString())
                        .with(como(Escenario.sysAdmin())))
                .andExpect(jsonPath("$.cantidad").value(2));
        mvc.perform(get("/api/alquileres/reporte-ingresos").with(como(Escenario.cajeroDe(sucursal))))
                .andExpect(status().isForbidden());
    }

    private String solicitarYConfirmar(Cuenta cuenta) throws Exception {
        String id = leer(enviar(post("/api/alquileres"), solicitud(false), cuenta.actual())
                .andExpect(status().isCreated())).get("id").asText();
        enviar(post("/api/alquileres/{id}/confirmacion", id), Map.of("precioFinal", "1200.00"), jefe)
                .andExpect(status().isOk());
        return id;
    }

    /** De Quetzaltenango a Huehuetenango, solicitado el 1 de octubre para salir el 10. */
    private Map<String, Object> solicitud(boolean idaYVuelta) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("sucursalId", sucursal);
        cuerpo.put("trayecto", trayecto("14.834999", "-91.518611", "15.319722", "-91.470833"));
        cuerpo.put("fechaSolicitud", "2026-10-01");
        cuerpo.put("fechaSalida", "2026-10-10");
        if (idaYVuelta) {
            cuerpo.put("fechaRetorno", "2026-10-12");
        }
        cuerpo.put("pasajeros", 30);
        return cuerpo;
    }

    private static Map<String, Object> trayecto(String lat1, String lng1, String lat2, String lng2) {
        return Map.of("origen", "Quetzaltenango", "origenLat", new BigDecimal(lat1), "origenLng", new BigDecimal(lng1),
                "destino", "Huehuetenango", "destinoLat", new BigDecimal(lat2), "destinoLng", new BigDecimal(lng2));
    }

    private ResultActions enviar(MockHttpServletRequestBuilder peticion, Map<String, ?> cuerpo, UsuarioActual actor)
            throws Exception {
        return mvc.perform(peticion.contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(cuerpo)).with(como(actor)));
    }

    private JsonNode leer(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString());
    }
}
