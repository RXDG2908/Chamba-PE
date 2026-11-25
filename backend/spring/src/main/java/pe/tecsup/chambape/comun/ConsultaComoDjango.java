package pe.tecsup.chambape.comun;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lee la consulta de la URL como Django ({@code QueryDict}): separa por {@code &}, el {@code +}
 * es un espacio, un {@code %} mal formado ({@code %ZZ}, {@code 1%}) se deja tal cual y los bytes
 * que no son UTF-8 válido se cambian por «�». Así Spring nunca rechaza una URL que Django acepta,
 * y la validación da los mismos mensajes. Se usa en vez de {@code getParameter()} de Tomcat,
 * que responde 400 ante un {@code %} mal formado.
 */
public final class ConsultaComoDjango {

    private ConsultaComoDjango() {
    }

    public static Map<String, List<String>> leer(String consulta) {
        Map<String, List<String>> valores = new LinkedHashMap<>();
        if (consulta == null || consulta.isEmpty()) {
            return valores;
        }
        for (String par : consulta.split("&", -1)) {
            if (par.isEmpty()) {
                continue;
            }
            int igual = par.indexOf('=');
            String nombre = decodificar(igual < 0 ? par : par.substring(0, igual));
            String valor = igual < 0 ? "" : decodificar(par.substring(igual + 1));
            valores.computeIfAbsent(nombre, clave -> new ArrayList<>()).add(valor);
        }
        return valores;
    }

    /** Si un parámetro se repite, Django usa el último valor. */
    public static String ultimo(Map<String, List<String>> valores, String nombre) {
        List<String> lista = valores.get(nombre);
        return lista == null || lista.isEmpty() ? null : lista.get(lista.size() - 1);
    }

    // Como urllib.parse.unquote_plus(texto, errors='replace').
    static String decodificar(String texto) {
        String conEspacios = texto.replace('+', ' ');
        StringBuilder salida = new StringBuilder();
        ByteArrayOutputStream escapados = new ByteArrayOutputStream();
        int i = 0;
        while (i < conEspacios.length()) {
            char c = conEspacios.charAt(i);
            if (c == '%' && i + 2 < conEspacios.length()
                && hex(conEspacios.charAt(i + 1)) >= 0 && hex(conEspacios.charAt(i + 2)) >= 0) {
                escapados.write(hex(conEspacios.charAt(i + 1)) * 16 + hex(conEspacios.charAt(i + 2)));
                i += 3;
                continue;
            }
            volcar(escapados, salida);
            salida.append(c);
            i++;
        }
        volcar(escapados, salida);
        return salida.toString();
    }

    private static void volcar(ByteArrayOutputStream escapados, StringBuilder salida) {
        if (escapados.size() > 0) {
            utf8ComoPython(escapados.toByteArray(), salida);
            escapados.reset();
        }
    }

    /**
     * UTF-8 con errors='replace' como Python: cada tramo inválido más largo posible se cambia por
     * un «�» (regla de Unicode). El decodificador de Java a veces junta varios tramos en uno solo.
     */
    static void utf8ComoPython(byte[] bytes, StringBuilder salida) {
        int i = 0;
        while (i < bytes.length) {
            int b0 = bytes[i] & 0xff;
            int faltan;
            int minimo = 0x80;
            int maximo = 0xbf;
            if (b0 < 0x80) {
                salida.append((char) b0);
                i++;
                continue;
            } else if (b0 >= 0xc2 && b0 <= 0xdf) {
                faltan = 1;
            } else if (b0 >= 0xe0 && b0 <= 0xef) {
                faltan = 2;
                minimo = b0 == 0xe0 ? 0xa0 : 0x80;
                maximo = b0 == 0xed ? 0x9f : 0xbf;
            } else if (b0 >= 0xf0 && b0 <= 0xf4) {
                faltan = 3;
                minimo = b0 == 0xf0 ? 0x90 : 0x80;
                maximo = b0 == 0xf4 ? 0x8f : 0xbf;
            } else {
                salida.append('\ufffd');
                i++;
                continue;
            }
            int punto = b0 & (0x3f >> faltan);
            int j = i + 1;
            boolean completo = true;
            for (int k = 0; k < faltan; k++, j++) {
                int limiteInferior = k == 0 ? minimo : 0x80;
                int limiteSuperior = k == 0 ? maximo : 0xbf;
                if (j >= bytes.length || (bytes[j] & 0xff) < limiteInferior || (bytes[j] & 0xff) > limiteSuperior) {
                    completo = false;
                    break;
                }
                punto = (punto << 6) | (bytes[j] & 0x3f);
            }
            if (completo) {
                salida.appendCodePoint(punto);
            } else {
                salida.append('\ufffd');  // el byte que falló se lee de nuevo como inicio
            }
            i = j;
        }
    }

    private static int hex(char c) {
        return c < 128 ? Character.digit(c, 16) : -1;
    }
}
