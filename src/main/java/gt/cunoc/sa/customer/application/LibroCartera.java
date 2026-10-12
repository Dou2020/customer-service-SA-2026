package gt.cunoc.sa.customer.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import gt.cunoc.sa.customer.api.cartera.CarteraRepository;
import gt.cunoc.sa.customer.api.cartera.MovimientoCarteraRepository;
import gt.cunoc.sa.customer.domain.Calendario;
import gt.cunoc.sa.customer.domain.MovimientoCarteraEntity;
import gt.cunoc.sa.customer.domain.TipoMovimiento;

/**
 * El libro de la cartera: cada cambio de saldo es un UPDATE condicional más una fila con su
 * Idempotency-Key, en la transacción de quien llama. La UQ de idempotency_key es el árbitro: si
 * dos operaciones con la misma clave se cruzan, el INSERT de la segunda falla y su transacción se
 * revierte entera, también el UPDATE del saldo. Quien llama decide qué responder releyendo la
 * clave en otra transacción (CarteraService.recargar, DebitosService).
 *
 * Se descartó un solo SQL con UPDATE e INSERT … ON CONFLICT DO NOTHING en un CTE: el UPDATE se
 * aplica aunque el INSERT no entre, y se cobraría dos veces.
 */
@Component
public class LibroCartera {

    public static final String SALDO_INSUFICIENTE = "No tienes saldo suficiente en tu cartera. Recárgala e intenta de nuevo";

    private final CarteraRepository carteras;
    private final MovimientoCarteraRepository movimientos;

    public LibroCartera(CarteraRepository carteras, MovimientoCarteraRepository movimientos) {
        this.carteras = carteras;
        this.movimientos = movimientos;
    }

    /** Claves de idempotencia: el prefijo evita que una recarga y un débito de sales choquen por casualidad. */
    public static String claveRecarga(String idempotencyKey) {
        return "RECARGA:" + idempotencyKey;
    }

    public static String clavePagoAlquiler(UUID alquilerId) {
        return "ALQUILER:" + alquilerId;
    }

    public static String claveReembolsoVenta(UUID ventaId) {
        return "REEMBOLSO:" + ventaId;
    }

    public static String claveReembolsoAlquiler(UUID alquilerId) {
        return "REEMBOLSO-ALQUILER:" + alquilerId;
    }

    /** RECARGA o REEMBOLSO. Quien llama ya comprobó que el cliente tiene cartera. */
    @Transactional(propagation = Propagation.MANDATORY)
    public MovimientoCarteraEntity acreditar(UUID clienteId, TipoMovimiento tipo, BigDecimal monto, LocalDate fecha,
            UUID referenciaId, String clave) {
        BigDecimal saldo = carteras.acreditar(clienteId, monto)
                .orElseThrow(() -> new IllegalStateException("El cliente " + clienteId + " no tiene cartera"));
        // saveAndFlush: si la clave ya existe, la UQ falla aquí y no al confirmar.
        return movimientos.saveAndFlush(new MovimientoCarteraEntity(clienteId, tipo, monto, fecha, referenciaId, clave,
                saldo, Instant.now()));
    }

    /**
     * DEBITO_BOLETO o DEBITO_ALQUILER. Quien llama ya comprobó que el cliente tiene cartera.
     *
     * @return el movimiento, o vacío si el saldo no alcanza
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<MovimientoCarteraEntity> debitar(UUID clienteId, TipoMovimiento tipo, BigDecimal monto,
            LocalDate fecha, UUID referenciaId, String clave) {
        return carteras.debitar(clienteId, monto).map(saldo -> movimientos.saveAndFlush(
                new MovimientoCarteraEntity(clienteId, tipo, monto, fecha, referenciaId, clave, saldo, Instant.now())));
    }

    /**
     * La lápida del reconciliador de sales (README, "Compra de boletos en línea"): DEBITO_ANULADO
     * con monto 0 y la clave del débito incierto.
     *
     * @return true si entró (el débito no existía y ya no podrá entrar); false si la clave ya existía
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean anular(UUID clienteId, String clave) {
        return movimientos.insertarLapida(clienteId, clave, Calendario.hoy()) == 1;
    }

    /**
     * Devuelve exactamente lo que se debitó con claveDebito, a la cartera de la que salió. Sin
     * débito no hay nada que devolver: venta en mostrador, alquiler pagado en efectivo o un pago
     * que el reconciliador anuló. Idempotente por claveReembolso.
     *
     * @return el reembolso, o vacío si no había débito
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<MovimientoCarteraEntity> reembolsar(String claveDebito, String claveReembolso, LocalDate fecha) {
        Optional<MovimientoCarteraEntity> hecho = movimientos.findByIdempotencyKey(claveReembolso);
        if (hecho.isPresent()) {
            return hecho;
        }
        return movimientos.findByIdempotencyKey(claveDebito)
                .filter(m -> m.getTipo() == TipoMovimiento.DEBITO_BOLETO || m.getTipo() == TipoMovimiento.DEBITO_ALQUILER)
                .map(debito -> acreditar(debito.getClienteId(), TipoMovimiento.REEMBOLSO, debito.getMonto(), fecha,
                        debito.getReferenciaId(), claveReembolso));
    }
}
