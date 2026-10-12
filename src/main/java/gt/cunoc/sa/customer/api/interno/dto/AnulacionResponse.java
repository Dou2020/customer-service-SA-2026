package gt.cunoc.sa.customer.api.interno.dto;

/**
 * Lo que el reconciliador de sales hace con la venta: APLICADO → la confirma (el débito había
 * entrado); ANULADO → libera los asientos (el débito ya no podrá entrar).
 */
public record AnulacionResponse(ResultadoDebito resultado) {
}
