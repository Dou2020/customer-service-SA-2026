package gt.cunoc.sa.customer.infrastructure.messaging.eventos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * E16: el alquiler se canceló. finance reversa el ingreso si estaba pagado y notification avisa
 * al cliente. aggregateId = alquilerId y aggregateVersion = versión del alquiler.
 *
 * @param estabaPagado si se había cobrado (solo entonces hay ingreso que reversar)
 * @param monto        el precio final (o el estimado si no llegó a confirmarse)
 * @param fechaPago    la del cobro original; null si no estaba pagado
 * @param fecha        la de la cancelación
 * @param reembolso    a dónde vuelve el dinero
 */
public record AlquilerCancelado(
        UUID alquilerId,
        UUID sucursalId,
        String correo,
        String nombre,
        boolean estabaPagado,
        BigDecimal monto,
        LocalDate fechaPago,
        LocalDate fecha,
        Reembolso reembolso,
        String motivo) {

    /** CARTERA: se devolvió a la cartera; CAJA: se pagó en efectivo y se devuelve en la sucursal; NINGUNO: no se había cobrado. */
    public enum Reembolso {
        CARTERA,
        CAJA,
        NINGUNO
    }
}
