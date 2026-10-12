package gt.cunoc.sa.customer.api.alquiler.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import gt.cunoc.sa.customer.domain.AlquilerEntity;
import gt.cunoc.sa.customer.domain.Canal;
import gt.cunoc.sa.customer.domain.ClienteEntity;
import gt.cunoc.sa.customer.domain.EstadoAlquiler;
import gt.cunoc.sa.customer.domain.Trayecto;

/**
 * @param clienteNit null si el cliente es una cuenta sin NIT
 * @param precioFinal null hasta que la sucursal lo confirma
 * @param busPlaca    y el resto de la asignación, null hasta que trip asigna bus y chofer
 */
public record AlquilerResponse(
        UUID id,
        UUID clienteId,
        String clienteNombre,
        String clienteNit,
        UUID sucursalId,
        Canal canal,
        String origen,
        BigDecimal origenLat,
        BigDecimal origenLng,
        String destino,
        BigDecimal destinoLat,
        BigDecimal destinoLng,
        BigDecimal distanciaKm,
        LocalDate fechaSolicitud,
        LocalDate fechaSalida,
        LocalDate fechaRetorno,
        boolean idaYVuelta,
        int pasajeros,
        BigDecimal precioEstimado,
        BigDecimal precioFinal,
        EstadoAlquiler estado,
        LocalDate fechaPago,
        UUID viajeId,
        UUID busId,
        String busPlaca,
        UUID choferId,
        String choferNombre,
        String motivo,
        Instant creadoEn) {

    public static AlquilerResponse de(AlquilerEntity a, ClienteEntity c) {
        Trayecto t = a.getTrayecto();
        return new AlquilerResponse(a.getId(), a.getClienteId(), c.getNombre(), c.getNit(), a.getSucursalId(),
                a.getCanal(), t.origen(), t.origenLat(), t.origenLng(), t.destino(), t.destinoLat(), t.destinoLng(),
                a.getDistanciaKm(), a.getFechaSolicitud(), a.getFechaSalida(), a.getFechaRetorno(), a.isIdaYVuelta(),
                a.getPasajeros(), a.getPrecioEstimado(), a.getPrecioFinal(), a.getEstado(), a.getFechaPago(),
                a.getViajeId(), a.getBusId(), a.getBusPlaca(), a.getChoferId(), a.getChoferNombre(), a.getMotivo(),
                a.getCreadoEn());
    }
}
