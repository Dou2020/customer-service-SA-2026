package gt.cunoc.sa.customer;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import gt.cunoc.sa.customer.domain.Rol;
import gt.cunoc.sa.customer.infrastructure.security.UsuarioActual;

/**
 * Datos de prueba escritos directo en la base (sin eventos): cada prueba arma lo suyo con
 * NIT y códigos únicos, porque todas comparten la misma base. Los usuarios no viven aquí, solo
 * llegan en los X-User-* del gateway: las fábricas de UsuarioActual usan ids al azar.
 */
@TestComponent
public class Escenario {

    /** Tarifas base vigentes desde esta fecha; las pruebas que necesitan otras usan fechas propias. */
    public static final LocalDate DESDE_TARIFAS_BASE = LocalDate.of(2000, 1, 1);
    public static final BigDecimal TARIFA_KM_BASE = new BigDecimal("5.0000");
    public static final BigDecimal TARIFA_DIA_BASE = new BigDecimal("500.0000");

    private static final AtomicInteger CONTADOR = new AtomicInteger(ThreadLocalRandom.current().nextInt(100_000));

    private final JdbcTemplate jdbc;

    public Escenario(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Un cliente con cuenta (como lo deja PerfilGuardado), con NIT propio y cartera con ese saldo. */
    public Cuenta cuenta(BigDecimal saldo) {
        UsuarioActual actual = cliente();
        UUID id = UUID.randomUUID();
        String nit = nit();
        jdbc.update("""
                insert into cliente (id, nit, nombre, correo, telefono, usuario_id, perfil_version)
                values (?, ?, ?, ?, '55550000', ?, 1)""", id, nit, "Cliente " + nit, correo(), actual.usuarioId());
        jdbc.update("insert into cartera (cliente_id, saldo) values (?, ?)", id, saldo);
        return new Cuenta(actual, id, nit);
    }

    /** Un cliente de mostrador: NIT, sin cuenta ni cartera. */
    public UUID presencial(String nit) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into cliente (id, nit, nombre) values (?, ?, ?)", id, nit, "Mostrador " + nit);
        return id;
    }

    public UUID generico() {
        return jdbc.queryForObject("select id from cliente where generico", UUID.class);
    }

    /** Una compra de boletos ya replicada (como la deja BoletosVendidos), con ids numéricos como los de sales y trip. */
    public String compra(UUID clienteId, String canal) {
        String ventaId = idNumerico();
        jdbc.update("""
                insert into compra_boleto (venta_id, cliente_id, canal, viaje_id, origen, destino, salida, asientos,
                                           total, fecha_pago, estado, version)
                values (?, ?, ?, ?, 'Quetzaltenango', 'San Marcos', ?, '{1,2}', 70.00, ?, 'CONFIRMADA', 1)""",
                ventaId, clienteId, canal, idNumerico(), Timestamp.from(Instant.parse("2026-10-20T14:00:00Z")),
                LocalDate.of(2026, 10, 15));
        return ventaId;
    }

    /** Un alquiler en el estado indicado; desde CONFIRMADO tiene precio final 1 000.00, y si se pagó, fecha de pago. */
    public UUID alquiler(UUID clienteId, UUID sucursalId, String canal, String estado) {
        UUID id = UUID.randomUUID();
        boolean conPrecio = !estado.equals("SOLICITADO") && !estado.equals("RECHAZADO");
        boolean pagado = conPrecio && !estado.equals("CONFIRMADO") && !estado.equals("CANCELADO");
        jdbc.update("""
                insert into alquiler (id, cliente_id, sucursal_id, canal, origen, origen_lat, origen_lng, destino,
                                      destino_lat, destino_lng, distancia_km, fecha_solicitud, fecha_salida,
                                      ida_y_vuelta, pasajeros, precio_estimado, precio_final, estado, fecha_pago)
                values (?, ?, ?, ?, 'Quetzaltenango', 14.834999, -91.518611, 'Huehuetenango', 15.319722, -91.470833,
                        70.00, ?, ?, false, 30, 850.00, ?, ?, ?)""",
                id, clienteId, sucursalId, canal, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 30),
                conPrecio ? new BigDecimal("1000.00") : null, estado, pagado ? LocalDate.of(2026, 10, 2) : null);
        return id;
    }

    /** ALQUILER_KM y ALQUILER_DIA vigentes desde el 2000, con ids que identity no alcanza. Idempotente. */
    public void tarifasBase() {
        jdbc.update("""
                insert into tarifa_alquiler (id, tipo, valor, vigente_desde) values
                    (900000001, 'ALQUILER_KM', ?, ?), (900000002, 'ALQUILER_DIA', ?, ?)
                on conflict do nothing""", TARIFA_KM_BASE, DESDE_TARIFAS_BASE, TARIFA_DIA_BASE, DESDE_TARIFAS_BASE);
    }

    public BigDecimal saldo(UUID clienteId) {
        return jdbc.queryForObject("select saldo from cartera where cliente_id = ?", BigDecimal.class, clienteId);
    }

    /** Un cliente con cuenta: quién es en los X-User-* y cuál es su cliente. */
    public record Cuenta(UsuarioActual actual, UUID clienteId, String nit) {
    }

    public static UsuarioActual sysAdmin() {
        return actual(Rol.SYS_ADMIN, null, null);
    }

    public static UsuarioActual adminDe(UUID sucursal) {
        return actual(Rol.BRANCH_ADMIN, sucursal, null);
    }

    public static UsuarioActual cajeroDe(UUID sucursal) {
        return actual(Rol.CASHIER, sucursal, null);
    }

    public static UsuarioActual cliente() {
        return actual(Rol.CUSTOMER, null, null);
    }

    /** Igual que el JWT de un DRIVER: trae choferId (el id de fleet, como texto) y no sucursal. */
    public static UsuarioActual chofer(String choferId) {
        return actual(Rol.DRIVER, null, choferId);
    }

    private static UsuarioActual actual(Rol rol, UUID sucursal, String chofer) {
        return new UsuarioActual(UUID.randomUUID(), sucursal, chofer, Set.of(rol.name()));
    }

    /** Los encabezados que escribe el gateway. */
    public static RequestPostProcessor como(UsuarioActual actual) {
        return request -> {
            request.addHeader("X-User-Id", actual.usuarioId().toString());
            request.addHeader("X-User-Roles", String.join(",", actual.roles()));
            if (actual.sucursalId() != null) {
                request.addHeader("X-Sucursal-Id", actual.sucursalId().toString());
            }
            if (actual.choferId() != null) {
                request.addHeader("X-Chofer-Id", actual.choferId());
            }
            return request;
        };
    }

    /** Para llamar casos de uso con @PreAuthorize fuera de un request (pruebas con hilos). */
    public static void autenticar(UsuarioActual actual) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                actual, null, actual.roles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList()));
    }

    /** Un código único entre pruebas (referencias, idempotency keys de prueba). */
    public static String codigo(String prefijo) {
        return prefijo + CONTADOR.incrementAndGet() + "-" + ThreadLocalRandom.current().nextInt(1_000_000);
    }

    /**
     * Un id como los de fleet, trip y sales (bigserial), distinto en cada llamada: para las ventas,
     * que son la PK de compra_boleto en la base compartida.
     */
    public static String idNumerico() {
        return Long.toString(1_000_000L * CONTADOR.incrementAndGet() + ThreadLocalRandom.current().nextInt(1_000_000));
    }

    /** Un NIT distinto en cada llamada. */
    public static String nit() {
        return Integer.toString(10_000_000 + CONTADOR.incrementAndGet() * 7
                + ThreadLocalRandom.current().nextInt(7));
    }

    public static String correo() {
        return codigo("cliente") + "@prueba.gt";
    }

    public static String dpi() {
        return Long.toString(1_000_000_000_000L + ThreadLocalRandom.current().nextLong(9_000_000_000_000L));
    }
}
