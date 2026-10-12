package gt.cunoc.sa.customer.api.interno;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import gt.cunoc.sa.customer.api.interno.dto.ClienteInternoResponse;
import gt.cunoc.sa.customer.application.ResolucionDeClientes;

/** S5: sales necesita el clienteId antes de vender (README, "Cliente, cuenta y NIT"). */
@Service
public class ClientesInternosService {

    private final ResolucionDeClientes resolucion;

    public ClientesInternosService(ResolucionDeClientes resolucion) {
        this.resolucion = resolucion;
    }

    /** Venta en mostrador: crea el cliente si el NIT no existe. */
    @Transactional
    public ClienteInternoResponse porNit(String nit, String nombre) {
        return ClienteInternoResponse.de(resolucion.porNit(nit, nombre));
    }

    /** Compra en línea: 409 mientras el PerfilGuardado de una cuenta recién confirmada no llegue. */
    @Transactional(readOnly = true)
    public ClienteInternoResponse porUsuario(UUID usuarioId) {
        return ClienteInternoResponse.de(resolucion.deLaCuenta(usuarioId));
    }
}
