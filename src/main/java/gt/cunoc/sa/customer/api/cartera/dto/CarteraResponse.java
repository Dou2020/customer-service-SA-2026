package gt.cunoc.sa.customer.api.cartera.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import gt.cunoc.sa.customer.domain.CarteraEntity;

public record CarteraResponse(
        UUID clienteId,
        BigDecimal saldo,
        Instant actualizadoEn) {

    public static CarteraResponse de(CarteraEntity c) {
        return new CarteraResponse(c.getClienteId(), c.getSaldo(), c.getActualizadoEn());
    }
}
