package gt.cunoc.sa.customer.api.interno;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import gt.cunoc.sa.customer.api.interno.DebitosService.Debito;
import gt.cunoc.sa.customer.api.interno.dto.AnulacionResponse;
import gt.cunoc.sa.customer.api.interno.dto.ClienteInternoResponse;
import gt.cunoc.sa.customer.api.interno.dto.DebitoRequest;
import gt.cunoc.sa.customer.api.interno.dto.DebitoResponse;
import gt.cunoc.sa.customer.api.interno.dto.ResolucionClienteRequest;
import gt.cunoc.sa.customer.infrastructure.messaging.ReprocesadorDlq;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Rutas para sales y para el operador. El gateway no las expone: solo se llega por la red interna
 * de Compose (README, "Premisa: solo el gateway llega a los servicios").
 */
@RestController
@RequestMapping("/internal")
@SecurityRequirements
@Tag(name = "Interno", description = "Solo red interna; el gateway no expone /internal/**")
public class InternoController {

    private final ReprocesadorDlq reprocesador;
    private final ClientesInternosService clientes;
    private final DebitosService debitos;

    public InternoController(ReprocesadorDlq reprocesador, ClientesInternosService clientes, DebitosService debitos) {
        this.reprocesador = reprocesador;
        this.clientes = clientes;
        this.debitos = debitos;
    }

    @PostMapping("/clientes/resolucion")
    @Operation(summary = "S5: el cliente de una venta en mostrador, por NIT",
            description = "Sin NIT o con CF, el genérico. Si el NIT no existe lo crea. 422 si el NIT no es válido")
    public ClienteInternoResponse resolverPorNit(@RequestBody @Valid ResolucionClienteRequest solicitud) {
        return clientes.porNit(solicitud.nit(), solicitud.nombre());
    }

    @GetMapping("/clientes/por-usuario/{usuarioId}")
    @Operation(summary = "S5: el cliente de una cuenta, para la compra en línea",
            description = "409 mientras customer no aplique el PerfilGuardado de una cuenta recién confirmada")
    public ClienteInternoResponse porUsuario(@PathVariable UUID usuarioId) {
        return clientes.porUsuario(usuarioId);
    }

    @PostMapping("/carteras/{clienteId}/debitos")
    @Operation(summary = "S4: debita la cartera para pagar una compra de boletos en línea",
            description = "Idempotency-Key = ventaId. 201 al aplicarlo; 200 si ya se había aplicado con esa clave; "
                    + "422 sin saldo; 409 si el reconciliador lo anuló o la clave es de otra operación; 404 sin cartera")
    public ResponseEntity<DebitoResponse> debitar(@PathVariable UUID clienteId,
            @RequestHeader(name = "Idempotency-Key", required = false) String clave,
            @RequestBody @Valid DebitoRequest solicitud) {
        Debito debito = debitos.debitar(clienteId, clave, solicitud);
        return ResponseEntity.status(debito.nuevo() ? HttpStatus.CREATED : HttpStatus.OK).body(debito.respuesta());
    }

    @PostMapping("/carteras/{clienteId}/debitos/{clave}/anulacion")
    @Operation(summary = "S4: el reconciliador resuelve un débito incierto",
            description = "Inserta la lápida DEBITO_ANULADO: ANULADO si el débito no había entrado (y ya no podrá), "
                    + "APLICADO si sí. Idempotente")
    public AnulacionResponse anular(@PathVariable UUID clienteId, @PathVariable String clave) {
        return debitos.anular(clienteId, clave);
    }

    @PostMapping("/dlq/reprocesar")
    @Operation(summary = "Reencola los mensajes de customer.events.dlq")
    public Map<String, Integer> reprocesarDlq() {
        return Map.of("reprocesados", reprocesador.reprocesar());
    }
}
