package gt.cunoc.sa.customer.api.historial;

import java.time.LocalDate;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import gt.cunoc.sa.customer.api.historial.dto.HistorialResponse;
import gt.cunoc.sa.customer.infrastructure.security.UsuarioActual;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/historial")
@Tag(name = "Historial", description = "Boletos y alquileres de la cuenta, incluidos los de mostrador con su NIT")
public class HistorialController {

    private final HistorialService servicio;

    public HistorialController(HistorialService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @Operation(summary = "Historial de boletos y alquileres de la cuenta",
            description = "Filtros opcionales desde y hasta (fecha de pago de los boletos, de solicitud de los "
                    + "alquileres). 409 si la cuenta se acaba de confirmar y customer todavía no recibe su perfil")
    public HistorialResponse historial(@RequestParam(required = false) LocalDate desde,
            @RequestParam(required = false) LocalDate hasta, @AuthenticationPrincipal UsuarioActual actual) {
        return servicio.historial(actual, desde, hasta);
    }
}
