package gt.cunoc.sa.customer.infrastructure.security;

import java.util.Set;
import java.util.UUID;

/**
 * Principal del SecurityContext, armado con los X-User-* que escribe el gateway.
 * sucursalId solo viene para BRANCH_ADMIN y CASHIER; choferId solo para DRIVER.
 */
public record UsuarioActual(UUID usuarioId, UUID sucursalId, UUID choferId, Set<String> roles) {

    public boolean tieneRol(String rol) {
        return roles.contains(rol);
    }
}
