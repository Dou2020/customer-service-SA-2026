package gt.cunoc.sa.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import gt.cunoc.sa.customer.Escenario;
import gt.cunoc.sa.customer.PruebaDeIntegracion;
import gt.cunoc.sa.customer.Publicador;
import gt.cunoc.sa.customer.api.interno.ClientesInternosService;
import gt.cunoc.sa.customer.infrastructure.messaging.eventos.TiposEvento;

/**
 * PerfilGuardado (E4) publicado como lo haría identity: alta, enlace por NIT, cambio de NIT con
 * absorción, duplicados, desorden y carreras (README, "Cliente, cuenta y NIT" y "Pruebas").
 */
@PruebaDeIntegracion
class EnlaceDeCuentasIT {

    private static final Duration ESPERA = Duration.ofSeconds(10);

    @Autowired
    Escenario escenario;
    @Autowired
    Publicador publicador;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ClientesInternosService clientesInternos;

    @Test
    void unaCuentaConCfTieneSuClientePropioConCarteraYNoTocaElGenerico() {
        UUID usuarioId = UUID.randomUUID();
        aplicar(usuarioId, 1, "CF");

        Map<String, Object> cliente = clienteDe(usuarioId);
        assertThat(cliente.get("nit")).isNull();
        assertThat(cliente.get("generico")).isEqualTo(false);
        assertThat(escenario.saldo((UUID) cliente.get("id"))).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("select usuario_id from cliente where generico", UUID.class)).isNull();
    }

    @Test
    void alConfirmarseConUnNitQueYaComproEnMostradorLaCuentaLoEnlaza() {
        String nit = Escenario.nit();
        UUID presencial = escenario.presencial(nit);
        UUID compra = escenario.compra(presencial, "PRESENCIAL");
        UUID usuarioId = UUID.randomUUID();

        aplicar(usuarioId, 1, nit);

        Map<String, Object> cliente = clienteDe(usuarioId);
        // El mismo cliente: lo vendido antes ya apunta a él, sin mover nada.
        assertThat(cliente.get("id")).isEqualTo(presencial);
        assertThat(cliente.get("nombre")).isEqualTo("Cuenta " + usuarioId);
        assertThat(cliente.get("perfil_version")).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select cliente_id from compra_boleto where venta_id = ?", UUID.class, compra))
                .isEqualTo(presencial);
        assertThat(escenario.saldo(presencial)).isEqualByComparingTo("0");
    }

    @Test
    void elMismoEventoDosVecesTieneUnSoloEfecto() {
        UUID usuarioId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Map<String, Object> perfil = perfil(usuarioId, Escenario.nit(), "Primero");

        publicador.publicar(eventId, TiposEvento.PERFIL_GUARDADO, usuarioId.toString(), 1, perfil);
        await().atMost(ESPERA).until(() -> publicador.procesado(eventId));
        // Mismo eventId (un reintento del relay) aunque traiga otro contenido: el inbox lo descarta.
        perfil.put("nombreCompleto", "Segundo");
        publicador.publicar(eventId, TiposEvento.PERFIL_GUARDADO, usuarioId.toString(), 2, perfil);
        UUID testigo = aplicar(UUID.randomUUID(), 1, "CF");   // procesado después del duplicado

        assertThat(publicador.procesado(testigo)).isTrue();
        assertThat(clienteDe(usuarioId).get("nombre")).isEqualTo("Primero");
        assertThat(jdbc.queryForObject("select count(*) from cliente where usuario_id = ?", Integer.class, usuarioId))
                .isEqualTo(1);
    }

    @Test
    void unaVersionViejaNoPisaALaNueva() {
        UUID usuarioId = UUID.randomUUID();
        String nit = Escenario.nit();
        aplicar(usuarioId, 2, nit);

        UUID viejo = publicador.publicar(TiposEvento.PERFIL_GUARDADO, usuarioId.toString(), 1,
                perfil(usuarioId, "CF", "Versión 1"));
        await().atMost(ESPERA).until(() -> publicador.procesado(viejo));

        Map<String, Object> cliente = clienteDe(usuarioId);
        assertThat(cliente.get("nit")).isEqualTo(nit);
        assertThat(cliente.get("perfil_version")).isEqualTo(2L);
    }

    @Test
    void alCambiarAUnNitQueComproEnMostradorLaCuentaAbsorbeSusComprasYAlquileres() {
        UUID usuarioId = UUID.randomUUID();
        aplicar(usuarioId, 1, Escenario.nit());
        UUID cuenta = (UUID) clienteDe(usuarioId).get("id");
        escenario.compra(cuenta, "ONLINE");

        String nitNuevo = Escenario.nit();
        UUID presencial = escenario.presencial(nitNuevo);
        UUID compra = escenario.compra(presencial, "PRESENCIAL");
        UUID alquiler = escenario.alquiler(presencial, UUID.randomUUID(), "PRESENCIAL", "FINALIZADO");
        // Una lápida anterior que apuntaba al cliente presencial pasa a apuntar a la cuenta.
        UUID lapidaVieja = UUID.randomUUID();
        jdbc.update("insert into cliente (id, nombre, absorbido_por) values (?, 'Viejo', ?)", lapidaVieja, presencial);

        aplicar(usuarioId, 2, nitNuevo);

        Map<String, Object> cliente = clienteDe(usuarioId);
        assertThat(cliente.get("id")).isEqualTo(cuenta);
        assertThat(cliente.get("nit")).isEqualTo(nitNuevo);
        assertThat(jdbc.queryForObject("select cliente_id from compra_boleto where venta_id = ?", UUID.class, compra))
                .isEqualTo(cuenta);
        assertThat(jdbc.queryForObject("select cliente_id from alquiler where id = ?", UUID.class, alquiler))
                .isEqualTo(cuenta);
        assertThat(jdbc.queryForMap("select nit, absorbido_por from cliente where id = ?", presencial))
                .containsEntry("nit", null).containsEntry("absorbido_por", cuenta);
        assertThat(jdbc.queryForObject("select absorbido_por from cliente where id = ?", UUID.class, lapidaVieja))
                .isEqualTo(cuenta);
        assertThat(jdbc.queryForObject("select count(*) from compra_boleto where cliente_id = ?", Integer.class,
                cuenta)).isEqualTo(2);
    }

    @Test
    void alCambiarACfLaCuentaConservaSuClienteYSueltaElNit() {
        UUID usuarioId = UUID.randomUUID();
        String nit = Escenario.nit();
        aplicar(usuarioId, 1, nit);
        UUID cuenta = (UUID) clienteDe(usuarioId).get("id");
        jdbc.update("update cartera set saldo = 150 where cliente_id = ?", cuenta);

        aplicar(usuarioId, 2, "CF");

        Map<String, Object> cliente = clienteDe(usuarioId);
        assertThat(cliente.get("id")).isEqualTo(cuenta);
        assertThat(cliente.get("nit")).isNull();
        assertThat(escenario.saldo(cuenta)).isEqualByComparingTo("150");
        // El NIT quedó libre: una venta en mostrador con él crea un cliente nuevo, sin cuenta.
        assertThat(clientesInternos.porNit(nit, null).conCuenta()).isFalse();
    }

    /** Solo pasa mientras el cambio de NIT de la otra cuenta va en camino: se reintenta y luego va a la DLQ. */
    @Test
    void elNitDeOtraCuentaSeReintentaYTerminaEnLaDlq() {
        String nit = Escenario.nit();
        aplicar(UUID.randomUUID(), 1, nit);
        int antes = publicador.mensajesEnDlq();

        UUID otra = UUID.randomUUID();
        publicador.publicar(TiposEvento.PERFIL_GUARDADO, otra.toString(), 1, perfil(otra, nit, "Otra"));

        await().atMost(Duration.ofSeconds(40)).until(() -> publicador.mensajesEnDlq() == antes + 1);
        assertThat(jdbc.queryForObject("select count(*) from cliente where usuario_id = ?", Integer.class, otra))
                .isZero();
    }

    /**
     * La UQ de cliente.nit decide entre el upsert de E4 y el de S5 (README, "Carreras y su árbitro"):
     * gane quien gane, queda un solo cliente con ese NIT, y es el de la cuenta.
     */
    @Test
    void ventasEnMostradorYConfirmacionSimultaneasConUnNitNuevoDejanUnSoloCliente() throws Exception {
        String nit = Escenario.nit();
        UUID usuarioId = UUID.randomUUID();
        ExecutorService hilos = Executors.newFixedThreadPool(4);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<UUID>> ventas = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ventas.add(hilos.submit(() -> {
                salida.await();
                return clientesInternos.porNit(nit, "Cajero").clienteId();
            }));
        }
        salida.countDown();
        UUID evento = publicador.publicar(TiposEvento.PERFIL_GUARDADO, usuarioId.toString(), 1,
                perfil(usuarioId, nit, "Dueño del NIT"));
        for (Future<UUID> venta : ventas) {
            venta.get();
        }
        hilos.shutdown();
        await().atMost(ESPERA).until(() -> publicador.procesado(evento));

        assertThat(jdbc.queryForObject("select count(*) from cliente where nit = ?", Integer.class, nit)).isEqualTo(1);
        UUID cliente = (UUID) clienteDe(usuarioId).get("id");
        assertThat(ventas).allSatisfy(v -> assertThat(v.get()).isEqualTo(cliente));
    }

    private UUID aplicar(UUID usuarioId, long version, String nit) {
        UUID eventId = publicador.publicar(TiposEvento.PERFIL_GUARDADO, usuarioId.toString(), version,
                perfil(usuarioId, nit, "Cuenta " + usuarioId));
        await().atMost(ESPERA).until(() -> publicador.procesado(eventId));
        return eventId;
    }

    private Map<String, Object> clienteDe(UUID usuarioId) {
        return jdbc.queryForMap("select * from cliente where usuario_id = ?", usuarioId);
    }

    private static Map<String, Object> perfil(UUID usuarioId, String nit, String nombre) {
        Map<String, Object> perfil = new HashMap<>();
        perfil.put("usuarioId", usuarioId);
        perfil.put("nit", nit);
        perfil.put("nombreCompleto", nombre);
        perfil.put("correo", usuarioId + "@prueba.gt");
        perfil.put("telefono", "55551234");
        perfil.put("saldo", new BigDecimal("1"));   // un campo que customer no conoce se ignora
        return perfil;
    }
}
