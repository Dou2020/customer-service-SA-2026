package gt.cunoc.sa.customer.infrastructure.messaging.eventos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * E14: el alquiler quedó pagado. trip crea el viaje ALQUILER pendiente de asignación, finance
 * registra el ingreso (sucursal que lo atiende, fecha de pago) y notification manda el
 * comprobante. aggregateId = alquilerId y aggregateVersion = versión del alquiler.
 *
 * @param nit   "CF" si el cliente no tiene NIT
 * @param monto el precio final confirmado
 */
public record AlquilerPagado(
        UUID alquilerId,
        UUID clienteId,
        UUID sucursalId,
        String nombre,
        String nit,
        String correo,
        String origen,
        BigDecimal origenLat,
        BigDecimal origenLng,
        String destino,
        BigDecimal destinoLat,
        BigDecimal destinoLng,
        BigDecimal distanciaKm,
        LocalDate fechaSalida,
        LocalDate fechaRetorno,
        boolean idaYVuelta,
        int pasajeros,
        BigDecimal monto,
        LocalDate fechaPago,
        MetodoPago metodoPago) {

    /** CARTERA: débito de la cartera digital; EFECTIVO: cobrado en el mostrador de la sucursal. */
    public enum MetodoPago {
        CARTERA,
        EFECTIVO
    }
}
