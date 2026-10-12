package gt.cunoc.sa.customer.api.interno.dto;

import jakarta.validation.constraints.Size;

/**
 * S5 en mostrador.
 *
 * @param nit    el que dio el comprador; vacío o "CF" es el cliente genérico
 * @param nombre para un NIT que todavía no existe (si no, "Cliente NIT …")
 */
public record ResolucionClienteRequest(
        @Size(max = 20) String nit,
        @Size(max = 150) String nombre) {
}
