package gt.cunoc.sa.customer.api.alquiler;

import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import gt.cunoc.sa.customer.api.alquiler.dto.AlquilerResponse;
import gt.cunoc.sa.customer.api.alquiler.dto.CancelacionRequest;
import gt.cunoc.sa.customer.api.alquiler.dto.ConfirmacionRequest;
import gt.cunoc.sa.customer.api.alquiler.dto.EstimacionRequest;
import gt.cunoc.sa.customer.api.alquiler.dto.EstimacionResponse;
import gt.cunoc.sa.customer.api.alquiler.dto.PagoRequest;
import gt.cunoc.sa.customer.api.alquiler.dto.RechazoRequest;
import gt.cunoc.sa.customer.api.alquiler.dto.ReporteIngresosResponse;
import gt.cunoc.sa.customer.api.alquiler.dto.SolicitudAlquilerRequest;
import gt.cunoc.sa.customer.api.alquiler.dto.SolicitudMostradorRequest;
import gt.cunoc.sa.customer.domain.EstadoAlquiler;
import gt.cunoc.sa.customer.infrastructure.security.UsuarioActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/alquileres")
@Tag(name = "Alquileres", description = "Alquiler de buses: solicitud, precio, pago y su ciclo de vida")
public class AlquilerController {

    private final AlquilerService servicio;

    public AlquilerController(AlquilerService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/estimacion")
    @Operation(summary = "Calcula el precio estimado sin guardar nada",
            description = "422 si las fechas no cuadran, si origen y destino coinciden o si no hay tarifas vigentes")
    public EstimacionResponse estimar(@RequestBody @Valid EstimacionRequest solicitud) {
        return servicio.estimar(solicitud.trayecto(), solicitud.fechaSolicitud(), solicitud.fechaSalida(),
                solicitud.fechaRetorno());
    }

    @PostMapping
    @Operation(summary = "Solicita un alquiler en línea (queda SOLICITADO con su precio estimado)",
            description = "El cliente es el de la cuenta. 409 si la cuenta se acaba de confirmar; mismos 422 que la "
                    + "estimación")
    public ResponseEntity<AlquilerResponse> solicitar(@RequestBody @Valid SolicitudAlquilerRequest solicitud,
            @AuthenticationPrincipal UsuarioActual actual) {
        return creado(servicio.solicitar(solicitud.sucursalId(), solicitud.trayecto(), solicitud.fechaSolicitud(),
                solicitud.fechaSalida(), solicitud.fechaRetorno(), solicitud.pasajeros(), actual));
    }

    @PostMapping("/mostrador")
    @Operation(summary = "Registra en mostrador la solicitud de un cliente identificado por NIT",
            description = "BRANCH_ADMIN y CASHIER, en su sucursal. Sin NIT, el cliente genérico CF. 422 si el NIT "
                    + "no es válido")
    public ResponseEntity<AlquilerResponse> solicitarEnMostrador(
            @RequestBody @Valid SolicitudMostradorRequest solicitud, @AuthenticationPrincipal UsuarioActual actual) {
        return creado(servicio.solicitarEnMostrador(solicitud.nit(), solicitud.nombre(), solicitud.trayecto(),
                solicitud.fechaSolicitud(), solicitud.fechaSalida(), solicitud.fechaRetorno(), solicitud.pasajeros(),
                actual));
    }

    @GetMapping("/mios")
    @Operation(summary = "Los alquileres de la cuenta, del más reciente al más antiguo")
    public Page<AlquilerResponse> mios(
            @PageableDefault(size = 20, sort = "creadoEn", direction = Sort.Direction.DESC) Pageable pagina,
            @AuthenticationPrincipal UsuarioActual actual) {
        return servicio.mios(actual, pagina);
    }

    @GetMapping
    @Operation(summary = "Alquileres de la sucursal (pendientes de confirmar, de asignar…)",
            description = "SYS_ADMIN ve todos y puede filtrar por sucursal; BRANCH_ADMIN y CASHIER, solo la suya")
    public Page<AlquilerResponse> listar(@RequestParam(required = false) EstadoAlquiler estado,
            @RequestParam(required = false) UUID sucursalId,
            @PageableDefault(size = 20, sort = "fechaSalida") Pageable pagina,
            @AuthenticationPrincipal UsuarioActual actual) {
        return servicio.listar(estado, sucursalId, pagina, actual);
    }

    @GetMapping("/reporte-ingresos")
    @Operation(summary = "Reporte de ingresos por alquiler de buses, por fecha de pago",
            description = "BRANCH_ADMIN (su sucursal) y SYS_ADMIN (todas o una). Sin intervalo, todos los registros")
    public ReporteIngresosResponse reporteIngresos(@RequestParam(required = false) LocalDate desde,
            @RequestParam(required = false) LocalDate hasta, @RequestParam(required = false) UUID sucursalId,
            @AuthenticationPrincipal UsuarioActual actual) {
        return servicio.reporteIngresos(desde, hasta, sucursalId, actual);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de un alquiler", description = "Lo ve su cliente y el personal de su sucursal")
    public AlquilerResponse obtener(@PathVariable UUID id, @AuthenticationPrincipal UsuarioActual actual) {
        return servicio.obtener(id, actual);
    }

    @PostMapping("/{id}/confirmacion")
    @Operation(summary = "Confirma el precio (el estimado o el que se indique) y avisa al cliente",
            description = "BRANCH_ADMIN de la sucursal o SYS_ADMIN. 409 si no está SOLICITADO")
    public AlquilerResponse confirmar(@PathVariable UUID id,
            @RequestBody(required = false) @Valid ConfirmacionRequest solicitud,
            @AuthenticationPrincipal UsuarioActual actual) {
        return servicio.confirmar(id, solicitud == null ? null : solicitud.precioFinal(), actual);
    }

    @PostMapping("/{id}/rechazo")
    @Operation(summary = "Rechaza la solicitud con un motivo",
            description = "BRANCH_ADMIN de la sucursal o SYS_ADMIN. 409 si no está SOLICITADO")
    public AlquilerResponse rechazar(@PathVariable UUID id, @RequestBody @Valid RechazoRequest solicitud,
            @AuthenticationPrincipal UsuarioActual actual) {
        return servicio.rechazar(id, solicitud.motivo(), actual);
    }

    @PostMapping("/{id}/cancelacion")
    @Operation(summary = "Cancela un alquiler que todavía no se paga",
            description = "Su cliente o el personal de la sucursal. 409 si ya está pagado: se cancela eliminando "
                    + "su viaje")
    public AlquilerResponse cancelar(@PathVariable UUID id,
            @RequestBody(required = false) @Valid CancelacionRequest solicitud,
            @AuthenticationPrincipal UsuarioActual actual) {
        return servicio.cancelar(id, solicitud == null ? null : solicitud.motivo(), actual);
    }

    @PostMapping("/{id}/pago")
    @Operation(summary = "Paga el alquiler confirmado con la cartera",
            description = "Solo su cliente. 422 sin saldo; 409 si no está CONFIRMADO o ya se pagó")
    public AlquilerResponse pagar(@PathVariable UUID id, @RequestBody(required = false) @Valid PagoRequest solicitud,
            @AuthenticationPrincipal UsuarioActual actual) {
        return servicio.pagar(id, solicitud == null ? null : solicitud.fechaPago(), actual);
    }

    @PostMapping("/{id}/pago-mostrador")
    @Operation(summary = "Registra el pago en efectivo en el mostrador",
            description = "BRANCH_ADMIN y CASHIER de la sucursal. 409 si no está CONFIRMADO o ya se pagó")
    public AlquilerResponse pagarEnMostrador(@PathVariable UUID id,
            @RequestBody(required = false) @Valid PagoRequest solicitud,
            @AuthenticationPrincipal UsuarioActual actual) {
        return servicio.pagarEnMostrador(id, solicitud == null ? null : solicitud.fechaPago(), actual);
    }

    private static ResponseEntity<AlquilerResponse> creado(AlquilerResponse alquiler) {
        return ResponseEntity.created(URI.create("/api/alquileres/" + alquiler.id())).body(alquiler);
    }
}
