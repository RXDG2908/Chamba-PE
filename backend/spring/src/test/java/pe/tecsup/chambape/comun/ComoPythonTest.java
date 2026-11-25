package pe.tecsup.chambape.comun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Valores esperados sacados de Python 3.13 (urllib.parse.parse_qsl, int() y float()). */
class ComoPythonTest {

    private static Map<String, List<String>> leer(String consulta) {
        return ConsultaComoDjango.leer(consulta);
    }

    @Test
    void consultaComoParseQsl() {
        assertEquals(Map.of("a", List.of("%ZZ"), "b", List.of("1%")), leer("a=%ZZ&b=1%"));
        assertEquals(Map.of("a", List.of("é"), "b", List.of("�")), leer("a=%C3%A9&b=%FF"));
        assertEquals(Map.of("a", List.of("�x")), leer("a=%E2%82x"));
        assertEquals(Map.of("a b", List.of("c d")), leer("a+b=c+d"));
        assertEquals(Map.of("a", List.of("1", "2")), leer("a=1&a=2"));
        assertEquals(Map.of("a", List.of("")), leer("a"));
        assertEquals(Map.of("a", List.of("1")), leer("&&a=1&"));
        assertEquals(Map.of("a", List.of("%2")), leer("a=%2"));
        assertEquals(Map.of("a", List.of("1")), leer("%61=1"));
        assertEquals(Map.of("a", List.of("=b")), leer("a==b"));
        assertEquals(Map.of("a", List.of("😀")), leer("a=%F0%9F%98%80"));
        assertEquals(Map.of("a", List.of("���")), leer("a=%ED%A0%80"));
        assertEquals(Map.of("a", List.of("�€")), leer("a=%E2%82%E2%82%AC"));
        assertEquals(Map.of("a", List.of("��")), leer("a=%C0%AF"));
        assertEquals(Map.of("a", List.of("���")), leer("a=%E0%80%80"));
        assertEquals(Map.of("a", List.of("����")), leer("a=%F4%90%80%80"));
        assertEquals(Map.of("a", List.of("�")), leer("a=%F0%9F%98"));
        assertEquals(Map.of("a", List.of("€z�")), leer("a=%E2%82%ACz%C3"));
        assertEquals(Map.of(), leer(null));
        assertEquals("2", ConsultaComoDjango.ultimo(leer("a=1&a=2"), "a"));
        assertNull(ConsultaComoDjango.ultimo(leer("a=1"), "b"));
    }

    @Test
    void enteroComoIntegerFieldDeDrf() {
        assertEquals(BigInteger.valueOf(12), NumerosComoPython.entero("١٢"));
        assertEquals(BigInteger.ONE, NumerosComoPython.entero(" 1　"));
        assertEquals(BigInteger.ONE, NumerosComoPython.entero("1 "));
        assertEquals(BigInteger.ONE, NumerosComoPython.entero("1.0　"));
        assertEquals(BigInteger.ONE, NumerosComoPython.entero("1.0\u001c"));
        assertEquals(BigInteger.valueOf(7), NumerosComoPython.entero("\u00857"));
        assertEquals(BigInteger.ONE, NumerosComoPython.entero("\u000b1"));
        assertNull(NumerosComoPython.entero("\u001c7"));
        assertNull(NumerosComoPython.entero("1\u001c"));
        assertNull(NumerosComoPython.entero("٣.٠"));
        assertNull(NumerosComoPython.entero("1​"));
    }

    @Test
    void realComoFloatDePython() {
        assertEquals(1.5, NumerosComoPython.real("١.٥"));
        assertEquals(10.0, NumerosComoPython.real(" 1e1　"));
        assertEquals(3.0, NumerosComoPython.real("٣"));
        assertEquals(7.0, NumerosComoPython.real("\u00857"));
        assertEquals(1.0, NumerosComoPython.real("\u000b1"));
        assertNull(NumerosComoPython.real("1​"));
        assertNull(NumerosComoPython.real("\u001c7"));
        assertNull(NumerosComoPython.real("1.0\u001c"));
    }
}
