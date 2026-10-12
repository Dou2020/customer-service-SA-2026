package gt.cunoc.sa.customer.application;

import java.time.LocalDate;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import gt.cunoc.sa.customer.api.alquiler.AlquilerRepository;
import gt.cunoc.sa.customer.domain.AlquilerEntity;
import gt.cunoc.sa.customer.domain.ClienteEntity;
import gt.cunoc.sa.customer.domain.Nit;
import gt.cunoc.sa.customer.infrastructure.messaging.Outbox;
import gt.cunoc.sa.customer.infrastructure.messaging.eventos.AlquilerCancelado;
import gt.cunoc.sa.customer.infrastructure.messaging.eventos.AlquilerCancelado.Reembolso;
import gt.cunoc.sa.customer.infrastructure.messaging.eventos.AlquilerConfirmado;
import gt.cunoc.sa.customer.infrastructure.messaging.eventos.AlquilerPagado;
import gt.cunoc.sa.customer.infrastructure.messaging.eventos.AlquilerPagado.MetodoPago;
import gt.cunoc.sa.customer.infrastructure.messaging.eventos.TiposEvento;

/**
 * Lo que customer publica de un alquiler (E13, E14 y E16), en un solo lugar porque lo emiten los
 * endpoints y también ViajeEliminado (E10). Va a la outbox en la transacción del cambio.
 *
 * aggregateVersion = la @Version del alquiler después del flush: los consumidores descartan un
 * evento más viejo que el último que aplicaron.
 */
@Component
public class EventosDeAlquiler {

    private final Outbox outbox;
    private final AlquilerRepository alquileres;

    public EventosDeAlquiler(Outbox outbox, AlquilerRepository alquileres) {
        this.outbox = outbox;
        this.alquileres = alquileres;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void confirmado(AlquilerEntity a, ClienteEntity c) {
        alquileres.flush();
        outbox.registrar(TiposEvento.ALQUILER_CONFIRMADO, a.getId().toString(), a.getVersion(),
                new AlquilerConfirmado(a.getId(), a.getSucursalId(), c.getCorreo(), c.getNombre(), a.getOrigen(),
                        a.getDestino(), a.getFechaSalida(), a.getFechaRetorno(), a.getPrecioFinal()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void pagado(AlquilerEntity a, ClienteEntity c, MetodoPago metodo) {
        alquileres.flush();
        var t = a.getTrayecto();
        outbox.registrar(TiposEvento.ALQUILER_PAGADO, a.getId().toString(), a.getVersion(),
                new AlquilerPagado(a.getId(), c.getId(), a.getSucursalId(), c.getNombre(),
                        c.getNit() == null ? Nit.CONSUMIDOR_FINAL : c.getNit(), c.getCorreo(), t.origen(),
                        t.origenLat(), t.origenLng(), t.destino(), t.destinoLat(), t.destinoLng(), a.getDistanciaKm(),
                        a.getFechaSalida(), a.getFechaRetorno(), a.isIdaYVuelta(), a.getPasajeros(),
                        a.getPrecioFinal(), a.getFechaPago(), metodo));
    }

    /** @param estabaPagado si se había cobrado; el estado ya es CANCELADO cuando se llama */
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelado(AlquilerEntity a, ClienteEntity c, boolean estabaPagado, LocalDate fecha,
            Reembolso reembolso) {
        alquileres.flush();
        outbox.registrar(TiposEvento.ALQUILER_CANCELADO, a.getId().toString(), a.getVersion(),
                new AlquilerCancelado(a.getId(), a.getSucursalId(), c.getCorreo(), c.getNombre(), estabaPagado,
                        a.getPrecioFinal() == null ? a.getPrecioEstimado() : a.getPrecioFinal(),
                        estabaPagado ? a.getFechaPago() : null, fecha, reembolso, a.getMotivo()));
    }
}
