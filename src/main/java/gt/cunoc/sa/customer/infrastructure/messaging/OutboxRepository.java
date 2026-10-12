package gt.cunoc.sa.customer.infrastructure.messaging;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Pendientes en orden de inserción. SKIP LOCKED: si alguna vez corren dos relays (dos
     * réplicas, o una pasada que se encima con la siguiente), cada uno toma filas distintas en
     * vez de esperar o publicar dos veces lo mismo.
     */
    @Query(value = """
            select * from outbox_event
            where publicado_en is null
            order by id
            limit :limite
            for update skip locked""", nativeQuery = true)
    List<OutboxEvent> bloquearPendientes(int limite);
}
