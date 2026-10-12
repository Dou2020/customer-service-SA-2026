package gt.cunoc.sa.customer.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Solo lectura desde JPA: el saldo cambia únicamente con el UPDATE condicional de LibroCartera
 * (saldo >= monto en el WHERE), nunca leyendo, restando en Java y guardando, que con dos débitos
 * simultáneos dejaría pasar los dos. La cartera nace con el enlace de la cuenta (E4).
 */
@Entity
@Immutable
@Table(name = "cartera")
public class CarteraEntity {

    @Id
    @Column(name = "cliente_id")
    private UUID clienteId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal saldo;

    @Column(name = "actualizado_en", nullable = false)
    private Instant actualizadoEn;

    protected CarteraEntity() {
    }

    public UUID getClienteId() {
        return clienteId;
    }

    public BigDecimal getSaldo() {
        return saldo;
    }

    public Instant getActualizadoEn() {
        return actualizadoEn;
    }
}
