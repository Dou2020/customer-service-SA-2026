package gt.cunoc.sa.customer.api.cliente;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import gt.cunoc.sa.customer.api.cliente.dto.ClienteResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/clientes")
@Tag(name = "Clientes", description = "Clientes por NIT, con o sin cuenta")
public class ClienteController {

    private final ClienteService servicio;

    public ClienteController(ClienteService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @Operation(summary = "Busca clientes por NIT o por nombre (para el mostrador)",
            description = "SYS_ADMIN, BRANCH_ADMIN y CASHIER. 422 si el NIT no es válido")
    public Page<ClienteResponse> buscar(@RequestParam(required = false) String nit,
            @RequestParam(required = false) String nombre,
            @PageableDefault(size = 20, sort = "nombre") Pageable pagina) {
        return servicio.buscar(nit, nombre, pagina);
    }
}
