package gt.cunoc.sa.customer.infrastructure.security;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.firewall.HttpStatusRequestRejectedHandler;
import org.springframework.security.web.firewall.RequestRejectedHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;

/**
 * El gateway autentica; este servicio autoriza. Aquí solo se exige que la request
 * traiga identidad: el rol va con @PreAuthorize en cada endpoint y el alcance por
 * sucursal en application/.
 */
@Configuration
@EnableMethodSecurity
public class SeguridadConfig {

    private final ObjectMapper objectMapper;

    public SeguridadConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    SecurityFilterChain filtros(HttpSecurity http) throws Exception {
        return http
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new EncabezadosGatewayFilter(), AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(a -> a
                        // El despacho a /error no lleva los encabezados ya procesados: sin
                        // esto, un 404 o 500 de un usuario autenticado saldría como 401.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        // /internal/** es permitAll porque el gateway no lo expone y en
                        // producción los servicios solo son alcanzables en la red interna.
                        .requestMatchers("/internal/**", "/v3/api-docs/**", "/swagger-ui/**",
                                "/swagger-ui.html", "/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())          // cerrado por defecto
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, ex) ->
                                problema(response, HttpStatus.UNAUTHORIZED, "Inicia sesión para continuar"))
                        .accessDeniedHandler((request, response, ex) ->
                                problema(response, HttpStatus.FORBIDDEN, "No tienes permiso para esta acción")))
                .build();
    }

    /**
     * El firewall de Spring Security rechaza URLs y encabezados maliciosos (/../, %00…)
     * lanzando una excepción que, sin esto, termina como 500 y un ERROR en el log.
     */
    @Bean
    RequestRejectedHandler solicitudRechazada() {
        return new HttpStatusRequestRejectedHandler();   // 400
    }

    private void problema(HttpServletResponse response, HttpStatus status, String detalle) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ProblemDetail.forStatusAndDetail(status, detalle));
    }
}
