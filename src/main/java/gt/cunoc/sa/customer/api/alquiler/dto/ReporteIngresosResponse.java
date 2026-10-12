package gt.cunoc.sa.customer.api.alquiler.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import gt.cunoc.sa.customer.domain.AlquilerEntity;
import gt.cunoc.sa.customer.domain.Canal;
import gt.cunoc.sa.customer.domain.ClienteEntity;

/**
 * Reporte de ingresos por alquiler de buses (README, "Reportes"): una fila por alquiler pagado y
 * no cancelado, por fecha de pago, con el total. El frontend lo muestra en ReportTable y lo exporta
 * a PDF.
 *
 * @param desde      null si no se filtró: entonces cuenta desde el primer registro
 * @param sucursalId null si un SYS_ADMIN pidió todas
 */
public record ReporteIngresosResponse(
        LocalDate desde,
        LocalDate hasta,
        UUID sucursalId,
        List<Fila> filas,
        int cantidad,
        BigDecimal total) {

    public record Fila(
            UUID alquilerId,
            UUID sucursalId,
            String cliente,
            String nit,
            Canal canal,
            String origen,
            String destino,
            LocalDate fechaSalida,
            LocalDate fechaRetorno,
            String busPlaca,
            String choferNombre,
            LocalDate fechaPago,
            BigDecimal precio) {

        public static Fila de(AlquilerEntity a, ClienteEntity c) {
            return new Fila(a.getId(), a.getSucursalId(), c.getNombre(), c.getNit(), a.getCanal(), a.getOrigen(),
                    a.getDestino(), a.getFechaSalida(), a.getFechaRetorno(), a.getBusPlaca(), a.getChoferNombre(),
                    a.getFechaPago(), a.getPrecioFinal());
        }
    }
}
