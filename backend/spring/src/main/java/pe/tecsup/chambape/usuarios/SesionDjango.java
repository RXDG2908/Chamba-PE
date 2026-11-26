package pe.tecsup.chambape.usuarios;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import pe.tecsup.chambape.comun.ClaveDjango;
import pe.tecsup.chambape.comun.FechasDjango;

/**
 * Lee la sesión de Django (cookie {@code sessionid}, tabla {@code django_session}) igual que
 * {@code SessionMiddleware} y {@code AuthenticationMiddleware}: el contenido firmado con la
 * DJANGO_SECRET_KEY ({@code signing.loads}), el id de la cuenta, el backend y el hash de su
 * contraseña (si la cambió, la sesión deja de valer). Spring nunca crea ni modifica sesiones:
 * el inicio y el cierre de sesión siguen en Django.
 */
@Component
public class SesionDjango {

    public static final String COOKIE = "sessionid";

    private static final String SAL_SESION = "django.contrib.sessions.SessionStore";
    private static final String SAL_HASH = "django.contrib.auth.models.AbstractBaseUser.get_session_auth_hash";
    private static final String BACKEND = "administracion.sanciones.BackendConSanciones";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final String clave;
    private final JdbcTemplate jdbc;
    private final FechasDjango fechas;

    public SesionDjango(ClaveDjango clave, JdbcTemplate jdbc, FechasDjango fechas) {
        this.clave = clave.valor();
        this.jdbc = jdbc;
        this.fechas = fechas;
    }

    public boolean disponible() {
        return !clave.isEmpty();
    }

    /** Lo guardado en la sesión que importa aquí; {@code hash} puede faltar. */
    public record Contenido(long usuarioId, String backend, String hash) {
    }

    /** El contenido de una sesión vigente y bien firmada, o vacío (como una sesión nueva). */
    public Optional<Contenido> leer(String clave) {
        if (!disponible() || clave == null || clave.isEmpty()) {
            return Optional.empty();
        }
        List<String> datos = jdbc.queryForList(
            "select session_data from django_session where session_key = ? and expire_date > ?",
            String.class, clave, fechas.parametro(FechasDjango.ahora()));
        return datos.isEmpty() ? Optional.empty() : decodificar(datos.get(0));
    }

    /**
     * La cuenta que AuthenticationMiddleware pondría en {@code request.user}: el id de la sesión,
     * con el backend configurado y el hash de su contraseña actual. Vacío si no coincide.
     * (Que la cuenta esté activa y sin sanción lo revisa quien llama, como el backend de Django.)
     */
    public Optional<Long> usuario(Contenido contenido) {
        if (!BACKEND.equals(contenido.backend()) || contenido.hash() == null) {
            return Optional.empty();
        }
        List<String> claves = jdbc.queryForList("select password from auth_user where id = ?", String.class,
            contenido.usuarioId());
        if (claves.isEmpty()) {
            return Optional.empty();
        }
        String esperado = HexFormat.of().formatHex(hmac(SAL_HASH, claves.get(0).getBytes(StandardCharsets.UTF_8)));
        boolean igual = MessageDigest.isEqual(esperado.getBytes(StandardCharsets.UTF_8),
            contenido.hash().getBytes(StandardCharsets.UTF_8));
        return igual ? Optional.of(contenido.usuarioId()) : Optional.empty();
    }

    // signing.loads(datos, salt=SAL_SESION, serializer=JSONSerializer): «carga:fecha:firma».
    Optional<Contenido> decodificar(String firmado) {
        int fin = firmado.lastIndexOf(':');
        if (fin < 0) {
            return Optional.empty();
        }
        String valor = firmado.substring(0, fin);
        byte[] firma = Base64.getUrlEncoder().withoutPadding()
            .encode(hmac(SAL_SESION + "signer", valor.getBytes(StandardCharsets.UTF_8)));
        if (!MessageDigest.isEqual(firma, firmado.substring(fin + 1).getBytes(StandardCharsets.UTF_8))) {
            return Optional.empty();
        }
        int fecha = valor.lastIndexOf(':');
        if (fecha < 0) {
            return Optional.empty();
        }
        String carga = valor.substring(0, fecha);
        boolean comprimida = carga.startsWith(".");
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(comprimida ? carga.substring(1) : carga);
            JsonNode sesion = JSON.readTree(new String(comprimida ? descomprimir(bytes) : bytes, StandardCharsets.ISO_8859_1));
            JsonNode id = sesion.get("_auth_user_id");
            JsonNode backend = sesion.get("_auth_user_backend");
            JsonNode hash = sesion.get("_auth_user_hash");
            if (id == null || !id.isString() || !id.asString().matches("[0-9]{1,18}")) {
                return Optional.empty();
            }
            return Optional.of(new Contenido(Long.parseLong(id.asString()),
                backend != null && backend.isString() ? backend.asString() : null,
                hash != null && hash.isString() ? hash.asString() : null));
        } catch (RuntimeException | DataFormatException ilegible) {
            return Optional.empty();
        }
    }

    // salted_hmac(sal, valor, SECRET_KEY, algorithm="sha256"): llave = sha256(sal + SECRET_KEY).
    private byte[] hmac(String sal, byte[] valor) {
        try {
            byte[] llave = MessageDigest.getInstance("SHA-256").digest((sal + clave).getBytes(StandardCharsets.UTF_8));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(llave, "HmacSHA256"));
            return mac.doFinal(valor);
        } catch (GeneralSecurityException imposible) {
            throw new IllegalStateException(imposible);
        }
    }

    private static byte[] descomprimir(byte[] comprimido) throws DataFormatException {
        Inflater inflador = new Inflater();
        inflador.setInput(comprimido);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        byte[] bloque = new byte[1024];
        try {
            while (!inflador.finished()) {
                int leidos = inflador.inflate(bloque);
                if (leidos == 0 && (inflador.needsInput() || inflador.needsDictionary())) {
                    throw new DataFormatException("incompleto");
                }
                salida.write(bloque, 0, leidos);
                if (salida.size() > 1_000_000) {
                    throw new DataFormatException("demasiado grande");
                }
            }
        } finally {
            inflador.end();
        }
        return salida.toByteArray();
    }

    /** Las 62 letras y cifras de los tokens CSRF de Django. */
    static final String LETRAS_CSRF = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    /** {@code _unmask_cipher_token}: el secreto de 32 caracteres de un token enmascarado de 64. */
    static String desenmascarar(String token) {
        String mascara = token.substring(0, 32);
        String cifrado = token.substring(32);
        char[] secreto = new char[32];
        for (int i = 0; i < 32; i++) {
            int x = LETRAS_CSRF.indexOf(cifrado.charAt(i));
            int y = LETRAS_CSRF.indexOf(mascara.charAt(i));
            secreto[i] = LETRAS_CSRF.charAt(Math.floorMod(x - y, LETRAS_CSRF.length()));
        }
        return new String(secreto);
    }

    /** {@code _check_token_format}: null si sirve, o el motivo. */
    static String formatoInvalido(String token) {
        if (token.length() != 32 && token.length() != 64) {
            return "has incorrect length";
        }
        return token.chars().allMatch(c -> LETRAS_CSRF.indexOf(c) >= 0) ? null : "has invalid characters";
    }
}
