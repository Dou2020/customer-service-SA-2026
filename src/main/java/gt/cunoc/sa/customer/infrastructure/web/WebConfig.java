package gt.cunoc.sa.customer.infrastructure.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;

/**
 * Las páginas salen como {content, page: {size, number, totalElements, totalPages}}: un formato
 * estable para el frontend. Serializar PageImpl directamente expone detalles internos que
 * cambian entre versiones de Spring Data, y Spring lo advierte en el log.
 */
@Configuration
@EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)
public class WebConfig {
}
