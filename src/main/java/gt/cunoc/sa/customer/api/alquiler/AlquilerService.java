package gt.cunoc.sa.customer.api.alquiler;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import gt.cunoc.sa.customer.api.alquiler.dto.AlquilerResponse;
import gt.cunoc.sa.customer.api.alquiler.dto.EstimacionResponse;
import gt.cunoc.sa.customer.api.alquiler.dto.ReporteIngresosResponse;
import gt.cunoc.sa.customer.api.alquiler.dto.TrayectoRequest;
import gt.cunoc.sa.customer.api.cliente.ClienteRepository;
import gt.cunoc.sa.customer.api.error.ErrorDeNegocio;
import gt.cunoc.sa.customer.application.Alcance;
import gt.cunoc.sa.customer.application.EventosDeAlquiler;
import gt.cunoc.sa.customer.application.LibroCartera;
import gt.cunoc.sa.customer.application.PropiedadesAlquiler;
import gt.cunoc.sa.customer.application.ResolucionDeClientes;
import gt.cunoc.sa.customer.domain.AlquilerEntity;
import gt.cunoc.sa.customer.domain.Calendario;
import gt.cunoc.sa.customer.domain.Canal;
import gt.cunoc.sa.customer.domain.ClienteEntity;
import gt.cunoc.sa.customer.domain.Distancia;
import gt.cunoc.sa.customer.domain.EstadoAlquiler;
import gt.cunoc.sa.customer.domain.PrecioAlquiler;
import gt.cunoc.sa.customer.domain.Rol;
import gt.cunoc.sa.customer.domain.TipoMovimiento;
import gt.cunoc.sa.customer.domain.TipoTarifa;
import gt.cunoc.sa.customer.domain.Trayecto;
import gt.cunoc.sa.customer.infrastructure.messaging.eventos.AlquilerCancelado.Reembolso;
import gt.cunoc.sa.customer.infrastructure.messaging.eventos.AlquilerPagado.MetodoPago;
import gt.cunoc.sa.customer.infrastructure.security.UsuarioActual;
import jakarta.persistence.criteria.Predicate;

/**
 * Alquileres de bus (README, "Ciclo de vida de un alquiler"). Quién hace qué:
 *
 * - El cliente (cualquier cuenta) solicita, cancela antes de pagar y paga con su cartera.
 * - El cajero o el admin de la sucursal solicita en mostrador y cobra en efectivo.
 * - El admin de la sucursal (o un SYS_ADMIN) confirma el precio o rechaza la solicitud.
 *
 * Asignar bus y chofer, salir, llegar y eliminar el viaje se hacen en trip; aquí llegan por eventos
 * (CicloDeAlquileres).
 */
@Service
public class AlquilerService {

    /** Pagados y no cancelados: lo que cuenta como ingreso. */
    private static final Set<EstadoAlquiler> PAGADOS = EnumSet.of(EstadoAlquiler.PAGADO, EstadoAlquiler.ASIGNADO,
            EstadoAlquiler.EN_CURSO, EstadoAlquiler.FINALIZADO);

    private final AlquilerRepository alquileres;
    private final ClienteRepository clientes;
    private final TarifaAlquilerRepository tarifas;
    private final ResolucionDeClientes resolucion;
    private final LibroCartera libro;
    private final EventosDeAlquiler eventos;
    private final PropiedadesAlquiler propiedades;

    public AlquilerService(AlquilerRepository alquileres, ClienteRepository clientes, TarifaAlquilerRepository tarifas,
            ResolucionDeClientes resolucion, LibroCartera libro, EventosDeAlquiler eventos,
            PropiedadesAlquiler propiedades) {
        this.alquileres = alquileres;
        this.clientes = clientes;
        this.tarifas = tarifas;
        this.resolucion = resolucion;
        this.libro = libro;
        this.eventos = eventos;
        this.propiedades = propiedades;
    }

    /** Vista previa del formulario: el mismo cálculo que la solicitud, sin guardar nada. */
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public EstimacionResponse estimar(TrayectoRequest trayecto, LocalDate fechaSolicitud, LocalDate fechaSalida,
            LocalDate fechaRetorno) {
        return estimacion(trayecto.aDominio(), conHoy(fechaSolicitud), fechaSalida, fechaRetorno);
    }

    /** En línea: el cliente es el de la cuenta, y la sucursal la que eligió de la lista de identity. */
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public AlquilerResponse solicitar(UUID sucursalId, TrayectoRequest trayecto, LocalDate fechaSolicitud,
            LocalDate fechaSalida, LocalDate fechaRetorno, int pasajeros, UsuarioActual actual) {
        ClienteEntity cliente = resolucion.deLaCuenta(actual.usuarioId());
        return crear(cliente, sucursalId, Canal.ONLINE, trayecto.aDominio(), conHoy(fechaSolicitud), fechaSalida,
                fechaRetorno, pasajeros);
    }

    /** En mostrador: el cliente se resuelve por NIT (como en la venta) y la sucursal es la del token. */
    @PreAuthorize("hasAnyRole('BRANCH_ADMIN', 'CASHIER')")
    @Transactional
    public AlquilerResponse solicitarEnMostrador(String nit, String nombre, TrayectoRequest trayecto,
            LocalDate fechaSolicitud, LocalDate fechaSalida, LocalDate fechaRetorno, int pasajeros,
            UsuarioActual actual) {
        UUID sucursal = Alcance.sucursalDe(actual);
        ClienteEntity cliente = resolucion.porNit(nit, nombre);
        return crear(cliente, sucursal, Canal.PRESENCIAL, trayecto.aDominio(), conHoy(fechaSolicitud), fechaSalida,
                fechaRetorno, pasajeros);
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public Page<AlquilerResponse> mios(UsuarioActual actual, Pageable pagina) {
        ClienteEntity cliente = resolucion.deLaCuenta(actual.usuarioId());
        return alquileres.findByClienteId(cliente.getId(), pagina).map(a -> AlquilerResponse.de(a, cliente));
    }

    /** Un BRANCH_ADMIN o CASHIER solo ve su sucursal: el filtro va en la consulta, no después. */
    @PreAuthorize("hasAnyRole('SYS_ADMIN', 'BRANCH_ADMIN', 'CASHIER')")
    @Transactional(readOnly = true)
    public Page<AlquilerResponse> listar(EstadoAlquiler estado, UUID sucursalId, Pageable pagina,
            UsuarioActual actual) {
        UUID alcance = Alcance.esSysAdmin(actual) ? sucursalId : Alcance.sucursalDe(actual);
        Specification<AlquilerEntity> filtro = (raiz, consulta, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (alcance != null) {
                condiciones.add(cb.equal(raiz.get("sucursalId"), alcance));
            }
            if (estado != null) {
                condiciones.add(cb.equal(raiz.get("estado"), estado));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
        Page<AlquilerEntity> encontrados = alquileres.findAll(filtro, pagina);
        Map<UUID, ClienteEntity> suyos = clientesDe(encontrados.getContent());
        return encontrados.map(a -> AlquilerResponse.de(a, suyos.get(a.getClienteId())));
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public AlquilerResponse obtener(UUID id, UsuarioActual actual) {
        AlquilerEntity alquiler = buscar(id);
        ClienteEntity cliente = clienteDe(alquiler);
        if (!esDueno(cliente, actual)) {
            exigirPersonalDeLaSucursal(alquiler, actual);
        }
        return AlquilerResponse.de(alquiler, cliente);
    }

    /** Emite AlquilerConfirmado (E13): el cliente recibe un correo para pagar. */
    @PreAuthorize("hasAnyRole('SYS_ADMIN', 'BRANCH_ADMIN')")
    @Transactional
    public AlquilerResponse confirmar(UUID id, BigDecimal precioFinal, UsuarioActual actual) {
        AlquilerEntity alquiler = buscar(id);
        Alcance.exigirSucursal(actual, alquiler.getSucursalId());
        alquiler.confirmar(precioFinal);
        ClienteEntity cliente = clienteDe(alquiler);
        eventos.confirmado(alquiler, cliente);
        return AlquilerResponse.de(alquiler, cliente);
    }

    @PreAuthorize("hasAnyRole('SYS_ADMIN', 'BRANCH_ADMIN')")
    @Transactional
    public AlquilerResponse rechazar(UUID id, String motivo, UsuarioActual actual) {
        AlquilerEntity alquiler = buscar(id);
        Alcance.exigirSucursal(actual, alquiler.getSucursalId());
        alquiler.rechazar(motivo.strip());
        return AlquilerResponse.de(alquiler, clienteDe(alquiler));
    }

    /**
     * Antes de pagar, lo cancela el cliente o el personal de la sucursal. Emite AlquilerCancelado
     * (E16) sin reembolso: no se había cobrado. Después del pago → 409 (supuesto 12).
     */
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public AlquilerResponse cancelar(UUID id, String motivo, UsuarioActual actual) {
        AlquilerEntity alquiler = buscar(id);
        ClienteEntity cliente = clienteDe(alquiler);
        if (!esDueno(cliente, actual)) {
            exigirPersonalDeLaSucursal(alquiler, actual);
        }
        alquiler.cancelar(motivo == null || motivo.isBlank() ? null : motivo.strip());
        eventos.cancelado(alquiler, cliente, false, Calendario.hoy(), Reembolso.NINGUNO);
        return AlquilerResponse.de(alquiler, cliente);
    }

    /**
     * Pago en línea con la cartera: débito, PAGADO y AlquilerPagado (E14) en una sola transacción
     * local, porque cartera y alquiler viven aquí. No hace falta saga.
     *
     * Dos pagos simultáneos del mismo alquiler: el segundo choca con la clave ALQUILER:id del primero
     * y se revierte entero (409), también su débito.
     */
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public AlquilerResponse pagar(UUID id, LocalDate fechaPago, UsuarioActual actual) {
        AlquilerEntity alquiler = buscar(id);
        ClienteEntity cliente = clienteDe(alquiler);
        if (!esDueno(cliente, actual)) {
            throw new AccessDeniedException("Solo el cliente paga su alquiler en línea");
        }
        LocalDate fecha = conHoy(fechaPago);
        alquiler.pagar(fecha);
        libro.debitar(cliente.getId(), TipoMovimiento.DEBITO_ALQUILER, alquiler.getPrecioFinal(), fecha,
                        alquiler.getId(), LibroCartera.clavePagoAlquiler(alquiler.getId()))
                .orElseThrow(() -> ErrorDeNegocio.noProcesable(LibroCartera.SALDO_INSUFICIENTE));
        eventos.pagado(alquiler, cliente, MetodoPago.CARTERA);
        return AlquilerResponse.de(alquiler, cliente);
    }

    /** Cobro en efectivo en el mostrador de la sucursal que atiende el alquiler. */
    @PreAuthorize("hasAnyRole('BRANCH_ADMIN', 'CASHIER')")
    @Transactional
    public AlquilerResponse pagarEnMostrador(UUID id, LocalDate fechaPago, UsuarioActual actual) {
        AlquilerEntity alquiler = buscar(id);
        Alcance.exigirSucursal(actual, alquiler.getSucursalId());
        alquiler.pagar(conHoy(fechaPago));
        ClienteEntity cliente = clienteDe(alquiler);
        eventos.pagado(alquiler, cliente, MetodoPago.EFECTIVO);
        return AlquilerResponse.de(alquiler, cliente);
    }

    /**
     * Ingresos por alquiler de buses: los pagados y no cancelados, por fecha de pago (la que eligió
     * quien pagó). Sin intervalo, todos. Un BRANCH_ADMIN siempre ve solo su sucursal.
     */
    @PreAuthorize("hasAnyRole('SYS_ADMIN', 'BRANCH_ADMIN')")
    @Transactional(readOnly = true)
    public ReporteIngresosResponse reporteIngresos(LocalDate desde, LocalDate hasta, UUID sucursalId,
            UsuarioActual actual) {
        UUID alcance = Alcance.esSysAdmin(actual) ? sucursalId : Alcance.sucursalDe(actual);
        Specification<AlquilerEntity> filtro = (raiz, consulta, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            condiciones.add(raiz.get("estado").in(PAGADOS));
            if (alcance != null) {
                condiciones.add(cb.equal(raiz.get("sucursalId"), alcance));
            }
            if (desde != null) {
                condiciones.add(cb.greaterThanOrEqualTo(raiz.get("fechaPago"), desde));
            }
            if (hasta != null) {
                condiciones.add(cb.lessThanOrEqualTo(raiz.get("fechaPago"), hasta));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
        List<AlquilerEntity> pagados = alquileres.findAll(filtro, Sort.by("fechaPago", "creadoEn"));
        Map<UUID, ClienteEntity> suyos = clientesDe(pagados);
        List<ReporteIngresosResponse.Fila> filas = pagados.stream()
                .map(a -> ReporteIngresosResponse.Fila.de(a, suyos.get(a.getClienteId())))
                .toList();
        BigDecimal total = filas.stream().map(ReporteIngresosResponse.Fila::precio).reduce(BigDecimal.ZERO,
                BigDecimal::add);
        return new ReporteIngresosResponse(desde, hasta, alcance, filas, filas.size(), total);
    }

    private AlquilerResponse crear(ClienteEntity cliente, UUID sucursalId, Canal canal, Trayecto trayecto,
            LocalDate fechaSolicitud, LocalDate fechaSalida, LocalDate fechaRetorno, int pasajeros) {
        EstimacionResponse estimacion = estimacion(trayecto, fechaSolicitud, fechaSalida, fechaRetorno);
        AlquilerEntity alquiler = alquileres.save(AlquilerEntity.solicitado(cliente.getId(), sucursalId, canal,
                trayecto, estimacion.distanciaKm(), fechaSolicitud, fechaSalida, fechaRetorno, pasajeros,
                estimacion.precioEstimado(), Instant.now()));
        return AlquilerResponse.de(alquiler, cliente);
    }

    /**
     * Supuesto 8: distancia × tarifa por km × (2 si es ida y vuelta) + días × cargo diario, con las
     * tarifas vigentes a la fecha de la solicitud.
     */
    private EstimacionResponse estimacion(Trayecto trayecto, LocalDate fechaSolicitud, LocalDate fechaSalida,
            LocalDate fechaRetorno) {
        if (fechaSalida.isBefore(fechaSolicitud)) {
            throw ErrorDeNegocio.noProcesable("La fecha de salida no puede ser anterior a la de la solicitud");
        }
        if (fechaRetorno != null && fechaRetorno.isBefore(fechaSalida)) {
            throw ErrorDeNegocio.noProcesable("La fecha de retorno no puede ser anterior a la de salida");
        }
        BigDecimal distancia = Distancia.estimadaKm(trayecto, propiedades.factorCarretera());
        if (distancia.compareTo(BigDecimal.ONE) < 0) {
            throw ErrorDeNegocio.noProcesable("El origen y el destino están en el mismo lugar");
        }
        BigDecimal tarifaKm = tarifaVigente(TipoTarifa.ALQUILER_KM, fechaSolicitud);
        BigDecimal cargoDia = tarifaVigente(TipoTarifa.ALQUILER_DIA, fechaSolicitud);
        int dias = PrecioAlquiler.dias(fechaSalida, fechaRetorno);
        boolean idaYVuelta = fechaRetorno != null;
        return new EstimacionResponse(distancia, idaYVuelta, dias, tarifaKm, cargoDia,
                PrecioAlquiler.estimar(distancia, idaYVuelta, dias, tarifaKm, cargoDia));
    }

    private BigDecimal tarifaVigente(TipoTarifa tipo, LocalDate fecha) {
        return tarifas.findFirstByTipoAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(tipo, fecha)
                .orElseThrow(() -> ErrorDeNegocio.noProcesable("Todavía no hay tarifas de alquiler vigentes a esa "
                        + "fecha: el administrador del sistema tiene que configurarlas"))
                .getValor();
    }

    private AlquilerEntity buscar(UUID id) {
        return alquileres.findById(id).orElseThrow(() -> ErrorDeNegocio.noEncontrado("No existe ese alquiler"));
    }

    private ClienteEntity clienteDe(AlquilerEntity alquiler) {
        return clientes.findById(alquiler.getClienteId()).orElseThrow();
    }

    private Map<UUID, ClienteEntity> clientesDe(List<AlquilerEntity> lista) {
        Set<UUID> ids = lista.stream().map(AlquilerEntity::getClienteId).collect(Collectors.toSet());
        return clientes.findAllById(ids).stream().collect(Collectors.toMap(ClienteEntity::getId, Function.identity()));
    }

    private static boolean esDueno(ClienteEntity cliente, UsuarioActual actual) {
        return actual.usuarioId().equals(cliente.getUsuarioId());
    }

    /** SYS_ADMIN, o BRANCH_ADMIN y CASHIER de la sucursal que atiende el alquiler. */
    private static void exigirPersonalDeLaSucursal(AlquilerEntity alquiler, UsuarioActual actual) {
        boolean personal = Alcance.esSysAdmin(actual) || actual.tieneRol(Rol.BRANCH_ADMIN.name())
                || actual.tieneRol(Rol.CASHIER.name());
        if (!personal) {
            throw new AccessDeniedException("Alquiler de otro cliente");
        }
        Alcance.exigirSucursal(actual, alquiler.getSucursalId());
    }

    private static LocalDate conHoy(LocalDate fecha) {
        return fecha == null ? Calendario.hoy() : fecha;
    }
}
