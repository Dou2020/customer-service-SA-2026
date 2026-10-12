package gt.cunoc.sa.customer.application;

import static gt.cunoc.sa.customer.Escenario.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import gt.cunoc.sa.customer.Escenario;
import gt.cunoc.sa.customer.Escenario.Cuenta;
import gt.cunoc.sa.customer.PruebaDeIntegracion;
import gt.cunoc.sa.customer.Publicador;
import gt.cunoc.sa.customer.api.interno.DebitosService;
import gt.cunoc.sa.customer.api.interno.dto.DebitoRequest;
import gt.cunoc.sa.customer.infrastructure.messaging.eventos.TiposEvento;

/**
 * BoletosVendidos (E11) y BoletosAnulados (E12) publicados como lo haría sales, y el historial que
 * arman (README, "Historial" y "Pruebas").
 */
@PruebaDeIntegracion
class HistorialIT {

    private static final Duration ESPERA = Duration.ofSeconds(10);

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    Escenario escenario;
    @Autowired
    Publicador publicador;
    @Autowired
    DebitosService debitos;

    @Test
    void elHistorialJuntaLoCompradoEnLineaYEnMostradorYLosAlquileres() throws Exception {
        Cuenta cuenta = escenario.cuenta(BigDecimal.ZERO);
        aplicar(TiposEvento.BOLETOS_VENDIDOS, UUID.randomUUID(), 1, venta(cuenta.clienteId(), "ONLINE", "2026-10-05"));
        // En mostrador con el NIT de la cuenta: S5 devolvió su cliente.
        aplicar(TiposEvento.BOLETOS_VENDIDOS, UUID.randomUUID(), 1,
                venta(cuenta.clienteId(), "PRESENCIAL", "2026-09-20"));
        escenario.alquiler(cuenta.clienteId(), UUID.randomUUID(), "PRESENCIAL", "FINALIZADO");

        mvc.perform(get("/api/historial").with(como(cuenta.actual())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nit").value(cuenta.nit()))
                .andExpect(jsonPath("$.boletos.length()").value(2))
                .andExpect(jsonPath("$.boletos[0].fechaPago").value("2026-10-05"))
                .andExpect(jsonPath("$.boletos[0].asientos[1]").value(13))
                .andExpect(jsonPath("$.boletos[1].canal").value("PRESENCIAL"))
                .andExpect(jsonPath("$.alquileres.length()").value(1));
        mvc.perform(get("/api/historial").param("desde", "2026-10-01").with(como(cuenta.actual())))
                .andExpect(jsonPath("$.boletos.length()").value(1));
        mvc.perform(get("/api/historial").with(como(Escenario.cliente()))).andExpect(status().isConflict());
    }

    /** S5 devolvió el cliente presencial justo antes de que una cuenta lo absorbiera (README, "Carreras"). */
    @Test
    void laVentaDeUnClienteAbsorbidoLlegaAlHistorialDeLaCuenta() throws Exception {
        Cuenta cuenta = escenario.cuenta(BigDecimal.ZERO);
        UUID lapida = UUID.randomUUID();
        jdbc.update("insert into cliente (id, nombre, absorbido_por) values (?, 'Absorbido', ?)", lapida,
                cuenta.clienteId());
        UUID ventaId = UUID.randomUUID();

        aplicar(TiposEvento.BOLETOS_VENDIDOS, ventaId, 1, venta(lapida, "PRESENCIAL", "2026-10-05"));

        assertThat(jdbc.queryForObject("select cliente_id from compra_boleto where venta_id = ?", UUID.class, ventaId))
                .isEqualTo(cuenta.clienteId());
        mvc.perform(get("/api/historial").with(como(cuenta.actual())))
                .andExpect(jsonPath("$.boletos[0].ventaId").value(ventaId.toString()));
    }

    @Test
    void siLaAnulacionLlegaAntesQueLaVentaLaCompraQuedaAnulada() {
        Cuenta cuenta = escenario.cuenta(BigDecimal.ZERO);
        UUID ventaId = UUID.randomUUID();

        aplicar(TiposEvento.BOLETOS_ANULADOS, ventaId, 2, venta(cuenta.clienteId(), "PRESENCIAL", "2026-10-05"));
        aplicar(TiposEvento.BOLETOS_VENDIDOS, ventaId, 1, venta(cuenta.clienteId(), "PRESENCIAL", "2026-10-05"));

        assertThat(jdbc.queryForObject("select estado from compra_boleto where venta_id = ?", String.class, ventaId))
                .isEqualTo("ANULADA");
    }

    @Test
    void anularUnaVentaEnLineaDevuelveLoDebitadoUnaSolaVez() {
        Cuenta cuenta = escenario.cuenta(new BigDecimal("100"));
        UUID ventaId = UUID.randomUUID();
        debitos.debitar(cuenta.clienteId(), ventaId.toString(), new DebitoRequest(new BigDecimal("70.00"), null, ventaId));
        aplicar(TiposEvento.BOLETOS_VENDIDOS, ventaId, 1, venta(cuenta.clienteId(), "ONLINE", "2026-10-05"));
        assertThat(escenario.saldo(cuenta.clienteId())).isEqualByComparingTo("30");

        aplicar(TiposEvento.BOLETOS_ANULADOS, ventaId, 2, venta(cuenta.clienteId(), "ONLINE", "2026-10-05"));
        aplicar(TiposEvento.BOLETOS_ANULADOS, ventaId, 2, venta(cuenta.clienteId(), "ONLINE", "2026-10-05"));

        assertThat(escenario.saldo(cuenta.clienteId())).isEqualByComparingTo("100");
        assertThat(jdbc.queryForObject("select count(*) from movimiento_cartera where idempotency_key = ?",
                Integer.class, "REEMBOLSO:" + ventaId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select estado from compra_boleto where venta_id = ?", String.class, ventaId))
                .isEqualTo("ANULADA");
    }

    @Test
    void sinDebitoNoHayReembolso() {
        // En mostrador (efectivo), y una en línea cuyo débito anuló el reconciliador.
        Cuenta cuenta = escenario.cuenta(new BigDecimal("100"));
        UUID presencial = UUID.randomUUID();
        UUID anulada = UUID.randomUUID();
        debitos.anular(cuenta.clienteId(), anulada.toString());

        aplicar(TiposEvento.BOLETOS_ANULADOS, presencial, 2, venta(cuenta.clienteId(), "PRESENCIAL", "2026-10-05"));
        aplicar(TiposEvento.BOLETOS_ANULADOS, anulada, 2, venta(cuenta.clienteId(), "ONLINE", "2026-10-05"));

        assertThat(escenario.saldo(cuenta.clienteId())).isEqualByComparingTo("100");
    }

    @Test
    void lasVentasAlClienteGenericoNoSeReplicanYUnClienteDesconocidoVaALaDlq() {
        UUID ventaGenerica = UUID.randomUUID();
        aplicar(TiposEvento.BOLETOS_VENDIDOS, ventaGenerica, 1, venta(escenario.generico(), "PRESENCIAL", "2026-10-05"));
        assertThat(jdbc.queryForObject("select count(*) from compra_boleto where venta_id = ?", Integer.class,
                ventaGenerica)).isZero();

        int antes = publicador.mensajesEnDlq();
        publicador.publicar(TiposEvento.BOLETOS_VENDIDOS, UUID.randomUUID().toString(), 1,
                venta(UUID.randomUUID(), "PRESENCIAL", "2026-10-05"));
        await().atMost(Duration.ofSeconds(5)).until(() -> publicador.mensajesEnDlq() == antes + 1);
    }

    private void aplicar(String tipo, UUID ventaId, long version, Map<String, Object> venta) {
        venta.put("ventaId", ventaId);
        UUID evento = publicador.publicar(tipo, ventaId.toString(), version, venta);
        await().atMost(ESPERA).until(() -> publicador.procesado(evento));
    }

    private static Map<String, Object> venta(UUID clienteId, String canal, String fechaPago) {
        Map<String, Object> venta = new HashMap<>();
        venta.put("clienteId", clienteId);
        venta.put("canal", canal);
        venta.put("viajeId", UUID.randomUUID());
        venta.put("origen", "Quetzaltenango");
        venta.put("destino", "Ciudad de Guatemala");
        venta.put("salida", "2026-10-20T12:00:00Z");
        venta.put("asientos", List.of(12, 13));
        venta.put("total", new BigDecimal("70.00"));
        venta.put("fechaPago", fechaPago);
        venta.put("correo", "comprador@prueba.gt");   // lo usa notification, customer lo ignora
        return venta;
    }
}
