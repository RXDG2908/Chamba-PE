package pe.tecsup.chambape.comun;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Lee el cuerpo de la petición como {@code request.data} de DRF: JSON o formulario
 * (application/x-www-form-urlencoded); sin cuerpo o sin tipo, vacío. Otro tipo: 415.
 */
public final class CuerpoComoDrf {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private CuerpoComoDrf() {
    }

    public static JsonNode leer(HttpServletRequest peticion) {
        byte[] bytes;
        try {
            bytes = peticion.getInputStream().readAllBytes();
        } catch (IOException error) {
            throw RespuestaError.detalle(400, "JSON parse error - " + error.getMessage());
        }
        String tipo = peticion.getContentType();
        if (bytes.length == 0 || tipo == null) {
            return JsonNodeFactory.instance.objectNode();
        }
        MediaType medio;
        try {
            medio = MediaType.parseMediaType(tipo);
        } catch (RuntimeException error) {
            throw noSoportado(tipo);
        }
        String texto = utf8Estricto(bytes);
        if (medio.isCompatibleWith(MediaType.APPLICATION_JSON)) {
            if (texto == null) {
                // JSONParser de DRF tampoco acepta bytes que no son UTF-8.
                throw RespuestaError.detalle(400, "JSON parse error - 'utf-8' codec can't decode the request body");
            }
            try {
                return JSON.readTree(texto);
            } catch (RuntimeException error) {
                // El texto exacto lo da el lector de JSON de Python; aquí se indica el mismo tipo de error.
                throw RespuestaError.detalle(400, "JSON parse error - " + error.getClass().getSimpleName());
            }
        }
        if (medio.isCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED)) {
            if (texto == null) {
                // QueryDict de Django: si el cuerpo no es UTF-8, lo lee entero como ISO-8859-1.
                texto = new String(bytes, StandardCharsets.ISO_8859_1);
            }
            ObjectNode datos = JsonNodeFactory.instance.objectNode();
            for (Map.Entry<String, List<String>> par : ConsultaComoDjango.leer(texto).entrySet()) {
                // QueryDict.get() devuelve el último valor, como DRF al validar.
                datos.put(par.getKey(), par.getValue().get(par.getValue().size() - 1));
            }
            return datos;
        }
        throw noSoportado(tipo);
    }

    private static String utf8Estricto(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException error) {
            return null;
        }
    }

    private static RespuestaError noSoportado(String tipo) {
        return RespuestaError.detalle(415, "Unsupported media type \"" + tipo + "\" in request.");
    }
}
