package gt.cunoc.sa.customer.infrastructure.security;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Convierte los encabezados de identidad del gateway en el principal de Spring Security.
 *
 * No vuelve a validar el JWT: eso obligaría a repartir el secreto y el acceso a Redis
 * entre los siete servicios, que es justo lo que el gateway centraliza. Confiar en los
 * encabezados es seguro porque el gateway borra los que manda el cliente y, en
 * producción, nadie más llega a este puerto.
 *
 * No es un @Component a propósito: Spring Boot lo registraría además como filtro de
 * servlet y correría dos veces. Lo instancia SeguridadConfig dentro de la cadena.
 */
public class EncabezadosGatewayFilter extends OncePerRequestFilter {

    public static final String USUARIO = "X-User-Id";
    public static final String ROLES = "X-User-Roles";
    public static final String SUCURSAL = "X-Sucursal-Id";
    public static final String CHOFER = "X-Chofer-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String usuarioId = request.getHeader(USUARIO);
        if (usuarioId != null) {
            try {
                UsuarioActual usuario = new UsuarioActual(
                        UUID.fromString(usuarioId),
                        uuidOpcional(request.getHeader(SUCURSAL)),
                        textoOpcional(request.getHeader(CHOFER)),
                        roles(request.getHeader(ROLES)));
                List<SimpleGrantedAuthority> autoridades = usuario.roles().stream()
                        .map(rol -> new SimpleGrantedAuthority("ROLE_" + rol))
                        .toList();
                SecurityContextHolder.getContext().setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(usuario, null, autoridades));
            } catch (IllegalArgumentException e) {
                // Un usuario o una sucursal que no es UUID no lo escribe el gateway: se trata
                // como anónimo y la cadena responde 401.
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }

    private static UUID uuidOpcional(String valor) {
        return valor == null || valor.isBlank() ? null : UUID.fromString(valor);
    }

    /**
     * El chofer es un id de fleet ("3"), no un UUID: si se leyera como UUID, todo request de un
     * DRIVER saldría 401 y el frontend cerraría su sesión.
     */
    private static String textoOpcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static Set<String> roles(String valor) {
        if (valor == null) {
            return Set.of();
        }
        return Arrays.stream(valor.split(","))
                .map(String::trim)
                .filter(rol -> !rol.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
