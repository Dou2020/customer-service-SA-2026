package gt.cunoc.sa.customer.api.cliente;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import gt.cunoc.sa.customer.api.cliente.dto.ClienteResponse;
import gt.cunoc.sa.customer.domain.ClienteEntity;
import gt.cunoc.sa.customer.domain.Nit;
import jakarta.persistence.criteria.Predicate;

/**
 * Consulta de clientes para el personal: el mostrador busca por NIT para mostrar el nombre antes de
 * vender. Los clientes son de todas las sucursales (cualquiera compra en cualquiera), así que no
 * hay filtro de alcance.
 */
@Service
public class ClienteService {

    private final ClienteRepository clientes;

    public ClienteService(ClienteRepository clientes) {
        this.clientes = clientes;
    }

    /** Las lápidas (clientes absorbidos por una cuenta) no aparecen: ya no tienen NIT ni compras. */
    @PreAuthorize("hasAnyRole('SYS_ADMIN', 'BRANCH_ADMIN', 'CASHIER')")
    @Transactional(readOnly = true)
    public Page<ClienteResponse> buscar(String nit, String nombre, Pageable pagina) {
        String nitBuscado = nit == null || nit.isBlank() ? null : Nit.normalizar(nit);
        Specification<ClienteEntity> filtro = (raiz, consulta, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            condiciones.add(cb.isNull(raiz.get("absorbidoPor")));
            if (nitBuscado != null) {
                condiciones.add(cb.equal(raiz.get("nit"), nitBuscado));
            }
            if (nombre != null && !nombre.isBlank()) {
                condiciones.add(cb.like(cb.lower(raiz.get("nombre")), "%" + nombre.strip().toLowerCase(Locale.ROOT) + "%"));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
        return clientes.findAll(filtro, pagina).map(ClienteResponse::de);
    }
}
