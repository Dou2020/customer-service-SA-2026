package gt.cunoc.sa.customer.application;

import java.util.Objects;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;

import gt.cunoc.sa.customer.domain.Rol;
import gt.cunoc.sa.customer.infrastructure.security.UsuarioActual;

/**
 * Reglas de alcance (README, "Autenticación y autorización"): el rol se exige con
 * @PreAuthorize, y esto decide sobre el recurso ya cargado. Fuera de alcance → 403.
 */
public final class Alcance {

    private Alcance() {
    }

    public static boolean esSysAdmin(UsuarioActual actual) {
        return actual.tieneRol(Rol.SYS_ADMIN.name());
    }

    /** La sucursal de un BRANCH_ADMIN o CASHIER; sin ella no tiene alcance sobre nada. */
    public static UUID sucursalDe(UsuarioActual actual) {
        if (actual.sucursalId() == null) {
            throw new AccessDeniedException("Sin sucursal en el token");
        }
        return actual.sucursalId();
    }

    /** Un SYS_ADMIN ve todo; los demás, solo su sucursal. */
    public static void exigirSucursal(UsuarioActual actual, UUID sucursalDelRecurso) {
        if (!esSysAdmin(actual) && !Objects.equals(sucursalDe(actual), sucursalDelRecurso)) {
            throw new AccessDeniedException("Recurso de otra sucursal");
        }
    }
}
