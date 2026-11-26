package pe.tecsup.chambape.usuarios;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * La comprobación CSRF que DRF hace a las peticiones con sesión ({@code SessionAuthentication.enforce_csrf},
 * que usa {@code CsrfViewMiddleware}): el Origin debe ser el propio servidor o uno de confianza, y la
 * cabecera {@code X-CSRFToken} debe corresponder a la cookie {@code csrftoken}. La web ya envía ambos.
 */
@Component
public class CsrfDjango {

    private static final Set<String> SEGUROS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final Set<String> confiables;

    public CsrfDjango(@Value("${chambape.django.csrf-origenes:}") String origenes) {
        this.confiables = Set.copyOf(Arrays.stream(origenes.split(",")).map(String::strip).filter(o -> !o.isEmpty())
            .toList());
    }

    /** El motivo del rechazo, con el mismo texto que Django, o null si la petición pasa. */
    public String motivo(HttpServletRequest peticion) {
        if (SEGUROS.contains(peticion.getMethod())) {
            return null;
        }
        String esquema = peticion.isSecure() ? "https" : "http";
        String propio = esquema + "://" + peticion.getHeader("Host");
        String origen = peticion.getHeader("Origin");
        if (origen != null) {
            if (!origen.equals(propio) && !confiables.contains(origen)) {
                return "Origin checking failed - " + origen + " does not match any trusted origins.";
            }
        } else if (peticion.isSecure()) {
            String referer = peticion.getHeader("Referer");
            if (referer == null) {
                return "Referer checking failed - no Referer.";
            }
            if (!refererConfiable(referer, peticion.getHeader("Host"))) {
                return "Referer checking failed - " + referer + " does not match any trusted origins.";
            }
        }

        String secreto = cookie(peticion, "csrftoken");
        if (secreto != null) {
            String formato = SesionDjango.formatoInvalido(secreto);
            if (formato != null) {
                return "CSRF cookie " + formato + ".";
            }
            if (secreto.length() == 64) {
                secreto = SesionDjango.desenmascarar(secreto);
            }
        } else {
            return "CSRF cookie not set.";
        }
        // La web no envía formularios con csrfmiddlewaretoken: solo se usa la cabecera.
        String token = peticion.getHeader("X-CSRFToken");
        if (token == null) {
            return "CSRF token missing.";
        }
        String formato = SesionDjango.formatoInvalido(token);
        if (formato != null) {
            return "CSRF token from the 'X-Csrftoken' HTTP header " + formato + ".";
        }
        String enviado = token.length() == 64 ? SesionDjango.desenmascarar(token) : token;
        if (!MessageDigest.isEqual(enviado.getBytes(StandardCharsets.US_ASCII), secreto.getBytes(StandardCharsets.US_ASCII))) {
            return "CSRF token from the 'X-Csrftoken' HTTP header incorrect.";
        }
        return null;
    }

    private boolean refererConfiable(String referer, String host) {
        try {
            URI uri = URI.create(referer);
            if (!"https".equals(uri.getScheme()) || uri.getRawAuthority() == null) {
                return false;
            }
            String origen = "https://" + uri.getRawAuthority();
            return uri.getRawAuthority().equals(host) || confiables.contains(origen);
        } catch (IllegalArgumentException malFormado) {
            return false;
        }
    }

    static String cookie(HttpServletRequest peticion, String nombre) {
        Cookie[] cookies = peticion.getCookies();
        if (cookies == null) {
            return null;
        }
        // Django se queda con la última cookie repetida.
        List<Cookie> todas = Arrays.stream(cookies).filter(c -> nombre.equals(c.getName())).toList();
        return todas.isEmpty() ? null : todas.get(todas.size() - 1).getValue();
    }
}
