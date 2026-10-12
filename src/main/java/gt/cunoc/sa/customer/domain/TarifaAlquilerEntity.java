package gt.cunoc.sa.customer.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Réplica de una versión de tarifa de alquiler (E2). El id es el de identity.tarifa: lo inserta
 * ReplicaTarifas con ON CONFLICT DO NOTHING, así un E2 repetido no duplica. Nunca se actualiza.
 */
@Entity
@Immutable
@Table(name = "tarifa_alquiler")
public class TarifaAlquilerEntity {

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoTarifa tipo;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal valor;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDate vigenteDesde;

    protected TarifaAlquilerEntity() {
    }

    public Long getId() {
        return id;
    }

    public TipoTarifa getTipo() {
        return tipo;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public LocalDate getVigenteDesde() {
        return vigenteDesde;
    }
}
