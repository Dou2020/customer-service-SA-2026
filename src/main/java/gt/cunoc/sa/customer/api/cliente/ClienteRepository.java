package gt.cunoc.sa.customer.api.cliente;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import gt.cunoc.sa.customer.domain.ClienteEntity;
import jakarta.persistence.LockModeType;

/**
 * Las altas son SQL nativo con ON CONFLICT: la UQ de cliente.nit es el único árbitro entre la
 * venta en mostrador (S5) y el enlace de una cuenta (E4), como la PK de asiento_ocupado con los
 * asientos (README, "Carreras y su árbitro"). Consultar y luego insertar dejaría que los dos
 * pasaran la consulta.
 */
public interface ClienteRepository extends JpaRepository<ClienteEntity, UUID>,
        JpaSpecificationExecutor<ClienteEntity> {

    Optional<ClienteEntity> findByUsuarioId(UUID usuarioId);

    Optional<ClienteEntity> findByNit(String nit);

    Optional<ClienteEntity> findByGenericoTrue();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ClienteEntity c where c.usuarioId = :usuarioId")
    Optional<ClienteEntity> bloquearPorUsuario(UUID usuarioId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ClienteEntity c where c.nit = :nit")
    Optional<ClienteEntity> bloquearPorNit(String nit);

    /**
     * Venta o alquiler en mostrador con un NIT que quizá no existe. Si dos cajeros (o un cajero y
     * un E4) lo insertan a la vez, el segundo espera al primero y no hace nada; quien llama relee.
     */
    @Modifying
    @Query(value = """
            insert into cliente (id, nit, nombre, generico, creado_en)
            values (:id, :nit, :nombre, false, :ahora)
            on conflict (nit) do nothing""", nativeQuery = true)
    int insertarPorNit(UUID id, String nit, String nombre, Instant ahora);

    /**
     * Alta de una cuenta con NIT (E4): inserta el cliente, o enlaza el que ya compró en mostrador
     * con ese NIT. El WHERE del DO UPDATE no deja tomar el de otra cuenta: entonces no devuelve
     * fila. Los datos de contacto pasan a ser los de la cuenta.
     *
     * @return el id del cliente enlazado, o vacío si el NIT ya es de otra cuenta
     */
    @Query(value = """
            insert into cliente (id, nit, nombre, correo, telefono, usuario_id, generico, perfil_version, creado_en)
            values (:id, :nit, :nombre, :correo, :telefono, :usuarioId, false, :version, :ahora)
            on conflict (nit) do update
                set usuario_id = excluded.usuario_id, nombre = excluded.nombre, correo = excluded.correo,
                    telefono = excluded.telefono, perfil_version = excluded.perfil_version
                where cliente.usuario_id is null and not cliente.generico
            returning id""", nativeQuery = true)
    Optional<UUID> enlazarPorNit(UUID id, String nit, String nombre, String correo, String telefono,
            UUID usuarioId, long version, Instant ahora);

    /** Alta de una cuenta con NIT CF: cliente propio sin NIT, que nunca toca el genérico. */
    @Modifying
    @Query(value = """
            insert into cliente (id, nit, nombre, correo, telefono, usuario_id, generico, perfil_version, creado_en)
            values (:id, null, :nombre, :correo, :telefono, :usuarioId, false, :version, :ahora)""",
            nativeQuery = true)
    int insertarCuentaSinNit(UUID id, String nombre, String correo, String telefono, UUID usuarioId, long version,
            Instant ahora);

    /** Absorción: las lápidas que apuntaban al absorbido apuntan ahora a la cuenta (un solo salto). */
    @Modifying
    @Query(value = "update cliente set absorbido_por = :cuenta where absorbido_por = :absorbido", nativeQuery = true)
    int reapuntarAbsorbidos(UUID absorbido, UUID cuenta);
}
