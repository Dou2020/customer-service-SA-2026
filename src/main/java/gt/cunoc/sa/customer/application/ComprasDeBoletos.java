package gt.cunoc.sa.customer.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import gt.cunoc.sa.customer.api.historial.CompraBoletoRepository;
import gt.cunoc.sa.customer.domain.Calendario;
import gt.cunoc.sa.customer.domain.Canal;
import gt.cunoc.sa.customer.domain.ClienteEntity;
import gt.cunoc.sa.customer.domain.CompraBoletoEntity;
import gt.cunoc.sa.customer.domain.CompraBoletoEntity.DatosCompra;
import gt.cunoc.sa.customer.domain.EstadoCompra;
import gt.cunoc.sa.customer.infrastructure.messaging.EventoNoProcesableException;
import gt.cunoc.sa.customer.infrastructure.messaging.Inbox;
import gt.cunoc.sa.customer.infrastructure.messaging.Payloads;
import gt.cunoc.sa.customer.infrastructure.messaging.Sobre;
import gt.cunoc.sa.customer.infrastructure.messaging.eventos.BoletosAnulados;
import gt.cunoc.sa.customer.infrastructure.messaging.eventos.BoletosVendidos;

/**
 * Aplica BoletosVendidos (E11) y BoletosAnulados (E12): la réplica de compras que arma el
 * historial, en línea y en mostrador (README, "Historial"). Así el enlace por NIT y el historial
 * viven en la misma base, y ver las compras previas no depende de que sales responda.
 */
@Service
public class ComprasDeBoletos {

    private static final Logger log = LoggerFactory.getLogger(ComprasDeBoletos.class);

    private final Inbox inbox;
    private final CompraBoletoRepository compras;
    private final ResolucionDeClientes resolucion;
    private final LibroCartera libro;
    private final ObjectMapper json;

    public ComprasDeBoletos(Inbox inbox, CompraBoletoRepository compras, ResolucionDeClientes resolucion,
            LibroCartera libro, ObjectMapper json) {
        this.inbox = inbox;
        this.compras = compras;
        this.resolucion = resolucion;
        this.libro = libro;
        this.json = json;
    }

    @Transactional
    public void vendidos(Sobre sobre) {
        if (!inbox.primeraVez(sobre.eventId())) {
            return;
        }
        BoletosVendidos v = Payloads.leer(json, sobre, BoletosVendidos.class);
        replicar(sobre, v.ventaId(), v.clienteId(), EstadoCompra.CONFIRMADA, new Venta(v.canal(), v.viajeId(),
                v.origen(), v.destino(), v.salida(), v.asientos(), v.total(), v.fechaPago()));
    }

    /**
     * Además de anular la compra, devuelve a la cartera exactamente lo que se debitó por esa venta
     * (su clave es el ventaId). Una venta en mostrador no tiene débito, y una cuyo pago anuló el
     * reconciliador tiene la lápida: en los dos casos no hay nada que devolver.
     */
    @Transactional
    public void anulados(Sobre sobre) {
        if (!inbox.primeraVez(sobre.eventId())) {
            return;
        }
        BoletosAnulados v = Payloads.leer(json, sobre, BoletosAnulados.class);
        replicar(sobre, v.ventaId(), v.clienteId(), EstadoCompra.ANULADA, new Venta(v.canal(), v.viajeId(),
                v.origen(), v.destino(), v.salida(), v.asientos(), v.total(), v.fechaPago()));
        LocalDate fecha = sobre.occurredAt() == null ? Calendario.hoy()
                : LocalDate.ofInstant(sobre.occurredAt(), Calendario.ZONA);
        libro.reembolsar(v.ventaId(), LibroCartera.claveReembolsoVenta(v.ventaId()), fecha)
                .ifPresent(r -> log.info("Venta {} anulada: se devolvieron {} a la cartera", v.ventaId(), r.getMonto()));
    }

    private void replicar(Sobre sobre, String ventaId, UUID clienteId, EstadoCompra estado, Venta venta) {
        Payloads.exigirId(ventaId, sobre, "ventaId");
        Payloads.exigirId(venta.viajeId(), sobre, "viajeId");
        Payloads.exigir(clienteId != null && venta.completa(), sobre, "datos de la venta");
        ClienteEntity cliente = resolucion.vigente(clienteId);
        if (cliente == null) {
            throw new EventoNoProcesableException("La venta " + ventaId + " es de un cliente que customer no conoce");
        }
        // Las compras de CF en mostrador son anónimas: el genérico nunca se enlaza a una cuenta,
        // así que su historial nadie lo vería.
        if (cliente.isGenerico()) {
            return;
        }
        DatosCompra datos = new DatosCompra(cliente.getId(), venta.canal(), venta.viajeId(), venta.origen(),
                venta.destino(), venta.salida(), asientos(venta.asientos()), venta.total(), venta.fechaPago());
        long version = sobre.aggregateVersion();
        compras.findById(ventaId).ifPresentOrElse(compra -> {
            if (!compra.aplicar(version, datos, estado)) {
                log.info("{} v{} de la venta {} ya superado (v{}); se descarta", sobre.type(), version, ventaId,
                        compra.getVersion());
            }
        }, () -> compras.save(CompraBoletoEntity.nueva(ventaId, version, datos, estado)));
    }

    private static short[] asientos(List<Integer> asientos) {
        short[] numeros = new short[asientos.size()];
        for (int i = 0; i < numeros.length; i++) {
            numeros[i] = asientos.get(i).shortValue();
        }
        return numeros;
    }

    /** Lo que E11 y E12 traen de la venta; el viajeId (texto de trip) se valida aparte. */
    private record Venta(Canal canal, String viajeId, String origen, String destino, Instant salida,
            List<Integer> asientos, BigDecimal total, LocalDate fechaPago) {

        boolean completa() {
            return canal != null && origen != null && destino != null && salida != null
                    && asientos != null && !asientos.isEmpty() && total != null && fechaPago != null;
        }
    }
}
