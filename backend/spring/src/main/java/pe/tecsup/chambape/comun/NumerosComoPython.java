package pe.tecsup.chambape.comun;

import java.math.BigInteger;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Lee números de la URL igual que los campos de Django REST Framework, para que
 * Spring acepte y rechace exactamente los mismos valores que la API de Django.
 */
public final class NumerosComoPython {

    // IntegerField quita un ".0", ".00"... final antes de convertir. Su \s es el de Python:
    // también U+001C a U+001F, U+0085, los Zs, U+2028 y U+2029.
    private static final Pattern DECIMAL_CERO_FINAL =
        Pattern.compile("\\.0*[\\s\\x1c-\\x1f\\x85\\p{Zs}\\u2028\\u2029]*$");
    private static final Pattern ENTERO = Pattern.compile("[+-]?\\d+(_\\d+)*");
    private static final Pattern DIGITOS = Pattern.compile("\\d+(_\\d+)*");
    private static final Pattern REAL = Pattern.compile(
        "[+-]?(" + DIGITOS + "(\\." + "(" + DIGITOS + ")?)?|\\." + DIGITOS + ")([eE][+-]?" + DIGITOS + ")?");
    private static final Pattern ESPECIAL = Pattern.compile("[+-]?(inf|infinity|nan)", Pattern.CASE_INSENSITIVE);

    private NumerosComoPython() {
    }

    /**
     * Quita los espacios de los extremos como {@code int()} y {@code float()} de Python: los ASCII
     * (espacio, \t, \n, \v, \f, \r) y los Unicode (Zs, U+0085, U+2028, U+2029). U+001C a U+001F no
     * cuentan, aunque {@code strip()} de Java sí los quitaría.
     */
    static String quitarEspacios(String texto) {
        int inicio = 0;
        int fin = texto.length();
        while (inicio < fin && esEspacioPython(texto.charAt(inicio))) {
            inicio++;
        }
        while (fin > inicio && esEspacioPython(texto.charAt(fin - 1))) {
            fin--;
        }
        return texto.substring(inicio, fin);
    }

    private static boolean esEspacioPython(char c) {
        return c == ' ' || (c >= 0x09 && c <= 0x0d)
            || (c > 127 && (Character.getType(c) == Character.SPACE_SEPARATOR
                || c == 0x85 || c == 0x2028 || c == 0x2029));
    }

    /** {@code int()} y {@code float()} de Python aceptan dígitos de cualquier escritura («١٢» es 12). */
    static String digitosAscii(String texto) {
        StringBuilder salida = new StringBuilder(texto.length());
        texto.codePoints().forEach(c -> salida.appendCodePoint(
            c > 127 && Character.getType(c) == Character.DECIMAL_DIGIT_NUMBER ? '0' + Character.digit(c, 10) : c));
        return salida.toString();
    }

    /** Como {@code int()} de Python sobre el texto; {@code null} si no es un entero válido. */
    public static BigInteger entero(String texto) {
        String limpio = digitosAscii(quitarEspacios(DECIMAL_CERO_FINAL.matcher(texto).replaceFirst("")));
        if (!ENTERO.matcher(limpio).matches()) {
            return null;
        }
        return new BigInteger(limpio.replace("_", "").replace("+", ""));
    }

    /** Como {@code float()} de Python sobre el texto; {@code null} si no es un número válido. */
    public static Double real(String texto) {
        String limpio = digitosAscii(quitarEspacios(texto));
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
