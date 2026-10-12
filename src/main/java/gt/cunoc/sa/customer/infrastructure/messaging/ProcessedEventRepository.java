package gt.cunoc.sa.customer.infrastructure.messaging;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEvent.Clave> {

    /**
     * ON CONFLICT DO NOTHING en vez de dejar que la PK lance: en PostgreSQL una violación
     * aborta la transacción, y el duplicado se tiene que poder confirmar (ack) sin error.
     *
     * @return 1 si es la primera vez que este consumidor ve el evento, 0 si es un duplicado
     */
    @Modifying
    @Query(value = """
            insert into processed_event (event_id, consumidor, procesado_en)
            values (:eventId, :consumidor, now())
            on conflict do nothing""", nativeQuery = true)
    int registrar(UUID eventId, String consumidor);
}
