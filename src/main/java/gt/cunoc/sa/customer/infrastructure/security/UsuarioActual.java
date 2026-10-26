package gt.cunoc.sa.customer.infrastructure.security;

import java.util.Set;
import java.util.UUID;

/**
 * Principal del SecurityContext, armado con los X-User-* que escribe el gateway.
 * sucursalId solo viene para BRANCH_ADMIN y CASHIER; choferId solo para DRIVER.
 *
 * @param choferId el id del chofer en fleet, como texto ("3"): fleet usa ids numéricos y identity
 *                 lo guarda y lo pone en el JWT tal cual (V3 de identity)
 */
public record UsuarioActual(UUID usuarioId, UUID sucursalId, String choferId, Set<String> roles) {

    public boolean tieneRol(String rol) {
        return roles.contains(rol);
    }
}
