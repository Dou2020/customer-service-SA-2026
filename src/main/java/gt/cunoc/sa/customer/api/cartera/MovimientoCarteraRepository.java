package gt.cunoc.sa.customer.api.cartera;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import gt.cunoc.sa.customer.domain.MovimientoCarteraEntity;

public interface MovimientoCarteraRepository extends JpaRepository<MovimientoCarteraEntity, Long>,
        JpaSpecificationExecutor<MovimientoCarteraEntity> {

    Optional<MovimientoCarteraEntity> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    /**
     * Lápida del reconciliador: DEBITO_ANULADO con monto 0 y la clave del débito incierto. Si el
     * débito ya entró, choca con su clave y no hace nada; si entra, un débito tardío con esa
     * clave choca con ella. ON CONFLICT y no dejar que la UQ lance: una violación abortaría la
     * transacción antes de poder leer quién ganó.
     *
     * @return 1 si la lápida entró, 0 si la clave ya existía (o el cliente no tiene cartera)
     */
    @Modifying
    @Query(value = """
            insert into movimiento_cartera
                (cliente_id, tipo, monto, fecha, referencia_id, idempotency_key, saldo_resultante, registrado_en)
            select c.cliente_id, 'DEBITO_ANULADO', 0, :fecha, null, :clave, c.saldo, now()
            from cartera c where c.cliente_id = :clienteId
            on conflict (idempotency_key) do nothing""", nativeQuery = true)
    int insertarLapida(UUID clienteId, String clave, LocalDate fecha);
}
