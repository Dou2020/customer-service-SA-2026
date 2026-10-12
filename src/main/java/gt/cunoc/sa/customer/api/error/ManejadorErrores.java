package gt.cunoc.sa.customer.api.error;

import java.util.LinkedHashMap;
import java.util.Map;

import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import gt.cunoc.sa.customer.domain.NitInvalidoException;
import gt.cunoc.sa.customer.domain.TransicionInvalidaException;

/**
 * Todos los errores hacia el cliente salen como ProblemDetail (RFC 9457); el frontend
 * muestra el "detail". Extiende ResponseEntityExceptionHandler para que los errores de
 * Spring MVC (404, 405, JSON ilegible…) también salgan así y no pasen por /error.
 */
@RestControllerAdvice
public class ManejadorErrores extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    /**
     * Las restricciones únicas de la base son el árbitro: la verificación previa en el caso de
     * uso solo da un mensaje mejor, y dos requests simultáneas que la pasan las dos terminan
     * aquí. Se traducen por nombre de restricción (los de las migraciones).
     */
    private static final Map<String, String> RESTRICCIONES = Map.of(
            "cliente_nit_key", "Ya existe un cliente con ese NIT",
            "cliente_usuario_id_key", "Esa cuenta ya está enlazada a otro cliente",
            "movimiento_cartera_idempotency_key_key", "Esa operación ya se registró",
            "tarifa_alquiler_tipo_fecha_uq", "Ya hay una versión de esa tarifa con esa fecha de vigencia");

    @ExceptionHandler(ErrorDeNegocio.class)
    public ProblemDetail errorDeNegocio(ErrorDeNegocio ex) {
        return ProblemDetail.forStatusAndDetail(ex.getEstado(), ex.getMessage());
    }

    @ExceptionHandler(NitInvalidoException.class)
    public ProblemDetail nitInvalido(NitInvalidoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    /** Un cambio de estado que el ciclo de vida del alquiler no permite. */
    @ExceptionHandler(TransicionInvalidaException.class)
    public ProblemDetail transicionInvalida(TransicionInvalidaException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(ServicioNoDisponibleException.class)
    public ProblemDetail noDisponible(ServicioNoDisponibleException ex) {
        log.warn("{}: {}", ex.getMessage(), ex.getCause() == null ? "" : ex.getCause().getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "El servicio no está disponible en este momento. Intenta de nuevo en unos minutos");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail integridad(DataIntegrityViolationException ex) {
        String restriccion = ex.getCause() instanceof ConstraintViolationException cve
                ? cve.getConstraintName() : null;
        String detalle = restriccion == null ? null : RESTRICCIONES.get(restriccion);
        if (detalle == null) {
            log.warn("Violación de integridad sin traducir ({})", restriccion, ex);
            detalle = "El cambio choca con datos que ya existen";
        }
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detalle);
    }

    /** @Version del alquiler: otro usuario lo guardó mientras tanto. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail concurrencia(OptimisticLockingFailureException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Alguien más modificó estos datos mientras tanto. Vuelve a cargarlos e intenta de nuevo");
    }

    /** Bloqueo que no se obtuvo (o interbloqueo que PostgreSQL resolvió abortando esta transacción). */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ProblemDetail bloqueo(PessimisticLockingFailureException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Otra operación está modificando estos datos. Intenta de nuevo");
    }

    /** ?sort= con una propiedad que no existe. */
    @ExceptionHandler(PropertyReferenceException.class)
    public ProblemDetail ordenInvalido(PropertyReferenceException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "No se puede ordenar por '" + ex.getPropertyName() + "'");
    }

    /**
     * Va en su propio handler: el genérico de Exception la atraparía y un @PreAuthorize
     * rechazado saldría como 500 en vez de 403.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail accesoDenegado(AccessDeniedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "No tienes permiso para esta acción");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail inesperado(Exception ex) {
        log.error("Error no controlado", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocurrió un error inesperado. Intenta de nuevo más tarde");
    }

    /** 400 con el mensaje de cada campo (en español por el locale fijo). */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errores = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errores.putIfAbsent(e.getField(), e.getDefaultMessage()));
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Hay datos inválidos en la solicitud");
        problema.setProperty("errores", errores);
        return ResponseEntity.badRequest().body(problema);
    }
}
