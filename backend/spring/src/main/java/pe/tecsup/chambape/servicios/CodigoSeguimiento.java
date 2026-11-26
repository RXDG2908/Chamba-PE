package pe.tecsup.chambape.servicios;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import pe.tecsup.chambape.comun.ClaveDjango;

/**
 * Código de seguimiento de una solicitud sin cuenta (HU-11), idéntico al de Django
 * ({@code servicios/codigo_seguimiento.py}): {@code signing.Signer(salt=...).sign(id)} con SHA-256.
 * Necesita la misma DJANGO_SECRET_KEY; sin ella no se entregan ni se aceptan códigos.
 */
@Component
public class CodigoSeguimiento {

    private static final String SAL = "servicios.solicitud.seguimiento";

    private final byte[] llave;

    public CodigoSeguimiento(ClaveDjango clave) {
        String claveSecreta = clave.valor();
        // salted_hmac de Django: llave = sha256(sal + "signer" + SECRET_KEY).
        this.llave = claveSecreta.isEmpty() ? null : sha256(SAL + "signer" + claveSecreta);
    }

    public boolean disponible() {
        return llave != null;
    }

    /** El código de la solicitud, o null si no hay clave configurada. */
    public String de(long solicitudId) {
        if (llave == null) {
            return null;
        }
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(llave, "HmacSHA256"));
            byte[] firma = hmac.doFinal(Long.toString(solicitudId).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(firma);
        } catch (GeneralSecurityException imposible) {
            throw new IllegalStateException(imposible);
        }
    }

    /** Compara en tiempo constante, como {@code Signer.unsign}. */
    public boolean valido(long solicitudId, String codigo) {
        String esperado = de(solicitudId);
        if (esperado == null || codigo == null || codigo.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(esperado.getBytes(StandardCharsets.UTF_8), codigo.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] sha256(String texto) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException imposible) {
            throw new IllegalStateException(imposible);
        }
    }
}
