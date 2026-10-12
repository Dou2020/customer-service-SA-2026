package gt.cunoc.sa.customer.api.alquiler;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import gt.cunoc.sa.customer.domain.TarifaAlquilerEntity;
import gt.cunoc.sa.customer.domain.TipoTarifa;

public interface TarifaAlquilerRepository extends JpaRepository<TarifaAlquilerEntity, Long> {

    /** La versión vigente a la fecha: la de vigente_desde más reciente que no la pasa. */
    Optional<TarifaAlquilerEntity> findFirstByTipoAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(TipoTarifa tipo,
            LocalDate fecha);

    /** Con el id de identity: un ConfiguracionActualizada repetido no duplica la versión. */
    @Modifying
    @Query(value = """
            insert into tarifa_alquiler (id, tipo, valor, vigente_desde)
            values (:id, :tipo, :valor, :vigenteDesde)
            on conflict do nothing""", nativeQuery = true)
    int insertar(long id, String tipo, BigDecimal valor, LocalDate vigenteDesde);
}
