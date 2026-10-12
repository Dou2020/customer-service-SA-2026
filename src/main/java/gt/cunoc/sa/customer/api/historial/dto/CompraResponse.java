package gt.cunoc.sa.customer.api.historial.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import gt.cunoc.sa.customer.domain.Canal;
import gt.cunoc.sa.customer.domain.CompraBoletoEntity;
import gt.cunoc.sa.customer.domain.EstadoCompra;

/** Una compra de boletos del historial, en línea o en mostrador. */
public record CompraResponse(
        UUID ventaId,
        Canal canal,
        UUID viajeId,
        String origen,
        String destino,
        Instant salida,
        List<Integer> asientos,
        BigDecimal total,
        LocalDate fechaPago,
        EstadoCompra estado) {

    public static CompraResponse de(CompraBoletoEntity c) {
        List<Integer> asientos = new ArrayList<>();
        for (short numero : c.getAsientos()) {
            asientos.add((int) numero);
        }
        return new CompraResponse(c.getVentaId(), c.getCanal(), c.getViajeId(), c.getOrigen(), c.getDestino(),
                c.getSalida(), asientos, c.getTotal(), c.getFechaPago(), c.getEstado());
    }
}
