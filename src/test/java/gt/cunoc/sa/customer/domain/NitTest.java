package gt.cunoc.sa.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
            "576937-k,     576937K",    // termina en K (el verificador 10 de SAT)
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

    /** El dígito verificador (módulo 11) ya no se valida: con él, ninguno de estos pasaba. */
    @ParameterizedTest
    @CsvSource({
            "12345678,  12345678",
            "576937-9,  5769379",
            "6-0,       60",
            "103030092, 103030092"})
    void aceptaCualquierDigitoFinal(String entrada, String esperado) {
        assertThat(Nit.normalizar(entrada)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @ValueSource(strings = {"12A45", "K1", "1234567890123456", "12.345"})
    void rechazaUnFormatoInvalido(String entrada) {
        assertThatThrownBy(() -> Nit.normalizar(entrada))
                .isInstanceOf(NitInvalidoException.class)
                .hasMessage("El NIT solo puede tener dígitos y terminar en un dígito o K");
    }
}
