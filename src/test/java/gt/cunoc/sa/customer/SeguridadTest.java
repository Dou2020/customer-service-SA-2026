package gt.cunoc.sa.customer;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** La cadena de seguridad: sin X-User-* responde 401; con ellos la request pasa. */
@PruebaDeIntegracion
class SeguridadTest {

    @Autowired
    MockMvc mvc;

    @Test
    void sinEncabezadosDeIdentidadResponde401ComoProblemDetail() throws Exception {
        mvc.perform(get("/api/alquileres"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    /** Un 404 de un usuario autenticado sale como 404 y no como 401 (despacho ERROR permitido). */
    @Test
    void conEncabezadosDelGatewayPasaLaSeguridad() throws Exception {
        mvc.perform(get("/api/no-existe")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Roles", "SYS_ADMIN"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void unUsuarioQueNoEsUuidSeTrataComoAnonimo() throws Exception {
        mvc.perform(get("/api/alquileres")
                        .header("X-User-Id", "admin")
                        .header("X-User-Roles", "SYS_ADMIN"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * El chofer es un id de fleet, como texto ("3"), no un UUID: un DRIVER queda autenticado (su
     * cuenta todavía sin cliente da 409, no 401, que haría al frontend cerrar la sesión).
     */
    @Test
    void unChoferConIdNumericoQuedaAutenticado() throws Exception {
        mvc.perform(get("/api/cartera")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Roles", "DRIVER")
                        .header("X-Chofer-Id", "3"))
                .andExpect(status().isConflict());
    }

    @Test
    void healthEsPublico() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void propagaElCorrelationIdOGeneraUno() throws Exception {
        mvc.perform(get("/actuator/health").header("X-Correlation-Id", "abc-123"))
                .andExpect(header().string("X-Correlation-Id", "abc-123"));
        mvc.perform(get("/actuator/health").header("X-Correlation-Id", "no valido; con espacios"))
                .andExpect(header().string("X-Correlation-Id", not("no valido; con espacios")));
    }
}
