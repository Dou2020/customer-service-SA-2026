package gt.cunoc.sa.customer.api.interno.dto;

import java.util.UUID;

import gt.cunoc.sa.customer.domain.ClienteEntity;

/**
 * Lo que sales guarda en la venta y manda en BoletosVendidos (E11): el cliente y su correo, para
 * el correo de confirmación.
 *
 * @param nit       null en una cuenta con CF
 * @param correo    null en un cliente de mostrador sin cuenta
 * @param conCuenta si tiene cuenta (y por lo tanto cartera)
 */
public record ClienteInternoResponse(
        UUID clienteId,
        String nit,
        String nombre,
        String correo,
        boolean generico,
        boolean conCuenta) {

    public static ClienteInternoResponse de(ClienteEntity c) {
        return new ClienteInternoResponse(c.getId(), c.getNit(), c.getNombre(), c.getCorreo(), c.isGenerico(),
                c.tieneCuenta());
    }
}
