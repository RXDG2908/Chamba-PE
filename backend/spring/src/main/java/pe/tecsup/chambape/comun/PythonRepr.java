package pe.tecsup.chambape.comun;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * {@code repr(float)} de Python: los dígitos más cortos que vuelven al mismo double (Java 19+ da los
 * mismos con {@code Double.toString}), pero con el formato de Python: notación fija entre 1e-4 y 1e16
 * («100.0», «0.0001») y exponencial fuera («1e-05», «1.5e+16»).
 */
public final class PythonRepr {

    private PythonRepr() {
    }

    // valor = d.ddd × 10^exponente, con los dígitos más cortos.
    private record Digitos(boolean negativo, String digitos, int exponente) {
    }

    private static Digitos digitos(double valor) {
        BigDecimal exacto = new BigDecimal(Double.toString(Math.abs(valor)));
        if (exacto.signum() == 0) {
            return new Digitos(1 / valor < 0, "0", 0);
        }
        BigDecimal limpio = exacto.stripTrailingZeros();
        String cifras = limpio.unscaledValue().toString();
        int cientifico = cifras.length() - 1 - limpio.scale();
        return new Digitos(valor < 0, cifras, cientifico);
    }

    /** {@code repr(valor)} de Python. */
    public static String flotante(double valor) {
        if (Double.isNaN(valor)) {
            return "nan";
        }
        if (Double.isInfinite(valor)) {
            return valor > 0 ? "inf" : "-inf";
        }
        Digitos d = digitos(valor);
        String signo = d.negativo() ? "-" : "";
        if (d.exponente() >= -4 && d.exponente() < 16) {
            BigDecimal fijo = new BigDecimal(new BigInteger(d.digitos()), d.digitos().length() - 1 - d.exponente());
            String texto = fijo.toPlainString();
            return signo + (texto.contains(".") ? texto : texto + ".0");
        }
        String mantisa = d.digitos().length() == 1 ? d.digitos() : d.digitos().charAt(0) + "." + d.digitos().substring(1);
        int e = d.exponente();
        return signo + mantisa + "e" + (e < 0 ? "-" : "+") + (Math.abs(e) < 10 ? "0" : "") + Math.abs(e);
    }

    /** {@code Decimal(str(valor))}: mismos dígitos y exponente que obtiene Python. */
    public static BigDecimal decimalDeFlotante(double valor) {
        return new BigDecimal(flotante(valor));
    }
}
