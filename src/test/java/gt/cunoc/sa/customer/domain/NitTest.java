package gt.cunoc.sa.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class NitTest {

    @ParameterizedTest
    @CsvSource({
            "12345679,     12345679",
            "1234567-9,    12345679",
            "' 1234 567-9 ', 12345679",
            "576937-k,     576937K",    // verificador K (10)
            "6K,           6K"})
    void normalizaYAceptaUnNitValido(String entrada, String esperado) {
        assertThat(Nit.normalizar(entrada)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"CF", "cf", " c-f ", "   "})
    void vacioOCfEsConsumidorFinal(String entrada) {
        assertThat(Nit.normalizar(entrada)).isEqualTo(Nit.CONSUMIDOR_FINAL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678", "576937-9", "6-0"})
    void rechazaUnDigitoVerificadorEquivocado(String entrada) {
        assertThatThrownBy(() -> Nit.normalizar(entrada))
                .isInstanceOf(NitInvalidoException.class)
                .hasMessageContaining("dígito verificador");
    }

    @ParameterizedTest
    @ValueSource(strings = {"12A45", "K1", "1234567890123456", "12.345"})
    void rechazaUnFormatoInvalido(String entrada) {
        assertThatThrownBy(() -> Nit.normalizar(entrada)).isInstanceOf(NitInvalidoException.class);
    }

    @Test
    void calculaElDigitoVerificadorModulo11() {
        // 1·8 + 2·7 + 3·6 + 4·5 + 5·4 + 6·3 + 7·2 = 112; 112 mod 11 = 2; 11 − 2 = 9
        assertThat(Nit.digitoVerificador("1234567")).isEqualTo('9');
        // 5·7 + 7·6 + 6·5 + 9·4 + 3·3 + 7·2 = 166; 166 mod 11 = 1; 11 − 1 = 10 → K
        assertThat(Nit.digitoVerificador("576937")).isEqualTo('K');
    }
}
