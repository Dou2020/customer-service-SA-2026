package gt.cunoc.sa.customer.domain;

import org.springframework.data.domain.Persistable;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;

/**
 * Las entidades con id asignado en el código no se pueden distinguir de una existente por el
 * id: Spring Data haría merge() en vez de persist(), con un SELECT de más, y Hibernate 6.6
 * trata como desactualizada (OptimisticLockException) a una entidad con @Version que no está
 * en la base. Este marcador le dice a save() cuándo es nueva.
 *
 * Genérico por el tipo del id: casi todas usan un UUID propio, pero la réplica de una venta
 * (CompraBoletoEntity) usa el id de sales, que es texto. En identity es Persistable<UUID>.
 */
@MappedSuperclass
public abstract class ConIdAsignado<ID> implements Persistable<ID> {

    @Transient
    private boolean nueva = true;

    @Override
    public boolean isNew() {
        return nueva;
    }

    @PostLoad
    @PostPersist
    void marcarPersistida() {
        nueva = false;
    }
}
