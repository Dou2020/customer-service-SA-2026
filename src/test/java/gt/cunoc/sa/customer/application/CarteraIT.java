package gt.cunoc.sa.customer.application;

import static gt.cunoc.sa.customer.Escenario.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;

import gt.cunoc.sa.customer.Escenario;
import gt.cunoc.sa.customer.Escenario.Cuenta;
import gt.cunoc.sa.customer.PruebaDeIntegracion;
import gt.cunoc.sa.customer.api.cartera.CarteraService;
import gt.cunoc.sa.customer.api.cartera.dto.RecargaRequest;
import gt.cunoc.sa.customer.api.error.ErrorDeNegocio;
import gt.cunoc.sa.customer.api.interno.DebitosService;
import gt.cunoc.sa.customer.api.interno.dto.DebitoRequest;
import gt.cunoc.sa.customer.api.interno.dto.ResultadoDebito;

/**
 * La cartera: recargas idempotentes, el débito de S4 con su Idempotency-Key, la concurrencia que
 * arbitra PostgreSQL y la lápida del reconciliador (README, "Compra de boletos en línea").
 */
@PruebaDeIntegracion
class CarteraIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    Escenario escenario;
    @Autowired
    CarteraService carteras;
    @Autowired
    DebitosService debitos;

    @Test
    void laRecargaAcreditaConLaFechaElegidaYApareceEnLosMovimientos() throws Exception {
        Cuenta cuenta = escenario.cuenta(BigDecimal.ZERO);

        recargar(cuenta, "clave-" + UUID.randomUUID(), Map.of("monto", "150.50", "fecha", "2026-10-01"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("RECARGA"))
                .andExpect(jsonPath("$.fecha").value("2026-10-01"))
                .andExpect(jsonPath("$.saldoResultante").value(150.5));

        mvc.perform(get("/api/cartera").with(como(cuenta.actual())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldo").value(150.5));
        mvc.perform(get("/api/cartera/movimientos").param("desde", "2026-10-01").param("hasta", "2026-10-01")
                        .with(como(cuenta.actual())))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].monto").value(150.5));
        mvc.perform(get("/api/cartera/movimientos").param("desde", "2026-10-02").with(como(cuenta.actual())))
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void laMismaClaveDevuelveLaRecargaOriginalSinAcreditarDosVeces() throws Exception {
        Cuenta cuenta = escenario.cuenta(BigDecimal.ZERO);
        String clave = "clave-" + UUID.randomUUID();
        String id = json.readTree(recargar(cuenta, clave, Map.of("monto", "100")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        recargar(cuenta, clave, Map.of("monto", "100")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        recargar(cuenta, clave, Map.of("monto", "90")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Esa Idempotency-Key ya se usó en otra operación"));
        // La clave de otro cliente tampoco sirve para acreditarse a uno mismo.
        recargar(escenario.cuenta(BigDecimal.ZERO), clave, Map.of("monto", "100")).andExpect(status().isConflict());
        assertThat(escenario.saldo(cuenta.clienteId())).isEqualByComparingTo("100");
    }

    @Test
    void recargasSimultaneasConLaMismaClaveAcreditanUnaSolaVez() throws Exception {
        Cuenta cuenta = escenario.cuenta(BigDecimal.ZERO);
        String clave = "doble-clic-" + UUID.randomUUID();
        List<Object> resultados = simultaneos(5, () -> {
            Escenario.autenticar(cuenta.actual());
            return carteras.recargar(cuenta.actual(), clave, new RecargaRequest(new BigDecimal("25.00"), null));
        });

        assertThat(resultados).allMatch(r -> r instanceof CarteraService.Recarga);
        assertThat(resultados).filteredOn(r -> ((CarteraService.Recarga) r).nueva()).hasSize(1);
        assertThat(escenario.saldo(cuenta.clienteId())).isEqualByComparingTo("25");
    }

    @Test
    void unaRecargaSinClaveOConMontoInvalidoResponde400() throws Exception {
        Cuenta cuenta = escenario.cuenta(BigDecimal.ZERO);
        mvc.perform(post("/api/cartera/recargas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"monto\": 10}").with(como(cuenta.actual())))
                .andExpect(status().isBadRequest());
        recargar(cuenta, "clave-" + UUID.randomUUID(), Map.of("monto", "-5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.monto").exists());
    }

    @Test
    void unaCuentaQueCustomerTodaviaNoConoceResponde409() throws Exception {
        mvc.perform(get("/api/cartera").with(como(Escenario.cliente())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(ResolucionDeClientes.CUENTA_SIN_CLIENTE));
    }

    @Test
    void elDebitoEsIdempotenteYNoPasaDelSaldo() throws Exception {
        Cuenta cuenta = escenario.cuenta(new BigDecimal("100"));
        String venta = UUID.randomUUID().toString();

        String movimiento = json.readTree(debitar(cuenta.clienteId(), venta, "60.00")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultado").value("APLICADO"))
                .andExpect(jsonPath("$.saldo").value(40.0))
                .andReturn().getResponse().getContentAsString()).get("movimientoId").asText();
        // sales reintenta tras un timeout: misma respuesta, un solo cargo.
        debitar(cuenta.clienteId(), venta, "60.00").andExpect(status().isOk())
                .andExpect(jsonPath("$.movimientoId").value(movimiento));
        debitar(cuenta.clienteId(), UUID.randomUUID().toString(), "60.00")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(LibroCartera.SALDO_INSUFICIENTE));

        assertThat(escenario.saldo(cuenta.clienteId())).isEqualByComparingTo("40");
        assertThat(jdbc.queryForObject("select fecha from movimiento_cartera where idempotency_key = ?",
                LocalDate.class, venta)).isEqualTo(LocalDate.of(2026, 10, 3));
    }

    @Test
    void debitosSimultaneosNuncaDejanElSaldoEnNegativo() throws Exception {
        Cuenta cuenta = escenario.cuenta(new BigDecimal("100"));
        List<Object> resultados = simultaneos(10, () -> debitos.debitar(cuenta.clienteId(),
                UUID.randomUUID().toString(), new DebitoRequest(new BigDecimal("30"), null, null)));

        assertThat(resultados).filteredOn(r -> r instanceof DebitosService.Debito).hasSize(3);
        assertThat(resultados).filteredOn(r -> r instanceof ErrorDeNegocio).hasSize(7);
        assertThat(escenario.saldo(cuenta.clienteId())).isEqualByComparingTo("10");
    }

    @Test
    void conLaLapidaPuestaUnDebitoTardioYaNoSeAplica() throws Exception {
        Cuenta cuenta = escenario.cuenta(new BigDecimal("100"));
        String venta = UUID.randomUUID().toString();

        anular(cuenta.clienteId(), venta).andExpect(status().isOk()).andExpect(jsonPath("$.resultado").value("ANULADO"));
        anular(cuenta.clienteId(), venta).andExpect(jsonPath("$.resultado").value("ANULADO"));
        debitar(cuenta.clienteId(), venta, "60.00").andExpect(status().isConflict());

        assertThat(escenario.saldo(cuenta.clienteId())).isEqualByComparingTo("100");
        // La lápida no movió dinero: el cliente no la ve.
        mvc.perform(get("/api/cartera/movimientos").with(como(cuenta.actual())))
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void siElDebitoYaEntroLaResolucionLoDaPorAplicado() throws Exception {
        Cuenta cuenta = escenario.cuenta(new BigDecimal("100"));
        String venta = UUID.randomUUID().toString();
        debitar(cuenta.clienteId(), venta, "60.00").andExpect(status().isCreated());

        anular(cuenta.clienteId(), venta).andExpect(jsonPath("$.resultado").value("APLICADO"));
        assertThat(escenario.saldo(cuenta.clienteId())).isEqualByComparingTo("40");
    }

    /** Gane quien gane, la respuesta del reconciliador coincide con lo que pasó con el dinero. */
    @Test
    void laLapidaYUnDebitoTardioSimultaneosSonConsistentes() throws Exception {
        for (int i = 0; i < 5; i++) {
            Cuenta cuenta = escenario.cuenta(new BigDecimal("100"));
            String venta = UUID.randomUUID().toString();
            List<Object> resultados = simultaneos(2, List.of(
                    () -> debitos.debitar(cuenta.clienteId(), venta, new DebitoRequest(new BigDecimal("60"), null, null)),
                    () -> debitos.anular(cuenta.clienteId(), venta).resultado()));

            boolean debitoEntro = resultados.get(0) instanceof DebitosService.Debito;
            assertThat(resultados.get(1)).isEqualTo(debitoEntro ? ResultadoDebito.APLICADO : ResultadoDebito.ANULADO);
            assertThat(escenario.saldo(cuenta.clienteId())).isEqualByComparingTo(debitoEntro ? "40" : "100");
        }
    }

    @Test
    void unClienteSinCarteraResponde404() throws Exception {
        debitar(escenario.generico(), UUID.randomUUID().toString(), "10").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Ese cliente no tiene cartera"));
    }

    private ResultActions recargar(Cuenta cuenta, String clave, Map<String, ?> cuerpo) throws Exception {
        return mvc.perform(post("/api/cartera/recargas").header("Idempotency-Key", clave)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo))
                .with(como(cuenta.actual())));
    }

    private ResultActions debitar(UUID clienteId, String clave, String monto) throws Exception {
        return mvc.perform(post("/internal/carteras/{id}/debitos", clienteId).header("Idempotency-Key", clave)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("monto", monto, "fecha", "2026-10-03"))));
    }

    private ResultActions anular(UUID clienteId, String clave) throws Exception {
        return mvc.perform(post("/internal/carteras/{id}/debitos/{clave}/anulacion", clienteId, clave));
    }

    /** Lanza las tareas a la vez; cada resultado es lo que devolvió o la excepción que lanzó. */
    private static List<Object> simultaneos(int veces, Callable<?> tarea) throws Exception {
        List<Callable<?>> tareas = new ArrayList<>();
        for (int i = 0; i < veces; i++) {
            tareas.add(tarea);
        }
        return simultaneos(veces, tareas);
    }

    private static List<Object> simultaneos(int hilos, List<Callable<?>> tareas) throws Exception {
        ExecutorService ejecutor = Executors.newFixedThreadPool(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Object>> futuros = new ArrayList<>();
        for (Callable<?> tarea : tareas) {
            futuros.add(ejecutor.submit(() -> {
                salida.await();
                try {
                    return tarea.call();
                } catch (RuntimeException e) {
                    return e;
                }
            }));
        }
        salida.countDown();
        List<Object> resultados = new ArrayList<>();
        for (Future<Object> futuro : futuros) {
            resultados.add(futuro.get());
        }
        ejecutor.shutdown();
        return resultados;
    }
}
