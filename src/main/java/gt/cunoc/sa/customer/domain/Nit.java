package gt.cunoc.sa.customer.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalización y validación del NIT guatemalteco. customer-service aplica la misma regla
 * (código copiado, como el de mensajería): si las dos difirieran, un NIT que identity acepta
 * podría no encontrar su cliente.
 *
 * Solo se valida el formato. El dígito verificador (módulo 11) se dejó de validar el 2026-10-13:
 * rechazaba NIT que se necesitaban registrar. Lo que se pierde es detectar un error de tecleo, que
 * ahora crea una cuenta o un cliente con un NIT que nadie más podría enlazar.
 */
public final class Nit {

    /** Consumidor final: varias cuentas pueden tenerlo (perfil_nit_uq lo excluye). */
    public static final String CONSUMIDOR_FINAL = "CF";

    // Dígitos y un último carácter que puede ser K (el verificador 10 de SAT); varchar(15) en la base.
    private static final Pattern FORMATO = Pattern.compile("\\d{1,14}[\\dK]");
    private static final Pattern SEPARADORES = Pattern.compile("[\\s-]");

    private Nit() {
    }

    /**
     * Mayúsculas y sin guiones ni espacios. Vacío, null o "CF" → "CF".
     *
     * @throws NitInvalidoException si no tiene formato de NIT
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
        return nit;
    }

    public static boolean esConsumidorFinal(String nitNormalizado) {
        return CONSUMIDOR_FINAL.equals(nitNormalizado);
    }
}
