package gt.cunoc.sa.customer.api.interno;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import gt.cunoc.sa.customer.api.cartera.CarteraRepository;
import gt.cunoc.sa.customer.api.cartera.MovimientoCarteraRepository;
import gt.cunoc.sa.customer.api.error.ErrorDeNegocio;
import gt.cunoc.sa.customer.api.interno.dto.AnulacionResponse;
import gt.cunoc.sa.customer.api.interno.dto.DebitoRequest;
import gt.cunoc.sa.customer.api.interno.dto.DebitoResponse;
import gt.cunoc.sa.customer.api.interno.dto.ResultadoDebito;
import gt.cunoc.sa.customer.application.LibroCartera;
import gt.cunoc.sa.customer.domain.Calendario;
import gt.cunoc.sa.customer.domain.MovimientoCarteraEntity;
import gt.cunoc.sa.customer.domain.TipoMovimiento;

/**
 * S4: el débito de la cartera que paga una compra de boletos en línea, y su resolución cuando quedó
 * incierto (README, "Compra de boletos en línea"). La Idempotency-Key es el ventaId: sales puede
 * reintentar el mismo débito sin cobrar dos veces.
 */
@Service
public class DebitosService {

    private final CarteraRepository carteras;
    private final MovimientoCarteraRepository movimientos;
    private final LibroCartera libro;
    private final TransactionTemplate transaccion;

    public DebitosService(CarteraRepository carteras, MovimientoCarteraRepository movimientos, LibroCartera libro,
            TransactionTemplate transaccion) {
        this.carteras = carteras;
        this.movimientos = movimientos;
        this.libro = libro;
        this.transaccion = transaccion;
    }

    /**
     * Sin @Transactional a propósito, como la recarga: si el INSERT choca con la UQ (el mismo débito
     * reintentado a la vez, o la lápida del reconciliador), su transacción se revierte entera, con
     * el UPDATE del saldo, y la respuesta sale de releer la clave.
     */
    public Debito debitar(UUID clienteId, String clave, DebitoRequest solicitud) {
        exigirClave(clave);
        try {
            return transaccion.execute(estado -> movimientos.findByIdempotencyKey(clave)
                    .map(previo -> repetido(previo, clienteId, solicitud))
                    .orElseGet(() -> aplicar(clienteId, clave, solicitud)));
        } catch (DataIntegrityViolationException e) {
            return transaccion.execute(estado -> repetido(movimientos.findByIdempotencyKey(clave).orElseThrow(() -> e),
                    clienteId, solicitud));
        }
    }

    /**
     * El reconciliador no consulta si el débito existe: inserta la lápida. Si el débito ya había
     * entrado, la lápida choca con su clave y la respuesta es APLICADO; si entra, es ANULADO y un
     * débito tardío con esa clave ya no puede aplicarse. La UQ decide, sin ventana entre consultar y
     * actuar.
     */
    @Transactional
    public AnulacionResponse anular(UUID clienteId, String clave) {
        exigirClave(clave);
        exigirCartera(clienteId);
        if (libro.anular(clienteId, clave)) {
            return new AnulacionResponse(ResultadoDebito.ANULADO);
        }
        MovimientoCarteraEntity existente = movimientos.findByIdempotencyKey(clave)
                .orElseThrow(() -> new IllegalStateException("La clave " + clave + " no entró ni existe"));
        if (existente.getTipo() == TipoMovimiento.DEBITO_ANULADO) {
            return new AnulacionResponse(ResultadoDebito.ANULADO);
        }
        if (existente.getTipo() == TipoMovimiento.DEBITO_BOLETO && existente.getClienteId().equals(clienteId)) {
            return new AnulacionResponse(ResultadoDebito.APLICADO);
        }
        throw ErrorDeNegocio.conflicto("Esa Idempotency-Key ya se usó en otra operación");
    }

    private Debito aplicar(UUID clienteId, String clave, DebitoRequest solicitud) {
        exigirCartera(clienteId);
        LocalDate fecha = solicitud.fecha() == null ? Calendario.hoy() : solicitud.fecha();
        return libro.debitar(clienteId, TipoMovimiento.DEBITO_BOLETO, solicitud.monto(), fecha,
                        solicitud.referenciaId(), clave)
                .map(debito -> new Debito(DebitoResponse.aplicado(debito), true))
                .orElseThrow(() -> ErrorDeNegocio.noProcesable(LibroCartera.SALDO_INSUFICIENTE));
    }

    /** Un reintento del mismo débito responde lo mismo que la primera vez; otra cosa con la misma clave, 409. */
    private static Debito repetido(MovimientoCarteraEntity previo, UUID clienteId, DebitoRequest solicitud) {
        if (previo.getTipo() == TipoMovimiento.DEBITO_ANULADO) {
            throw ErrorDeNegocio.conflicto("Este pago se anuló porque no se pudo confirmar a tiempo. "
                    + "Intenta la compra de nuevo");
        }
        if (previo.getTipo() != TipoMovimiento.DEBITO_BOLETO || !previo.getClienteId().equals(clienteId)
                || previo.getMonto().compareTo(solicitud.monto()) != 0) {
            throw ErrorDeNegocio.conflicto("Esa Idempotency-Key ya se usó en otra operación");
        }
        return new Debito(DebitoResponse.aplicado(previo), false);
    }

    private void exigirCartera(UUID clienteId) {
        if (!carteras.existsById(clienteId)) {
            throw ErrorDeNegocio.noEncontrado("Ese cliente no tiene cartera");
        }
    }

    private static void exigirClave(String clave) {
        if (clave == null || clave.isBlank() || clave.length() > 64) {
            throw ErrorDeNegocio.solicitudInvalida("Falta el encabezado Idempotency-Key, o tiene más de 64 caracteres");
        }
    }

    /** @param nuevo false si es la respuesta de un débito que ya se había aplicado con esa clave */
    public record Debito(DebitoResponse respuesta, boolean nuevo) {
    }
}
