package gt.cunoc.sa.customer.api.cartera;

import java.net.URI;
import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import gt.cunoc.sa.customer.api.cartera.CarteraService.Recarga;
import gt.cunoc.sa.customer.api.cartera.dto.CarteraResponse;
import gt.cunoc.sa.customer.api.cartera.dto.MovimientoResponse;
import gt.cunoc.sa.customer.api.cartera.dto.RecargaRequest;
import gt.cunoc.sa.customer.infrastructure.security.UsuarioActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/cartera")
@Tag(name = "Cartera", description = "Cartera digital de la cuenta: saldo, movimientos y recargas")
public class CarteraController {

    private final CarteraService servicio;

    public CarteraController(CarteraService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @Operation(summary = "Saldo de la cartera propia",
            description = "409 si la cuenta se acaba de confirmar y customer todavía no recibe su perfil")
    public CarteraResponse consultar(@AuthenticationPrincipal UsuarioActual actual) {
        return servicio.consultar(actual);
    }

    @GetMapping("/movimientos")
    @Operation(summary = "Movimientos de la cartera propia, del más reciente al más antiguo",
            description = "Filtros opcionales desde y hasta sobre la fecha elegida al operar")
    public Page<MovimientoResponse> movimientos(@RequestParam(required = false) LocalDate desde,
            @RequestParam(required = false) LocalDate hasta,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pagina,
            @AuthenticationPrincipal UsuarioActual actual) {
        return servicio.movimientos(actual, desde, hasta, pagina);
    }

    @PostMapping("/recargas")
    @Operation(summary = "Recarga la cartera propia con el monto y la fecha indicados",
            description = "Exige el encabezado Idempotency-Key (400 sin él): repetir la misma clave devuelve la "
                    + "recarga original con 200 y no acredita dos veces; 409 si la clave ya se usó en otra operación")
    public ResponseEntity<MovimientoResponse> recargar(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody @Valid RecargaRequest solicitud, @AuthenticationPrincipal UsuarioActual actual) {
        Recarga recarga = servicio.recargar(actual, idempotencyKey, solicitud);
        return recarga.nueva()
                ? ResponseEntity.created(URI.create("/api/cartera/movimientos")).body(recarga.movimiento())
                : ResponseEntity.ok(recarga.movimiento());
    }
}
