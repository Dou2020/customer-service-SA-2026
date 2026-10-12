package gt.cunoc.sa.customer.api.cartera;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import gt.cunoc.sa.customer.domain.CarteraEntity;

/**
 * El saldo solo cambia con estos UPDATE: la condición del débito (saldo >= monto) la evalúa
 * PostgreSQL con la fila bloqueada, así dos débitos simultáneos no pueden dejarla en negativo.
 * Devuelven el saldo resultante (RETURNING) para anotarlo en el libro.
 */
public interface CarteraRepository extends JpaRepository<CarteraEntity, UUID> {

    @Modifying
    @Query(value = """
            insert into cartera (cliente_id, saldo, actualizado_en) values (:clienteId, 0, now())
            on conflict do nothing""", nativeQuery = true)
    int crear(UUID clienteId);

    /** @return el saldo después del débito, o vacío si no alcanza (o no hay cartera) */
    @Query(value = """
            update cartera set saldo = saldo - :monto, actualizado_en = now()
            where cliente_id = :clienteId and saldo >= :monto
            returning saldo""", nativeQuery = true)
    Optional<BigDecimal> debitar(UUID clienteId, BigDecimal monto);

    /** @return el saldo después del crédito, o vacío si no hay cartera */
    @Query(value = """
            update cartera set saldo = saldo + :monto, actualizado_en = now()
            where cliente_id = :clienteId
            returning saldo""", nativeQuery = true)
    Optional<BigDecimal> acreditar(UUID clienteId, BigDecimal monto);
}
