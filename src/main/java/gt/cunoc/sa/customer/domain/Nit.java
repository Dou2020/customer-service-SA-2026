package gt.cunoc.sa.customer.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalización y validación del NIT guatemalteco. customer-service aplica la misma regla
 * (código copiado, como el de mensajería): si las dos difirieran, un NIT que identity acepta
 * podría no encontrar su cliente.
 *
 * Se valida el dígito verificador porque un error de tecleo crearía una cuenta o un cliente
 * con un NIT que nadie más podría enlazar.
 */
public final class Nit {

    /** Consumidor final: varias cuentas pueden tenerlo (perfil_nit_uq lo excluye). */
    public static final String CONSUMIDOR_FINAL = "CF";

    // Cuerpo numérico y dígito verificador (0-9 o K); varchar(15) en la base.
    private static final Pattern FORMATO = Pattern.compile("\\d{1,14}[\\dK]");
    private static final Pattern SEPARADORES = Pattern.compile("[\\s-]");

    private Nit() {
    }

    /**
     * Mayúsculas y sin guiones ni espacios. Vacío, null o "CF" → "CF".
     *
     * @throws NitInvalidoException si no tiene formato de NIT o el dígito verificador no cuadra
     */
    public static String normalizar(String valor) {
        if (valor == null) {
            return CONSUMIDOR_FINAL;
        }
        String nit = SEPARADORES.matcher(valor).replaceAll("").toUpperCase(Locale.ROOT);
        if (nit.isEmpty() || nit.equals(CONSUMIDOR_FINAL)) {
            return CONSUMIDOR_FINAL;
        }
        if (!FORMATO.matcher(nit).matches()) {
            throw new NitInvalidoException("El NIT solo puede tener dígitos y terminar en un dígito o K");
        }
        if (nit.charAt(nit.length() - 1) != digitoVerificador(nit.substring(0, nit.length() - 1))) {
            throw new NitInvalidoException("El NIT no es válido: revisa el dígito verificador");
        }
        return nit;
    }

    public static boolean esConsumidorFinal(String nitNormalizado) {
        return CONSUMIDOR_FINAL.equals(nitNormalizado);
    }

    /**
     * Módulo 11: cada dígito del cuerpo se multiplica por su posición contada desde la derecha
     * empezando en 2; el verificador es (11 − suma mod 11) mod 11, y 10 se escribe K.
     */
    public static char digitoVerificador(String cuerpo) {
        int suma = 0;
        for (int i = 0; i < cuerpo.length(); i++) {
            int factor = cuerpo.length() - i + 1;
            suma += (cuerpo.charAt(i) - '0') * factor;
        }
        int resto = (11 - suma % 11) % 11;
        return resto == 10 ? 'K' : (char) ('0' + resto);
    }
}
