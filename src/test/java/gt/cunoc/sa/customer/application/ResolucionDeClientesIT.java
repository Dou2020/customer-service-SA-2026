package gt.cunoc.sa.customer.application;

import static gt.cunoc.sa.customer.Escenario.como;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import gt.cunoc.sa.customer.api.interno.ClientesInternosService;

/** S5 (lo que llama sales) y la búsqueda de clientes del mostrador. */
@PruebaDeIntegracion
class ResolucionDeClientesIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    Escenario escenario;
    @Autowired
    ClientesInternosService clientesInternos;

    @Test
    void sinNitOConCfLaVentaVaAlClienteGenerico() throws Exception {
        String generico = escenario.generico().toString();
        resolver(Map.of()).andExpect(status().isOk()).andExpect(jsonPath("$.clienteId").value(generico))
                .andExpect(jsonPath("$.generico").value(true));
        resolver(Map.of("nit", " c-f ")).andExpect(jsonPath("$.clienteId").value(generico));
    }

    @Test
    void unNitNuevoCreaElClienteYDespuesDevuelveElMismo() throws Exception {
        String nit = Escenario.nit();
        String escrito = nit.substring(0, nit.length() - 1) + "-" + nit.charAt(nit.length() - 1);

        String id = json.readTree(resolver(Map.of("nit", escrito, "nombre", "Ana López"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nit").value(nit))
                .andExpect(jsonPath("$.nombre").value("Ana López"))
                .andExpect(jsonPath("$.conCuenta").value(false))
                .andReturn().getResponse().getContentAsString()).get("clienteId").asText();

        // El segundo cajero escribe otro nombre: el cliente ya existe y no se renombra.
        resolver(Map.of("nit", nit, "nombre", "Otro")).andExpect(jsonPath("$.clienteId").value(id))
                .andExpect(jsonPath("$.nombre").value("Ana López"));
    }

    @Test
    void unNitDeUnaCuentaDevuelveElClienteDeLaCuenta() throws Exception {
        Cuenta cuenta = escenario.cuenta(BigDecimal.ZERO);
        resolver(Map.of("nit", cuenta.nit())).andExpect(jsonPath("$.clienteId").value(cuenta.clienteId().toString()))
                .andExpect(jsonPath("$.conCuenta").value(true));
    }

    @Test
    void unNitConDigitoVerificadorInvalidoResponde422() throws Exception {
        String nit = Escenario.nit();
        char malo = nit.charAt(nit.length() - 1) == '0' ? '1' : '0';
        resolver(Map.of("nit", nit.substring(0, nit.length() - 1) + malo))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("El NIT no es válido: revisa el dígito verificador"));
    }

    @Test
    void dosCajerosConElMismoNitNuevoDejanUnSoloCliente() throws Exception {
        String nit = Escenario.nit();
        ExecutorService hilos = Executors.newFixedThreadPool(6);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<UUID>> resultados = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            resultados.add(hilos.submit(() -> {
                salida.await();
                return clientesInternos.porNit(nit, null).clienteId();
            }));
        }
        salida.countDown();
        UUID primero = resultados.get(0).get();
        for (Future<UUID> resultado : resultados) {
            assertThat(resultado.get()).isEqualTo(primero);
        }
        hilos.shutdown();
        assertThat(jdbc.queryForObject("select count(*) from cliente where nit = ?", Integer.class, nit)).isEqualTo(1);
    }

    @Test
    void elClienteDeUnaCuentaResponde409HastaQueLlegueSuPerfil() throws Exception {
        mvc.perform(get("/internal/clientes/por-usuario/{id}", UUID.randomUUID()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(ResolucionDeClientes.CUENTA_SIN_CLIENTE));

        Cuenta cuenta = escenario.cuenta(BigDecimal.ZERO);
        mvc.perform(get("/internal/clientes/por-usuario/{id}", cuenta.actual().usuarioId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clienteId").value(cuenta.clienteId().toString()));
    }

    @Test
    void elPersonalBuscaClientesPorNitYUnClienteNo() throws Exception {
        String nit = Escenario.nit();
        UUID presencial = escenario.presencial(nit);

        mvc.perform(get("/api/clientes").param("nit", nit).with(como(Escenario.cajeroDe(UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(presencial.toString()))
                .andExpect(jsonPath("$.page.totalElements").value(1));
        mvc.perform(get("/api/clientes").param("nit", nit).with(como(Escenario.cliente())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/clientes")).andExpect(status().isUnauthorized());
    }

    private ResultActions resolver(Map<String, ?> cuerpo) throws Exception {
        return mvc.perform(post("/internal/clientes/resolucion").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(cuerpo)));
    }
}
