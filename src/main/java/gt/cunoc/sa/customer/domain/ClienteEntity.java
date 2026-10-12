package gt.cunoc.sa.customer.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un cliente por NIT, con o sin cuenta (README, "Cliente, cuenta y NIT"). Las ventas y los
 * alquileres apuntan al cliente y no al usuario: enlazar una cuenta es llenar usuario_id, y lo
 * comprado antes aparece en su historial sin mover nada.
 *
 * Las altas no pasan por aquí: el upsert por NIT (ON CONFLICT) es SQL nativo en ClienteRepository,
 * porque la UQ de nit es el árbitro entre PerfilGuardado (E4) y la venta en mostrador (S5).
 */
@Entity
@Table(name = "cliente")
public class ClienteEntity extends ConIdAsignado {

    @Id
    private UUID id;

    // Normalizado (Nit). 'CF' solo en la fila genérica; NULL en una cuenta con CF y en las lápidas.
    @Column(length = 15, unique = true)
    private String nit;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 150)
    private String correo;

    @Column(length = 15)
    private String telefono;

    @Column(name = "usuario_id", unique = true)
    private UUID usuarioId;

    @Column(nullable = false)
    private boolean generico;

    // La cuenta que absorbió sus compras al tomar su NIT. Se mapea como id y no como relación:
    // solo se sigue el puntero, nunca se navega el grafo.
    @Column(name = "absorbido_por")
    private UUID absorbidoPor;

    // aggregateVersion del último PerfilGuardado (E4) aplicado.
    @Column(name = "perfil_version")
    private Long perfilVersion;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn;

    protected ClienteEntity() {
    }

    /** Un E4 que llega tarde (dos ediciones al revés) no pisa al que ya se aplicó. */
    public boolean aceptaVersionDePerfil(long version) {
        return perfilVersion == null || version > perfilVersion;
    }

    /** Los datos de contacto los dicta la cuenta: el nombre, el correo y el teléfono del perfil. */
    public void aplicarPerfil(String nombre, String correo, String telefono, long version) {
        this.nombre = nombre;
        this.correo = correo;
        this.telefono = telefono;
        this.perfilVersion = version;
    }

    /** @param nit normalizado, o null si la cuenta pasó a CF */
    public void cambiarNit(String nit) {
        this.nit = nit;
    }

    /** Lápida: suelta el NIT para que lo tome la cuenta y deja el puntero para los eventos tardíos. */
    public void absorberEn(UUID cuenta) {
        this.nit = null;
        this.absorbidoPor = cuenta;
    }

    public boolean tieneCuenta() {
        return usuarioId != null;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public String getNit() {
        return nit;
    }

    public String getNombre() {
        return nombre;
    }

    public String getCorreo() {
        return correo;
    }

    public String getTelefono() {
        return telefono;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public boolean isGenerico() {
        return generico;
    }

    public UUID getAbsorbidoPor() {
        return absorbidoPor;
    }

    public Long getPerfilVersion() {
        return perfilVersion;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }
}
