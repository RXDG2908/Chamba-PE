package pe.tecsup.chambape.comun;

import java.math.BigInteger;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Lee números de la URL igual que los campos de Django REST Framework, para que
 * Spring acepte y rechace exactamente los mismos valores que la API de Django.
 */
public final class NumerosComoPython {

    // IntegerField quita un ".0", ".00"... final antes de convertir.
    private static final Pattern DECIMAL_CERO_FINAL = Pattern.compile("\\.0*\\s*$");
    private static final Pattern ENTERO = Pattern.compile("[+-]?\\d+(_\\d+)*");
    private static final Pattern DIGITOS = Pattern.compile("\\d+(_\\d+)*");
    private static final Pattern REAL = Pattern.compile(
        "[+-]?(" + DIGITOS + "(\\." + "(" + DIGITOS + ")?)?|\\." + DIGITOS + ")([eE][+-]?" + DIGITOS + ")?");
    private static final Pattern ESPECIAL = Pattern.compile("[+-]?(inf|infinity|nan)", Pattern.CASE_INSENSITIVE);

    private NumerosComoPython() {
    }

    /** Como {@code int()} de Python sobre el texto; {@code null} si no es un entero válido. */
    public static BigInteger entero(String texto) {
        String limpio = DECIMAL_CERO_FINAL.matcher(texto).replaceFirst("").strip();
        if (!ENTERO.matcher(limpio).matches()) {
            return null;
        }
        return new BigInteger(limpio.replace("_", "").replace("+", ""));
    }

    /** Como {@code float()} de Python sobre el texto; {@code null} si no es un número válido. */
    public static Double real(String texto) {
        String limpio = texto.strip();
        if (ESPECIAL.matcher(limpio).matches()) {
            boolean negativo = limpio.startsWith("-");
            String nombre = limpio.replaceFirst("^[+-]", "").toLowerCase(Locale.ROOT);
            if (nombre.equals("nan")) {
                return Double.NaN;
            }
            return negativo ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        }
        if (!REAL.matcher(limpio).matches()) {
            return null;
        }
        return Double.parseDouble(limpio.replace("_", ""));
    }
}
