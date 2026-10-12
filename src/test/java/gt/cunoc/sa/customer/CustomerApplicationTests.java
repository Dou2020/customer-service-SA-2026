package gt.cunoc.sa.customer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Si el contexto levanta, Flyway aplicó las migraciones y Hibernate validó que las entidades
 * coinciden con el esquema (ddl-auto=validate falla al arrancar si no).
 */
@PruebaDeIntegracion
class CustomerApplicationTests {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void aplicaLasMigraciones() {
        List<String> tablas = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'", String.class);

        assertThat(tablas).contains("tarifa_alquiler", "cliente", "cartera", "movimiento_cartera", "alquiler",
                "compra_boleto", "outbox_event", "processed_event");
        assertThat(jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success", Integer.class)).isEqualTo(2);
    }

    /** El cliente genérico de las ventas sin NIT existe desde la migración, sin cuenta ni cartera. */
    @Test
    void creaElClienteGenerico() {
        assertThat(jdbc.queryForObject("""
                select count(*) from cliente c
                where c.nit = 'CF' and c.generico and c.usuario_id is null
                  and not exists (select 1 from cartera where cliente_id = c.id)""",
                Integer.class)).isEqualTo(1);
    }
}
