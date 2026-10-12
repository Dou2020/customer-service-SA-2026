package gt.cunoc.sa.customer.infrastructure.messaging.eventos;

import java.util.UUID;

/**
 * E4, lo publica identity al confirmar la cuenta y en cada edición del perfil.
 * aggregateId = usuarioId y aggregateVersion = perfil.version.
 *
 * @param nit normalizado por identity; "CF" si la cuenta no tiene NIT
 */
public record PerfilGuardado(
        UUID usuarioId,
        String nit,
        String nombreCompleto,
        String correo,
        String telefono) {
}
