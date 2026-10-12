package gt.cunoc.sa.customer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * La misma configuración en todas las pruebas de integración: Spring reutiliza el contexto (y
 * los contenedores) entre clases solo si coincide exactamente.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContenedoresDePrueba.class)
public @interface PruebaDeIntegracion {
}
