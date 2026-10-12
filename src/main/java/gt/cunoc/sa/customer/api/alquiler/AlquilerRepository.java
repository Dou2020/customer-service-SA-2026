package gt.cunoc.sa.customer.api.alquiler;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import gt.cunoc.sa.customer.domain.AlquilerEntity;

public interface AlquilerRepository extends JpaRepository<AlquilerEntity, UUID>,
        JpaSpecificationExecutor<AlquilerEntity> {

    Page<AlquilerEntity> findByClienteId(UUID clienteId, Pageable pagina);

    List<AlquilerEntity> findByClienteIdOrderByFechaSolicitudDescCreadoEnDesc(UUID clienteId);

    /** Absorción (cambio de NIT): los alquileres del cliente absorbido pasan a la cuenta. */
    @Modifying
    @Query(value = "update alquiler set cliente_id = :cuenta where cliente_id = :absorbido", nativeQuery = true)
    int reasignarCliente(UUID absorbido, UUID cuenta);
}
