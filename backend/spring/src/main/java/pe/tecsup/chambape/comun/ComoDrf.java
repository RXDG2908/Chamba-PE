package pe.tecsup.chambape.comun;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.Set;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;

/**
 * Conversión de valores de entrada como los campos de Django REST Framework (DecimalField,
 * CharField, BooleanField y PrimaryKeyRelatedField), con los mismos mensajes. Los valores
 * llegan como JSON (números, textos, booleanos) o como texto de un formulario.
 */
public final class ComoDrf {

    public static final String REQUERIDO = "This field is required.";
    public static final String NULO = "This field may not be null.";

    private static final Pattern DECIMAL = Pattern.compile(
        "[+-]?(\\d+(_\\d+)*(\\.(\\d+(_\\d+)*)?)?|\\.\\d+(_\\d+)*)([eE][+-]?\\d+(_\\d+)*)?");
    private static final Pattern ESPECIAL = Pattern.compile("[+-]?(inf|infinity|s?nan\\d*)", Pattern.CASE_INSENSITIVE);

    private ComoDrf() {
    }

    /** Error de un campo: el mensaje de DRF. */
    public static final class Invalido extends Exception {
        public Invalido(String mensaje) {
            super(mensaje, null, false, false);
        }
    }

    /** Como {@code str(valor)} de Python para un valor JSON (o el texto de un formulario). */
    public static String comoTextoPython(JsonNode valor) {
        if (valor.isString()) {
            return valor.asString();
        }
        if (valor.isBoolean()) {
            return valor.booleanValue() ? "True" : "False";
        }
        if (valor.isIntegralNumber()) {
            return valor.bigIntegerValue().toString();
        }
        if (valor.isNumber()) {
            return PythonRepr.flotante(valor.doubleValue());
        }
        // Listas y objetos: DRF los rechaza antes de convertirlos a texto.
        return valor.toString();
    }

    /** DecimalField(max_digits, decimal_places): valor ya redondeado a {@code decimales}. */
    public static BigDecimal decimal(JsonNode valor, int maxDigitos, int decimales) throws Invalido {
        BigDecimal numero;
        if (valor.isNumber() && !valor.isIntegralNumber()) {
            // Python: Decimal(str(float)): el float con su representación más corta.
            numero = PythonRepr.decimalDeFlotante(valor.doubleValue());
        } else {
            // DRF hace str(valor).strip(): ese strip también quita U+001C a U+001F.
            String texto = stripPython(comoTextoPython(valor));
            if (texto.codePointCount(0, texto.length()) > 1000) {
                throw new Invalido("String value too large.");
            }
            String limpio = NumerosComoPython.digitosAscii(texto);
            if (ESPECIAL.matcher(limpio).matches() || !DECIMAL.matcher(limpio).matches()) {
                throw new Invalido("A valid number is required.");
            }
            numero = new BigDecimal(limpio.replace("_", ""));
        }
        validarPrecision(numero, maxDigitos, decimales);
        return numero.setScale(decimales, RoundingMode.HALF_EVEN);
    }

    // DecimalField.validate_precision de DRF, con los dígitos y el exponente de Decimal de Python.
    private static void validarPrecision(BigDecimal numero, int maxDigitos, int decimales) throws Invalido {
        String digitos = numero.unscaledValue().abs().toString();
        int exponente = -numero.scale();
        int total;
        int enteros;
        int lugares;
        if (exponente >= 0) {
            total = digitos.length() + exponente;
            enteros = total;
            lugares = 0;
        } else if (digitos.length() > -exponente) {
            total = digitos.length();
            enteros = total + exponente;
            lugares = -exponente;
        } else {
            total = -exponente;
            enteros = 0;
            lugares = total;
        }
        if (total > maxDigitos) {
            throw new Invalido("Ensure that there are no more than " + maxDigitos + " digits in total.");
        }
        if (lugares > decimales) {
            throw new Invalido("Ensure that there are no more than " + decimales + " decimal places.");
        }
        if (enteros > maxDigitos - decimales) {
            throw new Invalido("Ensure that there are no more than " + (maxDigitos - decimales)
                + " digits before the decimal point.");
        }
    }

    /** CharField(trim_whitespace=False): acepta texto y números; rechaza booleanos, listas y objetos. */
    public static String texto(JsonNode valor, Integer maxLargo, String mensajeVacio) throws Invalido {
        if (valor.isBoolean() || !(valor.isString() || valor.isNumber())) {
            throw new Invalido("Not a valid string.");
        }
        String texto = comoTextoPython(valor);
        if (texto.isEmpty()) {
            throw new Invalido(mensajeVacio != null ? mensajeVacio : "This field may not be blank.");
        }
        if (maxLargo != null && texto.codePointCount(0, texto.length()) > maxLargo) {
            throw new Invalido("Ensure this field has no more than " + maxLargo + " characters.");
        }
        return texto;
    }

    /**
     * PrimaryKeyRelatedField: devuelve el id a buscar, o lanza el error de tipo. Si el id no existe,
     * quien llama responde {@link #noExiste(JsonNode)}.
     */
    public static long clavePrimaria(JsonNode valor) throws Invalido {
        // Django convierte con int(): admite "5", " 5 ", 5 y 5.7 (→ 5); los booleanos no.
        if (valor.isBoolean() || valor.isArray() || valor.isObject()) {
            throw new Invalido("Incorrect type. Expected pk value, received " + tipoPython(valor) + ".");
        }
        BigInteger entero;
        if (valor.isIntegralNumber()) {
            entero = valor.bigIntegerValue();
        } else if (valor.isNumber()) {
            double numero = valor.doubleValue();
            if (Double.isNaN(numero) || Double.isInfinite(numero)) {
                throw new Invalido("Incorrect type. Expected pk value, received float.");
            }
            entero = new BigDecimal(numero).toBigInteger();
        } else {
            entero = NumerosComoPython.entero(valor.asString());
            // int() de Python no acepta "5.0" (IntegerField de DRF sí, pero aquí no se usa).
            if (entero == null || valor.asString().contains(".")) {
                throw new Invalido("Incorrect type. Expected pk value, received str.");
            }
        }
        if (entero.bitLength() > 63) {
            // Ningún id llega a tanto: se responde como «no existe».
            return -1;
        }
        return entero.longValueExact();
    }

    public static String noExiste(JsonNode valor) {
        // DRF muestra el valor recibido tal como lo imprime Python.
        return "Invalid pk \"" + comoTextoPython(valor) + "\" - object does not exist.";
    }

    private static final Set<String> VERDADEROS = Set.of("t", "T", "y", "Y", "yes", "Yes", "YES", "true", "True",
        "TRUE", "on", "On", "ON", "1");
    private static final Set<String> FALSOS = Set.of("f", "F", "n", "N", "no", "No", "NO", "false", "False",
        "FALSE", "off", "Off", "OFF", "0");

    /** BooleanField de DRF. */
    public static boolean booleano(JsonNode valor) throws Invalido {
        if (valor.isBoolean()) {
            return valor.booleanValue();
        }
        if (valor.isNumber()) {
            BigDecimal numero = valor.isIntegralNumber() ? new BigDecimal(valor.bigIntegerValue())
                : Double.isFinite(valor.doubleValue()) ? BigDecimal.valueOf(valor.doubleValue()) : null;
            if (numero != null && numero.compareTo(BigDecimal.ONE) == 0) {
                return true;
            }
            if (numero != null && numero.signum() == 0) {
                return false;
            }
        } else if (valor.isString()) {
            if (VERDADEROS.contains(valor.asString())) {
                return true;
            }
            if (FALSOS.contains(valor.asString())) {
                return false;
            }
        }
        throw new Invalido("Must be a valid boolean.");
    }

    /** {@code unicodedata.normalize('NFC', texto)}. */
    public static String nfc(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFC);
    }

    /** {@code str.isalpha()} de un carácter. */
    public static boolean esLetra(int caracter) {
        return Character.isLetter(caracter);
    }

    /** {@code texto.strip()} de Python. */
    public static String stripPython(String texto) {
        int inicio = 0;
        int fin = texto.length();
        while (inicio < fin && esEspacio(texto.charAt(inicio))) {
            inicio++;
        }
        while (fin > inicio && esEspacio(texto.charAt(fin - 1))) {
            fin--;
        }
        return texto.substring(inicio, fin);
    }

    private static boolean esEspacio(int c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c) || c == 0x85 || (c >= 0x1c && c <= 0x1f);
    }

    /** {@code texto.strip()} de Python vacío: solo espacios (incluidos U+001C a U+001F). */
    public static boolean soloEspacios(String texto) {
        return texto.codePoints().allMatch(ComoDrf::esEspacio);
    }

    private static String tipoPython(JsonNode valor) {
        if (valor.isBoolean()) {
            return "bool";
        }
        return valor.isArray() ? "list" : valor.isObject() ? "dict" : "str";
    }
}
