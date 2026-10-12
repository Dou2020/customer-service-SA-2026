package gt.cunoc.sa.customer.infrastructure.web;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Propaga el X-Correlation-Id que pone el gateway (o genera uno si no viene) y lo deja
 * en el MDC: aparece en cada línea de log y la outbox lo copia al sobre del evento.
 * Corre antes que Spring Security para que también los 401 y 403 lo lleven.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String ENCABEZADO = "X-Correlation-Id";
    public static final String MDC_CLAVE = "correlationId";

    // Lo que llega del cliente termina en los logs: solo se acepta un valor corto y
    // sin caracteres de control, para que no inyecte líneas falsas.
    private static final Pattern VALIDO = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String recibido = request.getHeader(ENCABEZADO);
        String correlationId = recibido != null && VALIDO.matcher(recibido).matches()
                ? recibido
                : UUID.randomUUID().toString();
        MDC.put(MDC_CLAVE, correlationId);
        response.setHeader(ENCABEZADO, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_CLAVE);
        }
    }
}
