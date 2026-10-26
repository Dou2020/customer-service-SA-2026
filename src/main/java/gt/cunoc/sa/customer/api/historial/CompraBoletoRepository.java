package gt.cunoc.sa.customer.api.historial;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import gt.cunoc.sa.customer.domain.CompraBoletoEntity;

/** La PK es el ventaId de sales, como texto (V3). */
public interface CompraBoletoRepository extends JpaRepository<CompraBoletoEntity, String>,
        JpaSpecificationExecutor<CompraBoletoEntity> {

    /** Absorción (cambio de NIT): lo comprado en mostrador con ese NIT pasa a la cuenta. */
    @Modifying
    @Query(value = "update compra_boleto set cliente_id = :cuenta where cliente_id = :absorbido", nativeQuery = true)
    int reasignarCliente(UUID absorbido, UUID cuenta);
}
