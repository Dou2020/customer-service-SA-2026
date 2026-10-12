package gt.cunoc.sa.customer.api.cartera;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import gt.cunoc.sa.customer.api.cartera.dto.CarteraResponse;
import gt.cunoc.sa.customer.api.cartera.dto.MovimientoResponse;
import gt.cunoc.sa.customer.api.cartera.dto.RecargaRequest;
import gt.cunoc.sa.customer.api.error.ErrorDeNegocio;
import gt.cunoc.sa.customer.application.LibroCartera;
import gt.cunoc.sa.customer.application.ResolucionDeClientes;
import gt.cunoc.sa.customer.domain.Calendario;
import gt.cunoc.sa.customer.domain.ClienteEntity;
import gt.cunoc.sa.customer.domain.MovimientoCarteraEntity;
import gt.cunoc.sa.customer.domain.TipoMovimiento;
import gt.cunoc.sa.customer.infrastructure.security.UsuarioActual;
import jakarta.persistence.criteria.Predicate;

/**
 * La cartera propia: la del cliente enlazado a la cuenta (X-User-Id), nunca la de un id que
 * mande el cliente. Cualquier cuenta tiene cliente y cartera, no solo las CUSTOMER: todos los
 * usuarios pueden ser clientes.
 */
@Service
public class CarteraService {

    // Lo que cabe en movimiento_cartera.idempotency_key (64) después del prefijo "RECARGA:".
    private static final Pattern CLAVE = Pattern.compile("[A-Za-z0-9_-]{1,56}");

    private final CarteraRepository carteras;
    private final MovimientoCarteraRepository movimientos;
    private final LibroCartera libro;
    private final ResolucionDeClientes resolucion;
    private final TransactionTemplate transaccion;

    public CarteraService(CarteraRepository carteras, MovimientoCarteraRepository movimientos, LibroCartera libro,
            ResolucionDeClientes resolucion, TransactionTemplate transaccion) {
        this.carteras = carteras;
        this.movimientos = movimientos;
        this.libro = libro;
        this.resolucion = resolucion;
        this.transaccion = transaccion;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public CarteraResponse consultar(UsuarioActual actual) {
        ClienteEntity cliente = resolucion.deLaCuenta(actual.usuarioId());
        return carteras.findById(cliente.getId()).map(CarteraResponse::de)
                .orElseThrow(() -> ErrorDeNegocio.conflicto(ResolucionDeClientes.CUENTA_SIN_CLIENTE));
    }

    /** Sin las lápidas del reconciliador (DEBITO_ANULADO): no movieron dinero. Filtra por la fecha elegida. */
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public Page<MovimientoResponse> movimientos(UsuarioActual actual, LocalDate desde, LocalDate hasta,
            Pageable pagina) {
        ClienteEntity cliente = resolucion.deLaCuenta(actual.usuarioId());
        Specification<MovimientoCarteraEntity> filtro = (raiz, consulta, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            condiciones.add(cb.equal(raiz.get("clienteId"), cliente.getId()));
            condiciones.add(cb.notEqual(raiz.get("tipo"), TipoMovimiento.DEBITO_ANULADO));
            if (desde != null) {
                condiciones.add(cb.greaterThanOrEqualTo(raiz.get("fecha"), desde));
            }
            if (hasta != null) {
                condiciones.add(cb.lessThanOrEqualTo(raiz.get("fecha"), hasta));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
        return movimientos.findAll(filtro, pagina).map(MovimientoResponse::de);
    }

    /**
     * La Idempotency-Key la genera el frontend por cada recarga: un doble clic o un reintento
     * con la misma clave devuelve la recarga original y no acredita dos veces.
     *
     * Sin @Transactional a propósito: si dos requests con la misma clave se cruzan, la UQ deja
     * entrar a una y revierte entera la transacción de la otra; la respuesta sale de releer la
     * clave en una transacción nueva.
     */
    @PreAuthorize("isAuthenticated()")
    public Recarga recargar(UsuarioActual actual, String idempotencyKey, RecargaRequest solicitud) {
        if (idempotencyKey == null || !CLAVE.matcher(idempotencyKey).matches()) {
            throw ErrorDeNegocio.solicitudInvalida(
                    "Falta el encabezado Idempotency-Key, o no es válido (hasta 56 letras, números, guiones o _)");
        }
        String clave = LibroCartera.claveRecarga(idempotencyKey);
        LocalDate fecha = solicitud.fecha() == null ? Calendario.hoy() : solicitud.fecha();
        try {
            return transaccion.execute(estado -> {
                ClienteEntity cliente = resolucion.deLaCuenta(actual.usuarioId());
                return movimientos.findByIdempotencyKey(clave)
                        .map(previa -> repetida(previa, cliente, solicitud))
                        .orElseGet(() -> new Recarga(MovimientoResponse.de(libro.acreditar(cliente.getId(),
                                TipoMovimiento.RECARGA, solicitud.monto(), fecha, null, clave)), true));
            });
        } catch (DataIntegrityViolationException e) {
            return transaccion.execute(estado -> repetida(movimientos.findByIdempotencyKey(clave).orElseThrow(() -> e),
                    resolucion.deLaCuenta(actual.usuarioId()), solicitud));
        }
    }

    private static Recarga repetida(MovimientoCarteraEntity previa, ClienteEntity cliente, RecargaRequest solicitud) {
        if (previa.getTipo() != TipoMovimiento.RECARGA || !previa.getClienteId().equals(cliente.getId())
                || previa.getMonto().compareTo(solicitud.monto()) != 0) {
            throw ErrorDeNegocio.conflicto("Esa Idempotency-Key ya se usó en otra operación");
        }
        return new Recarga(MovimientoResponse.de(previa), false);
    }

    /** @param nueva false si es la respuesta de una recarga que ya se había registrado con esa clave */
    public record Recarga(MovimientoResponse movimiento, boolean nueva) {
    }
}
