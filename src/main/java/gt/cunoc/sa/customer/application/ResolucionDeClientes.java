package gt.cunoc.sa.customer.application;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import gt.cunoc.sa.customer.api.cliente.ClienteRepository;
import gt.cunoc.sa.customer.api.error.ErrorDeNegocio;
import gt.cunoc.sa.customer.domain.ClienteEntity;
import gt.cunoc.sa.customer.domain.Nit;

/**
 * El cliente de cada canal (README, "Cómo se resuelve el cliente en cada canal"): por NIT en
 * mostrador (S5 y alquiler presencial) y por la cuenta en línea (S5 y lo propio de cada usuario).
 */
@Service
public class ResolucionDeClientes {

    public static final String CUENTA_SIN_CLIENTE =
            "Tu cuenta se está terminando de configurar. Intenta de nuevo en unos segundos";

    private final ClienteRepository clientes;

    public ResolucionDeClientes(ClienteRepository clientes) {
        this.clientes = clientes;
    }

    /**
     * Sin NIT o con CF, el genérico. Si el NIT no existe se crea; si es de una cuenta, la venta cae
     * en su cliente y aparece en su historial de inmediato.
     *
     * @param nombre el que escribe el cajero para un NIT nuevo; no reemplaza el de un cliente existente
     * @throws gt.cunoc.sa.customer.domain.NitInvalidoException si el NIT no tiene formato válido (422)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ClienteEntity porNit(String nitEscrito, String nombre) {
        String nit = Nit.normalizar(nitEscrito);
        if (Nit.esConsumidorFinal(nit)) {
            return clientes.findByGenericoTrue()
                    .orElseThrow(() -> new IllegalStateException("Falta el cliente genérico CF de la migración"));
        }
        String nombreNuevo = nombre == null || nombre.isBlank() ? "Cliente NIT " + nit : nombre.strip();
        clientes.insertarPorNit(UUID.randomUUID(), nit, nombreNuevo, Instant.now());
        // Relee aunque el INSERT no haya entrado: lo insertó otro cajero, o es de una cuenta.
        return clientes.findByNit(nit)
                .orElseThrow(() -> new IllegalStateException("El cliente con NIT " + nit + " no quedó registrado"));
    }

    /**
     * El cliente enlazado a la cuenta. No existe mientras customer no aplique el PerfilGuardado
     * (E4) de una cuenta recién confirmada: milisegundos en la práctica, y la cartera nace con
     * ese mismo evento, así que antes tampoco hay saldo.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ClienteEntity deLaCuenta(UUID usuarioId) {
        return clientes.findByUsuarioId(usuarioId)
                .orElseThrow(() -> ErrorDeNegocio.conflicto(CUENTA_SIN_CLIENTE));
    }

    /**
     * Sigue la lápida de un cliente absorbido hasta la cuenta que tomó su NIT: un evento que sales
     * emitió con el id viejo (S5 justo antes de la absorción) llega igual al historial de la cuenta.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ClienteEntity vigente(UUID clienteId) {
        ClienteEntity cliente = clientes.findById(clienteId).orElse(null);
        // Absorber reapunta las lápidas a la cuenta nueva, así que la cadena tiene un salto; el
        // límite solo protege de un ciclo por datos corruptos.
        for (int saltos = 0; cliente != null && cliente.getAbsorbidoPor() != null && saltos < 5; saltos++) {
            cliente = clientes.findById(cliente.getAbsorbidoPor()).orElse(null);
        }
        return cliente;
    }
}
