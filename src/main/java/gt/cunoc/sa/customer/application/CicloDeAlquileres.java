package gt.cunoc.sa.customer.application;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import gt.cunoc.sa.customer.api.alquiler.AlquilerRepository;
import gt.cunoc.sa.customer.api.cliente.ClienteRepository;
import gt.cunoc.sa.customer.domain.AlquilerEntity;
import gt.cunoc.sa.customer.domain.Calendario;
import gt.cunoc.sa.customer.domain.ClienteEntity;
import gt.cunoc.sa.customer.domain.MovimientoCarteraEntity;
import gt.cunoc.sa.customer.infrastructure.messaging.EventoNoProcesableException;
import gt.cunoc.sa.customer.infrastructure.messaging.Inbox;
import gt.cunoc.sa.customer.infrastructure.messaging.Payloads;
import gt.cunoc.sa.customer.infrastructure.messaging.Sobre;
import gt.cunoc.sa.customer.infrastructure.messaging.eventos.AlquilerAsignado;
import gt.cunoc.sa.customer.infrastructure.messaging.eventos.AlquilerCancelado.Reembolso;
import gt.cunoc.sa.customer.infrastructure.messaging.eventos.ViajeDeAlquiler;

/**
 * El alquiler sigue a su viaje en trip (README, "Ciclo de vida de un alquiler"): AlquilerAsignado
 * (E15), ViajeIniciado (E8), ViajeFinalizado (E9) y ViajeEliminado (E10). Los estados solo
 * avanzan, así que un evento repetido o que llega tarde no tiene efecto. Cada uno en una
 * transacción con su inbox.
 */
@Service
public class CicloDeAlquileres {

    private static final Logger log = LoggerFactory.getLogger(CicloDeAlquileres.class);

    private final Inbox inbox;
    private final AlquilerRepository alquileres;
    private final ClienteRepository clientes;
    private final LibroCartera libro;
    private final EventosDeAlquiler eventos;
    private final ObjectMapper json;

    public CicloDeAlquileres(Inbox inbox, AlquilerRepository alquileres, ClienteRepository clientes,
            LibroCartera libro, EventosDeAlquiler eventos, ObjectMapper json) {
        this.inbox = inbox;
        this.alquileres = alquileres;
        this.clientes = clientes;
        this.libro = libro;
        this.eventos = eventos;
        this.json = json;
    }

    @Transactional
    public void asignado(Sobre sobre) {
        if (!inbox.primeraVez(sobre.eventId())) {
            return;
        }
        AlquilerAsignado asignacion = Payloads.leer(json, sobre, AlquilerAsignado.class);
        Payloads.exigir(asignacion.alquilerId() != null, sobre, "alquilerId");
        String viajeId = Payloads.exigirId(asignacion.viajeId(), sobre, "viajeId");
        String busId = Payloads.exigirId(asignacion.busId(), sobre, "busId");
        String choferId = Payloads.exigirId(asignacion.choferId(), sobre, "choferId");
        AlquilerEntity alquiler = buscar(asignacion.alquilerId(), sobre);
        if (!alquiler.asignar(viajeId, busId, asignacion.placa(), choferId, asignacion.choferNombre(),
                sobre.aggregateVersion())) {
            log.info("AlquilerAsignado v{} del alquiler {} no aplica ({}); se descarta", sobre.aggregateVersion(),
                    alquiler.getId(), alquiler.getEstado());
        }
    }

    @Transactional
    public void iniciado(Sobre sobre) {
        avanzar(sobre, (alquiler, viaje) -> alquiler.iniciar(viaje.viajeId()));
    }

    @Transactional
    public void finalizado(Sobre sobre) {
        avanzar(sobre, (alquiler, viaje) -> alquiler.finalizar(viaje.viajeId()));
    }

    /**
     * Después del pago, el alquiler se cancela eliminando su viaje (supuesto 12). Si se pagó con la
     * cartera se reembolsa ahí mismo, en esta transacción; si se pagó en efectivo, la devolución es
     * en caja. AlquilerCancelado (E16) le dice a finance que reverse el ingreso.
     */
    @Transactional
    public void eliminado(Sobre sobre) {
        ViajeDeAlquiler viaje = viajeDeAlquiler(sobre);
        if (viaje == null || !inbox.primeraVez(sobre.eventId())) {
            return;
        }
        AlquilerEntity alquiler = buscar(viaje.alquilerId(), sobre);
        if (!alquiler.cancelarPorViajeEliminado("Se eliminó el viaje del alquiler")) {
            log.info("ViajeEliminado del alquiler {} en estado {}; se descarta", alquiler.getId(), alquiler.getEstado());
            return;
        }
        LocalDate fecha = sobre.occurredAt() == null ? Calendario.hoy()
                : LocalDate.ofInstant(sobre.occurredAt(), Calendario.ZONA);
        Optional<MovimientoCarteraEntity> reembolso = libro.reembolsar(
                LibroCartera.clavePagoAlquiler(alquiler.getId()),
                LibroCartera.claveReembolsoAlquiler(alquiler.getId()), fecha);
        ClienteEntity cliente = clientes.findById(alquiler.getClienteId()).orElseThrow();
        eventos.cancelado(alquiler, cliente, true, fecha, reembolso.isPresent() ? Reembolso.CARTERA : Reembolso.CAJA);
    }

    /**
     * E8 y E9 dejan el viajeId en el alquiler si E15 no lo dejó antes, así que se valida aunque
     * pueda faltar. E10 no lo guarda y no lo revisa: como los demás campos que customer no usa, un
     * viajeId malo ahí no justifica dejar el reembolso en la DLQ.
     */
    private void avanzar(Sobre sobre, Transicion transicion) {
        ViajeDeAlquiler viaje = viajeDeAlquiler(sobre);
        if (viaje == null) {
            return;
        }
        Payloads.idOpcional(viaje.viajeId(), sobre, "viajeId");
        if (!inbox.primeraVez(sobre.eventId())) {
            return;
        }
        AlquilerEntity alquiler = buscar(viaje.alquilerId(), sobre);
        if (!transicion.aplicar(alquiler, viaje)) {
            log.info("{} del alquiler {} en estado {}; se descarta", sobre.type(), alquiler.getId(),
                    alquiler.getEstado());
        }
    }

    /** @return null si es un viaje REGULAR: customer recibe los eventos de todos los viajes y solo le importan estos */
    private ViajeDeAlquiler viajeDeAlquiler(Sobre sobre) {
        ViajeDeAlquiler viaje = Payloads.leer(json, sobre, ViajeDeAlquiler.class);
        return viaje.alquilerId() == null ? null : viaje;
    }

    /** trip creó el viaje con el AlquilerPagado de este servicio: si el alquiler no existe, algo está mal. */
    private AlquilerEntity buscar(UUID alquilerId, Sobre sobre) {
        return alquileres.findById(alquilerId).orElseThrow(() -> new EventoNoProcesableException(
                sobre.type() + " de un alquiler que no existe en customer: " + alquilerId));
    }

    @FunctionalInterface
    private interface Transicion {
        boolean aplicar(AlquilerEntity alquiler, ViajeDeAlquiler viaje);
    }
}

