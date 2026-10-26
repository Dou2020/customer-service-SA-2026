package gt.cunoc.sa.customer.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Un alquiler de bus y su ciclo de vida (README, "Ciclo de vida de un alquiler").
 *
 * Las transiciones que pide un usuario (confirmar, rechazar, cancelar, pagar) lanzan
 * TransicionInvalidaException si el estado no las permite: el usuario recibe un 409 que le dice
 * por qué. Las que traen los eventos de trip (asignar, iniciar, finalizar, cancelar por viaje
 * eliminado) devuelven false en vez de fallar: un evento repetido o atrasado no es un error, y los
 * estados solo avanzan.
 */
@Entity
@Table(name = "alquiler")
public class AlquilerEntity extends ConIdAsignado<UUID> {

    /** Pagado y todavía sin terminar: el viaje existe en trip y solo se cancela eliminándolo. */
    private static final Set<EstadoAlquiler> PAGADO_SIN_INICIAR = EnumSet.of(EstadoAlquiler.PAGADO,
            EstadoAlquiler.ASIGNADO);

    @Id
    private UUID id;

    @Column(name = "cliente_id", nullable = false)
    private UUID clienteId;

    // La que atiende el alquiler (la elige el cliente). Sin validar: customer no replica
    // sucursales, y una sucursal que no existe deja un alquiler que ningún admin confirma.
    @Column(name = "sucursal_id", nullable = false, updatable = false)
    private UUID sucursalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private Canal canal;

    @Column(nullable = false, length = 200)
    private String origen;

    @Column(name = "origen_lat", nullable = false, precision = 9, scale = 6)
    private BigDecimal origenLat;

    @Column(name = "origen_lng", nullable = false, precision = 9, scale = 6)
    private BigDecimal origenLng;

    @Column(nullable = false, length = 200)
    private String destino;

    @Column(name = "destino_lat", nullable = false, precision = 9, scale = 6)
    private BigDecimal destinoLat;

    @Column(name = "destino_lng", nullable = false, precision = 9, scale = 6)
    private BigDecimal destinoLng;

    @Column(name = "distancia_km", nullable = false, precision = 8, scale = 2)
    private BigDecimal distanciaKm;

    // La que eligió el usuario (README, "Fechas"); creadoEn es la del sistema.
    @Column(name = "fecha_solicitud", nullable = false)
    private LocalDate fechaSolicitud;

    @Column(name = "fecha_salida", nullable = false)
    private LocalDate fechaSalida;

    @Column(name = "fecha_retorno")
    private LocalDate fechaRetorno;

    @Column(name = "ida_y_vuelta", nullable = false)
    private boolean idaYVuelta;

    @Column(nullable = false)
    private short pasajeros;

    @Column(name = "precio_estimado", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioEstimado;

    @Column(name = "precio_final", precision = 10, scale = 2)
    private BigDecimal precioFinal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private EstadoAlquiler estado;

    @Column(name = "fecha_pago")
    private LocalDate fechaPago;

    // Ids de trip (viaje) y de fleet (bus, chofer) como texto (V3): se guardan tal cual llegan.
    @Column(name = "viaje_id", length = 64)
    private String viajeId;

    @Column(name = "bus_id", length = 64)
    private String busId;

    @Column(name = "chofer_id", length = 64)
    private String choferId;

    // 20, el largo de bus.placa en fleet (V3).
    @Column(name = "bus_placa", length = 20)
    private String busPlaca;

    @Column(name = "chofer_nombre", length = 150)
    private String choferNombre;

    // Versión del viaje del último AlquilerAsignado (E15) aplicado.
    @Column(name = "asignacion_version")
    private Long asignacionVersion;

    @Column(length = 200)
    private String motivo;

    // Bloqueo optimista (dos admins confirmando a la vez) y aggregateVersion de E13, E14 y E16.
    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    protected AlquilerEntity() {
    }

    public static AlquilerEntity solicitado(UUID clienteId, UUID sucursalId, Canal canal, Trayecto trayecto,
            BigDecimal distanciaKm, LocalDate fechaSolicitud, LocalDate fechaSalida, LocalDate fechaRetorno,
            int pasajeros, BigDecimal precioEstimado, Instant ahora) {
        AlquilerEntity alquiler = new AlquilerEntity();
        alquiler.id = UUID.randomUUID();
        alquiler.clienteId = clienteId;
        alquiler.sucursalId = sucursalId;
        alquiler.canal = canal;
        alquiler.origen = trayecto.origen();
        alquiler.origenLat = trayecto.origenLat();
        alquiler.origenLng = trayecto.origenLng();
        alquiler.destino = trayecto.destino();
        alquiler.destinoLat = trayecto.destinoLat();
        alquiler.destinoLng = trayecto.destinoLng();
        alquiler.distanciaKm = distanciaKm;
        alquiler.fechaSolicitud = fechaSolicitud;
        alquiler.fechaSalida = fechaSalida;
        alquiler.fechaRetorno = fechaRetorno;
        alquiler.idaYVuelta = fechaRetorno != null;
        alquiler.pasajeros = (short) pasajeros;
        alquiler.precioEstimado = precioEstimado;
        alquiler.estado = EstadoAlquiler.SOLICITADO;
        alquiler.creadoEn = ahora;
        return alquiler;
    }

    /** @param precio el que fija el admin, o null para aceptar el estimado */
    public void confirmar(BigDecimal precio) {
        if (estado != EstadoAlquiler.SOLICITADO) {
            throw new TransicionInvalidaException("Solo se confirma un alquiler solicitado, y este está "
                    + describir(estado));
        }
        this.precioFinal = precio == null ? precioEstimado : precio;
        this.estado = EstadoAlquiler.CONFIRMADO;
    }

    public void rechazar(String motivo) {
        if (estado != EstadoAlquiler.SOLICITADO) {
            throw new TransicionInvalidaException("Solo se rechaza un alquiler solicitado, y este está "
                    + describir(estado));
        }
        this.motivo = motivo;
        this.estado = EstadoAlquiler.RECHAZADO;
    }

    /**
     * Antes de pagar (supuesto 12). Después del pago el viaje ya existe en trip: se cancela
     * eliminándolo, y el reembolso llega con ViajeEliminado (E10).
     */
    public void cancelar(String motivo) {
        if (PAGADO_SIN_INICIAR.contains(estado)) {
            throw new TransicionInvalidaException(
                    "Un alquiler pagado ya no se cancela aquí: la sucursal lo cancela eliminando su viaje");
        }
        if (estado != EstadoAlquiler.SOLICITADO && estado != EstadoAlquiler.CONFIRMADO) {
            throw new TransicionInvalidaException("El alquiler ya está " + describir(estado));
        }
        this.motivo = motivo;
        this.estado = EstadoAlquiler.CANCELADO;
    }

    public void pagar(LocalDate fecha) {
        if (estado == EstadoAlquiler.SOLICITADO) {
            throw new TransicionInvalidaException("La sucursal todavía no confirma el precio de este alquiler");
        }
        if (estado != EstadoAlquiler.CONFIRMADO) {
            throw new TransicionInvalidaException(PAGADO_SIN_INICIAR.contains(estado)
                    || estado == EstadoAlquiler.EN_CURSO || estado == EstadoAlquiler.FINALIZADO
                    ? "Este alquiler ya está pagado" : "El alquiler está " + describir(estado));
        }
        this.fechaPago = fecha;
        this.estado = EstadoAlquiler.PAGADO;
    }

    /**
     * AlquilerAsignado (E15), también en cada reasignación. Bus y chofer se escriben aunque el
     * viaje ya haya salido (un E15 atrasado); el estado solo pasa de PAGADO a ASIGNADO.
     *
     * @return false si el alquiler no está pagado o el evento es más viejo que el último aplicado
     */
    public boolean asignar(String viajeId, String busId, String busPlaca, String choferId, String choferNombre,
            long version) {
        if (!estaPagado() || (asignacionVersion != null && version <= asignacionVersion)) {
            return false;
        }
        this.viajeId = viajeId;
        this.busId = busId;
        this.busPlaca = busPlaca;
        this.choferId = choferId;
        this.choferNombre = choferNombre;
        this.asignacionVersion = version;
        if (estado == EstadoAlquiler.PAGADO) {
            estado = EstadoAlquiler.ASIGNADO;
        }
        return true;
    }

    /** ViajeIniciado (E8). @return false si ya había salido, terminado o se canceló */
    public boolean iniciar(String viajeId) {
        if (!PAGADO_SIN_INICIAR.contains(estado)) {
            return false;
        }
        anotarViaje(viajeId);
        estado = EstadoAlquiler.EN_CURSO;
        return true;
    }

    /** ViajeFinalizado (E9). Si el E8 no llegó todavía, el alquiler pasa directo a FINALIZADO. */
    public boolean finalizar(String viajeId) {
        if (!PAGADO_SIN_INICIAR.contains(estado) && estado != EstadoAlquiler.EN_CURSO) {
            return false;
        }
        anotarViaje(viajeId);
        estado = EstadoAlquiler.FINALIZADO;
        return true;
    }

    /** ViajeEliminado (E10): trip solo elimina un viaje que no ha salido. */
    public boolean cancelarPorViajeEliminado(String motivo) {
        if (!PAGADO_SIN_INICIAR.contains(estado)) {
            return false;
        }
        this.motivo = motivo;
        this.estado = EstadoAlquiler.CANCELADO;
        return true;
    }

    /** Ya se cobró y no se ha cancelado: cuenta como ingreso. */
    public boolean estaPagado() {
        return PAGADO_SIN_INICIAR.contains(estado) || estado == EstadoAlquiler.EN_CURSO
                || estado == EstadoAlquiler.FINALIZADO;
    }

    private void anotarViaje(String viajeId) {
        if (this.viajeId == null) {
            this.viajeId = viajeId;
        }
    }

    private static String describir(EstadoAlquiler estado) {
        return estado.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getClienteId() {
        return clienteId;
    }

    public UUID getSucursalId() {
        return sucursalId;
    }

    public Canal getCanal() {
        return canal;
    }

    public Trayecto getTrayecto() {
        return new Trayecto(origen, origenLat, origenLng, destino, destinoLat, destinoLng);
    }

    public String getOrigen() {
        return origen;
    }

    public String getDestino() {
        return destino;
    }

    public BigDecimal getDistanciaKm() {
        return distanciaKm;
    }

    public LocalDate getFechaSolicitud() {
        return fechaSolicitud;
    }

    public LocalDate getFechaSalida() {
        return fechaSalida;
    }

    public LocalDate getFechaRetorno() {
        return fechaRetorno;
    }

    public boolean isIdaYVuelta() {
        return idaYVuelta;
    }

    public int getPasajeros() {
        return pasajeros;
    }

    public BigDecimal getPrecioEstimado() {
        return precioEstimado;
    }

    public BigDecimal getPrecioFinal() {
        return precioFinal;
    }

    public EstadoAlquiler getEstado() {
        return estado;
    }

    public LocalDate getFechaPago() {
        return fechaPago;
    }

    public String getViajeId() {
        return viajeId;
    }

    public String getBusId() {
        return busId;
    }

    public String getChoferId() {
        return choferId;
    }

    public String getBusPlaca() {
        return busPlaca;
    }

    public String getChoferNombre() {
        return choferNombre;
    }

    public String getMotivo() {
        return motivo;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
