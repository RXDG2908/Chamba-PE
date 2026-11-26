package pe.tecsup.chambape.comun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import pe.tecsup.chambape.servicios.CodigoSeguimiento;

/** Valores esperados sacados de Python 3.13, Django 6.1 y DRF (repr, Decimal, Signer, DateTimeField). */
class ComoDrfTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static JsonNode json(String texto) {
        return JSON.readTree(texto);
    }

    private static String decimal(String json) throws ComoDrf.Invalido {
        return ComoDrf.decimal(json(json), 9, 6).toPlainString();
    }

    private static String error(String json) {
        return assertThrows(ComoDrf.Invalido.class, () -> ComoDrf.decimal(json(json), 9, 6)).getMessage();
    }

    @Test
    void reprDePython() {
        assertEquals("100.0", PythonRepr.flotante(100.0));
        assertEquals("-12.046374", PythonRepr.flotante(-12.046374));
        assertEquals("1e-05", PythonRepr.flotante(0.00001));
        assertEquals("0.0001", PythonRepr.flotante(0.0001));
        assertEquals("1.5e+16", PythonRepr.flotante(1.5e16));
        assertEquals("1e+16", PythonRepr.flotante(1e16));
        assertEquals("9999999999999998.0", PythonRepr.flotante(9999999999999998.0));
        assertEquals("-0.0", PythonRepr.flotante(-0.0));
        assertEquals("0.1", PythonRepr.flotante(0.1));
    }

    @Test
    void decimalFieldComoDrf() throws Exception {
        assertEquals("-12.046374", decimal("\"-12.046374\""));
        assertEquals("-12.046374", decimal("-12.046374"));
        assertEquals("1.500000", decimal("\" 1.5\\u001c\""));
        assertEquals("10.000000", decimal("\"1_0\""));
        assertEquals("0.000010", decimal("0.00001"));
        assertEquals("A valid number is required.", error("\"nan\""));
        assertEquals("A valid number is required.", error("\"Infinity\""));
        assertEquals("A valid number is required.", error("true"));
        assertEquals("A valid number is required.", error("\"1,5\""));
        assertEquals("Ensure that there are no more than 9 digits in total.", error("\"1234.567890\""));
        assertEquals("Ensure that there are no more than 6 decimal places.", error("0.0000001"));
        assertEquals("Ensure that there are no more than 3 digits before the decimal point.", error("1000"));
        assertEquals("Ensure that there are no more than 9 digits in total.", error("1e16"));
        assertEquals("String value too large.", error("\"" + "1".repeat(1001) + "\""));
    }

    @Test
    void booleanosYClaves() throws Exception {
        assertTrue(ComoDrf.booleano(json("\"yes\"")));
        assertTrue(ComoDrf.booleano(json("1.0")));
        assertFalse(ComoDrf.booleano(json("\"off\"")));
        assertEquals("Must be a valid boolean.",
            assertThrows(ComoDrf.Invalido.class, () -> ComoDrf.booleano(json("2"))).getMessage());
        assertEquals("Must be a valid boolean.",
            assertThrows(ComoDrf.Invalido.class, () -> ComoDrf.booleano(json("\"verdad\""))).getMessage());
        assertEquals(5, ComoDrf.clavePrimaria(json("\" 5 \"")));
        assertEquals(5, ComoDrf.clavePrimaria(json("5.7")));
        assertEquals("Incorrect type. Expected pk value, received str.",
            assertThrows(ComoDrf.Invalido.class, () -> ComoDrf.clavePrimaria(json("\"5.0\""))).getMessage());
        assertEquals("Incorrect type. Expected pk value, received list.",
            assertThrows(ComoDrf.Invalido.class, () -> ComoDrf.clavePrimaria(json("[1]"))).getMessage());
        assertEquals("Invalid pk \"5.7\" - object does not exist.", ComoDrf.noExiste(json("5.7")));
    }

    @Test
    void codigoDeSeguimientoIgualAlDeDjango() {
        // signing.Signer(salt='servicios.solicitud.seguimiento').sign(id) con SECRET_KEY='clave-de-prueba-spring'.
        CodigoSeguimiento codigos = new CodigoSeguimiento("clave-de-prueba-spring");
        assertEquals("Mu2dU6kmoRJau7lcWsc3xtfZZOsYcbRsN-um5WFmavI", codigos.de(1));
        assertEquals("hURfMxXndlxozW4g6djnK0bE0YIpwbiHKv8QgYcKXpU", codigos.de(42));
        assertEquals("fv-72qCXnfJahzMLL_d32yAP9RS2nJSJyq6CpoAn6eg", codigos.de(123456));
        assertTrue(codigos.valido(42, "hURfMxXndlxozW4g6djnK0bE0YIpwbiHKv8QgYcKXpU"));
        assertFalse(codigos.valido(43, "hURfMxXndlxozW4g6djnK0bE0YIpwbiHKv8QgYcKXpU"));
        assertFalse(codigos.valido(42, ""));
        CodigoSeguimiento sinClave = new CodigoSeguimiento("");
        assertNull(sinClave.de(1));
        assertFalse(sinClave.valido(1, "cualquiera"));
    }

    @Test
    void fechasComoDjangoYDrf() {
        Instant fecha = FechasDjango.leer("2026-11-25 21:47:01.123456");
        assertEquals("2026-11-25T21:47:01.123456Z", FechasDjango.paraApi(fecha));
        assertEquals("2026-11-25T21:47:01Z", FechasDjango.paraApi(FechasDjango.leer("2026-11-25 21:47:01")));
        assertEquals("2026-11-25 21:47:01.123456", new FechasDjango("jdbc:sqlite:x").parametro(fecha));
        assertEquals("2026-11-25 21:47:01", new FechasDjango("jdbc:sqlite:x").parametro(Instant.parse("2026-11-25T21:47:01Z")));
    }
}
