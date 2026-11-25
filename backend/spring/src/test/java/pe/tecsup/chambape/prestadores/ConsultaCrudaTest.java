package pe.tecsup.chambape.prestadores;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * URLs que Django acepta y Tomcat rechazaría: «%» mal formado y caracteres como | { }.
 * Van por un socket, tal cual, porque los clientes HTTP de Java las corrigen o las rechazan antes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConsultaCrudaTest extends PruebaConBase {

    @LocalServerPort
    private int puerto;

    private String pedir(String ruta) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", puerto)) {
            OutputStream salida = socket.getOutputStream();
            salida.write(("GET " + ruta + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")
                .getBytes(StandardCharsets.ISO_8859_1));
            salida.flush();
            InputStream entrada = socket.getInputStream();
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void contiene(String respuesta, String... textos) {
        for (String texto : textos) {
            assertTrue(respuesta.contains(texto), "Falta «" + texto + "» en:\n" + respuesta);
        }
    }

    @Test
    void porcentajeMalFormadoSeValidaComoTexto() throws Exception {
        String respuesta = pedir("/api/prestadores/buscar/?rubro=%ZZ&latitud=1%&longitud=%2");
        contiene(respuesta, "HTTP/1.1 400",
            "\"rubro\":[\"El rubro debe ser un ID entero válido.\"]",
            "\"latitud\":[\"Ingresa una coordenada numérica válida.\"]",
            "\"longitud\":[\"Ingresa una coordenada numérica válida.\"]");
    }

    @Test
    void caracteresQueTomcatNoPermitePorDefecto() throws Exception {
        String respuesta = pedir("/api/prestadores/buscar/?rubro={1}&latitud=[0]&longitud=|0|");
        contiene(respuesta, "HTTP/1.1 400",
            "\"rubro\":[\"El rubro debe ser un ID entero válido.\"]",
            "\"latitud\":[\"Ingresa una coordenada numérica válida.\"]");
    }

    @Test
    void consultaLargaComoEnDjango() throws Exception {
        // 1001 emojis codificados son 12 KB de URL: Tomcat por defecto la cortaría a los 8 KB.
        String respuesta = pedir("/api/prestadores/buscar/?rubro=" + "%F0%9F%98%80".repeat(1001) + "&latitud=0&longitud=0");
        contiene(respuesta, "HTTP/1.1 400", "\"rubro\":[\"El ID del rubro es demasiado largo.\"]");
    }

    @Test
    void consultaValidaConMasYBytesNoUtf8() throws Exception {
        // «+» es espacio y %FF no es UTF-8 (se vuelve «�», como en Django).
        contiene(pedir("/api/prestadores/buscar/?rubro=+1+&latitud=-12.044861&longitud=-77.026582&x=%FF"),
            "HTTP/1.1 200", "\"nombres\":\"Ana\"");
    }
}
