package gt.cunoc.sa.customer.application;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import gt.cunoc.sa.customer.api.alquiler.AlquilerRepository;
import gt.cunoc.sa.customer.api.cartera.CarteraRepository;
import gt.cunoc.sa.customer.api.cliente.ClienteRepository;
import gt.cunoc.sa.customer.api.historial.CompraBoletoRepository;
import gt.cunoc.sa.customer.domain.ClienteEntity;
import gt.cunoc.sa.customer.domain.Nit;
import gt.cunoc.sa.customer.domain.NitInvalidoException;
import gt.cunoc.sa.customer.infrastructure.messaging.EventoNoProcesableException;
import gt.cunoc.sa.customer.infrastructure.messaging.Inbox;
import gt.cunoc.sa.customer.infrastructure.messaging.Payloads;
import gt.cunoc.sa.customer.infrastructure.messaging.Sobre;
import gt.cunoc.sa.customer.infrastructure.messaging.eventos.PerfilGuardado;

/**
 * Aplica PerfilGuardado (E4): crea el cliente de la cuenta o la enlaza por NIT, y al cambiar el
 * NIT hace la absorción (README, "PerfilGuardado en customer"). identity y customer nunca se
 * llaman por REST: este evento es lo único que los une.
 *
 * Todo en una transacción con el inbox: un duplicado no tiene efecto, y si algo falla el inbox se
 * revierte con el resto y el reintento empieza de cero.
 */
@Service
public class EnlaceDeCuentas {

    private static final Logger log = LoggerFactory.getLogger(EnlaceDeCuentas.class);

    private final Inbox inbox;
    private final ClienteRepository clientes;
    private final CarteraRepository carteras;
    private final CompraBoletoRepository compras;
    private final AlquilerRepository alquileres;
    private final ObjectMapper json;

    public EnlaceDeCuentas(Inbox inbox, ClienteRepository clientes, CarteraRepository carteras,
            CompraBoletoRepository compras, AlquilerRepository alquileres, ObjectMapper json) {
        this.inbox = inbox;
        this.clientes = clientes;
        this.carteras = carteras;
        this.compras = compras;
        this.alquileres = alquileres;
        this.json = json;
    }

    @Transactional
    public void aplicar(Sobre sobre) {
        if (!inbox.primeraVez(sobre.eventId())) {
            log.debug("PerfilGuardado {} duplicado; se descarta", sobre.eventId());
            return;
        }
        PerfilGuardado perfil = Payloads.leer(json, sobre, PerfilGuardado.class);
        Payloads.exigir(perfil.usuarioId() != null && perfil.nombreCompleto() != null, sobre,
                "usuarioId o nombreCompleto");
        String nit = nitDeCuenta(perfil.nit(), sobre);
        long version = sobre.aggregateVersion();

        Optional<ClienteEntity> existente = clientes.bloquearPorUsuario(perfil.usuarioId());
        if (existente.isEmpty()) {
            alta(perfil, nit, version);
            return;
        }
        ClienteEntity cuenta = existente.get();
        // Dos ediciones que llegan al revés: la vieja no pisa a la nueva.
        if (!cuenta.aceptaVersionDePerfil(version)) {
            log.info("PerfilGuardado v{} de la cuenta {} ya superado (v{}); se descarta", version,
                    perfil.usuarioId(), cuenta.getPerfilVersion());
            return;
        }
        if (!Objects.equals(nit, cuenta.getNit())) {
            cambiarNit(cuenta, nit);
        }
        cuenta.aplicarPerfil(perfil.nombreCompleto(), perfil.correo(), perfil.telefono(), version);
    }

    /**
     * Con CF, un cliente propio sin NIT: las compras de CF en mostrador son anónimas y nunca se
     * heredan. Con NIT, el upsert enlaza el cliente que ya compró en mostrador, y lo comprado
     * aparece en el historial sin mover nada. La cartera nace aquí, en cero.
     */
    private void alta(PerfilGuardado perfil, String nit, long version) {
        UUID id = UUID.randomUUID();
        Instant ahora = Instant.now();
        if (nit == null) {
            clientes.insertarCuentaSinNit(id, perfil.nombreCompleto(), perfil.correo(), perfil.telefono(),
                    perfil.usuarioId(), version, ahora);
        } else {
            id = clientes.enlazarPorNit(id, nit, perfil.nombreCompleto(), perfil.correo(), perfil.telefono(),
                    perfil.usuarioId(), version, ahora).orElseThrow(() -> new NitDeOtraCuentaException(nit));
        }
        carteras.crear(id);
    }

    /**
     * La cuenta conserva su cliente (y con él la cartera y lo comprado en línea) y toma el NIT
     * nuevo. Si ese NIT ya compró en mostrador, la cuenta absorbe sus compras y alquileres, y el
     * otro cliente queda como lápida. Lo comprado con el NIT anterior se queda con la cuenta.
     */
    private void cambiarNit(ClienteEntity cuenta, String nit) {
        if (nit != null) {
            // Bloqueado: una venta en mostrador no le puede agregar compras a la mitad del traspaso.
            Optional<ClienteEntity> delNit = clientes.bloquearPorNit(nit);
            if (delNit.isPresent()) {
                absorber(cuenta, delNit.get());
            }
        }
        cuenta.cambiarNit(nit);
    }

    private void absorber(ClienteEntity cuenta, ClienteEntity otro) {
        if (otro.tieneCuenta() || otro.isGenerico()) {
            throw new NitDeOtraCuentaException(otro.getNit());
        }
        String nit = otro.getNit();
        int compras = this.compras.reasignarCliente(otro.getId(), cuenta.getId());
        int alquileres = this.alquileres.reasignarCliente(otro.getId(), cuenta.getId());
        clientes.reapuntarAbsorbidos(otro.getId(), cuenta.getId());
        otro.absorberEn(cuenta.getId());
        // El NIT se libera en la base antes de que la cuenta lo tome: si no, la UQ lo rechaza.
        clientes.flush();
        log.info("La cuenta {} absorbió al cliente {} con NIT {}: {} compras y {} alquileres", cuenta.getUsuarioId(),
                otro.getId(), nit, compras, alquileres);
    }

    /** @return el NIT normalizado, o null si la cuenta es CF */
    private static String nitDeCuenta(String nit, Sobre sobre) {
        try {
            String normalizado = Nit.normalizar(nit);
            return Nit.esConsumidorFinal(normalizado) ? null : normalizado;
        } catch (NitInvalidoException e) {
            // identity lo valida con la misma regla: si llega mal, ningún reintento lo arregla.
            throw new EventoNoProcesableException("PerfilGuardado " + sobre.eventId() + " con NIT inválido", e);
        }
    }
}
