package gt.cunoc.sa.customer.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Réplica de una venta de sales para el historial: la llenan BoletosVendidos (E11) y
 * BoletosAnulados (E12). Vive en la misma base que el enlace de la cuenta, así el historial es
 * una consulta local y no depende de que sales responda.
 */
@Entity
@Table(name = "compra_boleto")
public class CompraBoletoEntity extends ConIdAsignado {

    @Id
    @Column(name = "venta_id")
    private UUID ventaId;

    @Column(name = "cliente_id", nullable = false)
    private UUID clienteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Canal canal;

    @Column(name = "viaje_id", nullable = false)
    private UUID viajeId;

    @Column(nullable = false, length = 100)
    private String origen;

    @Column(nullable = false, length = 100)
    private String destino;

    @Column(nullable = false)
    private Instant salida;

    @Column(nullable = false)
    private short[] asientos;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @Column(name = "fecha_pago", nullable = false)
    private LocalDate fechaPago;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private EstadoCompra estado;

    // aggregateVersion de la venta (E11, E12). No es @Version: la escribe sales, no este servicio.
    @Column(nullable = false)
    private long version;

    protected CompraBoletoEntity() {
    }

    public static CompraBoletoEntity nueva(UUID ventaId, long version, DatosCompra datos, EstadoCompra estado) {
        CompraBoletoEntity compra = new CompraBoletoEntity();
        compra.ventaId = ventaId;
        compra.version = -1;
        compra.aplicar(version, datos, estado);
        return compra;
    }

    /**
     * E11 y E12 traen la venta completa: el que tenga la versión mayor gana, en el orden que
     * lleguen. Así un BoletosAnulados que se adelanta deja la compra ANULADA y el BoletosVendidos
     * tardío no la revive.
     *
     * @return false si el evento es más viejo que lo ya aplicado
     */
    public boolean aplicar(long version, DatosCompra datos, EstadoCompra estado) {
        if (version <= this.version) {
            return false;
        }
        this.version = version;
        this.clienteId = datos.clienteId();
        this.canal = datos.canal();
        this.viajeId = datos.viajeId();
        this.origen = datos.origen();
        this.destino = datos.destino();
        this.salida = datos.salida();
        this.asientos = datos.asientos();
        this.total = datos.total();
        this.fechaPago = datos.fechaPago();
        this.estado = estado;
        return true;
    }

    /** Lo que E11 y E12 cuentan de la venta; clienteId ya resuelto (siguiendo absorbido_por). */
    public record DatosCompra(UUID clienteId, Canal canal, UUID viajeId, String origen, String destino,
            Instant salida, short[] asientos, BigDecimal total, LocalDate fechaPago) {
    }

    @Override
    public UUID getId() {
        return ventaId;
    }

    public UUID getVentaId() {
        return ventaId;
    }

    public UUID getClienteId() {
        return clienteId;
    }

    public Canal getCanal() {
        return canal;
    }

    public UUID getViajeId() {
        return viajeId;
    }

    public String getOrigen() {
        return origen;
    }

    public String getDestino() {
        return destino;
    }

    public Instant getSalida() {
        return salida;
    }

    public short[] getAsientos() {
        return asientos;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public LocalDate getFechaPago() {
        return fechaPago;
    }

    public EstadoCompra getEstado() {
        return estado;
    }

    public long getVersion() {
        return version;
    }
}
