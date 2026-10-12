package gt.cunoc.sa.customer.api.alquiler.dto;

import java.math.BigDecimal;

/**
 * Cómo se llegó al precio estimado, para mostrarlo en el formulario antes de enviar la solicitud.
 *
 * @param distanciaKm línea recta por el factor de carretera
 * @param dias        del de salida al de retorno, ambos incluidos; 1 si es solo de ida
 */
public record EstimacionResponse(
        BigDecimal distanciaKm,
        boolean idaYVuelta,
        int dias,
        BigDecimal tarifaKm,
        BigDecimal cargoDia,
        BigDecimal precioEstimado) {
}
