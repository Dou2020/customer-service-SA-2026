package gt.cunoc.sa.customer.application;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * alquiler.* de application.yml.
 *
 * @param factorCarretera km por carretera por cada km en línea recta (Distancia)
 */
@ConfigurationProperties("alquiler")
@Validated
public record PropiedadesAlquiler(@NotNull @DecimalMin("1.0") BigDecimal factorCarretera) {
}
