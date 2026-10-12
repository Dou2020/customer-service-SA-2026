package gt.cunoc.sa.customer.api.historial;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import gt.cunoc.sa.customer.api.alquiler.AlquilerRepository;
import gt.cunoc.sa.customer.api.alquiler.dto.AlquilerResponse;
import gt.cunoc.sa.customer.api.historial.dto.CompraResponse;
import gt.cunoc.sa.customer.api.historial.dto.HistorialResponse;
import gt.cunoc.sa.customer.application.ResolucionDeClientes;
import gt.cunoc.sa.customer.domain.AlquilerEntity;
import gt.cunoc.sa.customer.domain.ClienteEntity;
import gt.cunoc.sa.customer.domain.CompraBoletoEntity;
import gt.cunoc.sa.customer.infrastructure.security.UsuarioActual;
import jakarta.persistence.criteria.Predicate;

/**
 * El historial de la cuenta: las filas de compra_boleto y alquiler de su cliente. Es una consulta
 * local porque el enlace (cliente.usuario_id) y las réplicas viven en la misma base.
 */
@Service
public class HistorialService {

    private final CompraBoletoRepository compras;
    private final AlquilerRepository alquileres;
    private final ResolucionDeClientes resolucion;

    public HistorialService(CompraBoletoRepository compras, AlquilerRepository alquileres,
            ResolucionDeClientes resolucion) {
        this.compras = compras;
        this.alquileres = alquileres;
        this.resolucion = resolucion;
    }

    /** Boletos por fecha de pago y alquileres por fecha de solicitud, los más recientes primero. */
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public HistorialResponse historial(UsuarioActual actual, LocalDate desde, LocalDate hasta) {
        ClienteEntity cliente = resolucion.deLaCuenta(actual.usuarioId());
        Specification<CompraBoletoEntity> suyasPorPago = delCliente(cliente, "fechaPago", desde, hasta);
        Specification<AlquilerEntity> suyosPorSolicitud = delCliente(cliente, "fechaSolicitud", desde, hasta);
        List<CompraResponse> boletos = compras.findAll(suyasPorPago,
                Sort.by(Sort.Direction.DESC, "fechaPago", "salida")).stream().map(CompraResponse::de).toList();
        List<AlquilerResponse> suyos = alquileres.findAll(suyosPorSolicitud,
                Sort.by(Sort.Direction.DESC, "fechaSolicitud", "creadoEn")).stream()
                .map(a -> AlquilerResponse.de(a, cliente)).toList();
        return new HistorialResponse(cliente.getId(), cliente.getNit(), cliente.getNombre(), boletos, suyos);
    }

    private static <T> Specification<T> delCliente(ClienteEntity cliente, String fecha, LocalDate desde, LocalDate hasta) {
        return (raiz, consulta, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            condiciones.add(cb.equal(raiz.get("clienteId"), cliente.getId()));
            if (desde != null) {
                condiciones.add(cb.greaterThanOrEqualTo(raiz.get(fecha), desde));
            }
            if (hasta != null) {
                condiciones.add(cb.lessThanOrEqualTo(raiz.get(fecha), hasta));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
    }
}
