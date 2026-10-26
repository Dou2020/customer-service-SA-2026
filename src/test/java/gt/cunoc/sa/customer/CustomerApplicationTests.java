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
                "select count(*) from flyway_schema_history where success", Integer.class)).isEqualTo(3);
    }

    /**
     * V3: los ids de fleet, trip y sales son texto y la placa mide lo que en fleet; los propios y los
     * de identity siguen siendo uuid.
     */
    @Test
    void losIdsDeFleetTripYSalesSonTexto() {
        List<String> columnas = jdbc.queryForList("""
                select table_name || '.' || column_name || ' ' || data_type || coalesce('(' || character_maximum_length || ')', '')
                from information_schema.columns
                where table_schema = 'public'
                  and (table_name, column_name) in (('compra_boleto', 'venta_id'), ('compra_boleto', 'viaje_id'),
                       ('alquiler', 'viaje_id'), ('alquiler', 'bus_id'), ('alquiler', 'chofer_id'),
                       ('movimiento_cartera', 'referencia_id'), ('movimiento_cartera', 'idempotency_key'),
                       ('alquiler', 'bus_placa'), ('alquiler', 'sucursal_id'), ('compra_boleto', 'cliente_id'))""",
                String.class);

        assertThat(columnas).containsExactlyInAnyOrder(
                "compra_boleto.venta_id character varying(64)", "compra_boleto.viaje_id character varying(64)",
                "alquiler.viaje_id character varying(64)", "alquiler.bus_id character varying(64)",
                "alquiler.chofer_id character varying(64)", "movimiento_cartera.referencia_id character varying(64)",
                "movimiento_cartera.idempotency_key character varying(80)",
                "alquiler.bus_placa character varying(20)",
                "alquiler.sucursal_id uuid", "compra_boleto.cliente_id uuid");
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
