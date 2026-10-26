package gt.cunoc.sa.customer.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Una fila del libro de la cartera: solo se insertan, nunca se corrigen. Una compensación es otra
 * fila (REEMBOLSO), así el saldo se puede reconstruir sumando el libro.
 */
@Entity
@Immutable
@Table(name = "movimiento_cartera")
public class MovimientoCarteraEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cliente_id", nullable = false)
    private UUID clienteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoMovimiento tipo;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    // La que eligió el usuario (README, "Fechas"); registradoEn es la del sistema.
    @Column(nullable = false)
    private LocalDate fecha;

    // La venta (texto de sales) o el alquiler que lo originó.
    @Column(name = "referencia_id", length = 64)
    private String referenciaId;

    @Column(name = "idempotency_key", nullable = false, length = 80, unique = true)
    private String idempotencyKey;

    @Column(name = "saldo_resultante", nullable = false, precision = 12, scale = 2)
    private BigDecimal saldoResultante;

    @Column(name = "registrado_en", nullable = false)
    private Instant registradoEn;

    protected MovimientoCarteraEntity() {
    }

    public MovimientoCarteraEntity(UUID clienteId, TipoMovimiento tipo, BigDecimal monto, LocalDate fecha,
            String referenciaId, String idempotencyKey, BigDecimal saldoResultante, Instant ahora) {
        this.clienteId = clienteId;
        this.tipo = tipo;
        this.monto = monto;
        this.fecha = fecha;
        this.referenciaId = referenciaId;
        this.idempotencyKey = idempotencyKey;
        this.saldoResultante = saldoResultante;
        this.registradoEn = ahora;
    }

    public Long getId() {
        return id;
    }

    public UUID getClienteId() {
        return clienteId;
    }

    public TipoMovimiento getTipo() {
        return tipo;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public String getReferenciaId() {
        return referenciaId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public BigDecimal getSaldoResultante() {
        return saldoResultante;
    }

    public Instant getRegistradoEn() {
        return registradoEn;
    }
}
