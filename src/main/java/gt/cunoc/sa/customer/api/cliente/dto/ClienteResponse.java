package gt.cunoc.sa.customer.api.cliente.dto;

import java.util.UUID;

import gt.cunoc.sa.customer.domain.ClienteEntity;

/** @param nit null en una cuenta con CF */
public record ClienteResponse(
        UUID id,
        String nit,
        String nombre,
        String correo,
        String telefono,
        boolean generico,
        boolean conCuenta) {

    public static ClienteResponse de(ClienteEntity c) {
        return new ClienteResponse(c.getId(), c.getNit(), c.getNombre(), c.getCorreo(), c.getTelefono(),
                c.isGenerico(), c.tieneCuenta());
    }
}
