package pe.tecsup.chambape.usuarios;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import pe.tecsup.chambape.comun.CuerpoComoDrf;
import pe.tecsup.chambape.comun.RespuestaError;

/**
 * {@code POST /api/usuarios/vincular/}, igual que {@code VincularCuentaView} de Django: la app vincula su
 * cuenta de Firebase con la cuenta de la web del mismo correo, probando que conoce su contraseña. Así el
 * mismo correo y contraseña sirven en la web y en la app. El correo solo no basta (en Firebase cualquiera
 * crea una cuenta con un correo ajeno) y los fallos por correo tienen límite: 10 por hora (OWASP API2).
 */
@RestController
public class VincularCuentaController {

    public static final String RUTA = "/api/usuarios/vincular/";
    static final int INTENTOS_MAXIMOS = 10;
    static final long VENTANA_SEGUNDOS = 60 * 60;
    static final String NO_COINCIDE = "El correo o la contraseña no coinciden con una cuenta de la web.";
    static final String DEMASIADOS = "Demasiados intentos con este correo. Inténtalo de nuevo en una hora.";

    private final VerificadorFirebase verificador;
    private final RegistroMovil registro;
    private final Cuentas cuentas;
    private final JdbcTemplate jdbc;
    private final Clock reloj = Clock.systemUTC();
    /** Fallos por correo en la ventana actual (como la caché de Django; se pierde al reiniciar). */
    private final Map<String, Fallos> fallos = new ConcurrentHashMap<>();

    private record Fallos(int cuantos, Instant desde) {
    }

    public VincularCuentaController(VerificadorFirebase verificador, RegistroMovil registro, Cuentas cuentas, JdbcTemplate jdbc) {
        this.verificador = verificador;
        this.registro = registro;
        this.cuentas = cuentas;
        this.jdbc = jdbc;
    }

    private record Cuenta(long id, String hash, String rol, String uid) {
    }

    @PostMapping(RUTA)
    @Transactional
    public Map<String, Object> vincular(HttpServletRequest peticion) {
        VerificadorFirebase.Identidad identidad = identidad(peticion);
        if (registro.vinculada(identidad.uid()).isPresent()) {
            throw RespuestaError.detalle(409, "Esta cuenta de la app ya está vinculada.");
        }
        String correo = identidad.correo() == null ? "" : identidad.correo().strip();
        JsonNode datos = CuerpoComoDrf.leer(peticion);
        JsonNode valor = datos.isObject() ? datos.get("password") : null;
        String clave = valor != null && valor.isString() ? valor.asString() : "";
        if (correo.isEmpty() || clave.isEmpty()) {
            throw RespuestaError.detalle(400, NO_COINCIDE);
        }
        String llave = correo.toLowerCase(java.util.Locale.ROOT);
        if (fallosVigentes(llave) >= INTENTOS_MAXIMOS) {
            throw RespuestaError.detalle(429, DEMASIADOS);
        }

        Cuenta cuenta = jdbc.query("""
            select u.id, u.password, p.rol, p.firebase_uid from auth_user u
            left join usuarios_perfil p on p.usuario_id = u.id
            where upper(u.email) = upper(?) and u.is_active order by u.id""",
            (fila, n) -> new Cuenta(fila.getLong("id"), fila.getString("password"), fila.getString("rol"),
                fila.getString("firebase_uid")), correo).stream().findFirst().orElse(null);
        if (cuenta == null || !contrasenaDjango(clave, cuenta.hash())) {
            sumarFallo(llave);
            throw RespuestaError.detalle(400, NO_COINCIDE);
        }
        if (!Cuentas.CLIENTE.equals(cuenta.rol()) && !Cuentas.PRESTADOR.equals(cuenta.rol())) {
            throw RespuestaError.detalle(409, "Esta cuenta no se usa en la app.");
        }
        if (cuenta.uid() != null && !cuenta.uid().isEmpty()) {
            throw RespuestaError.detalle(409, "Esa cuenta de la web ya está vinculada con otra cuenta de la app.");
        }
        cuentas.sancionVigente(cuenta.id()).ifPresent(mensaje -> {
            throw RespuestaError.detalle(403, mensaje, "cuenta_sancionada");
        });
        jdbc.update("update usuarios_perfil set firebase_uid = ? where usuario_id = ?", identidad.uid(), cuenta.id());
        fallos.remove(llave);
        return registro.usuarioActual(cuenta.id());
    }

    private int fallosVigentes(String llave) {
        Fallos f = fallos.get(llave);
        if (f == null) return 0;
        if (f.desde().plusSeconds(VENTANA_SEGUNDOS).isBefore(reloj.instant())) {
            fallos.remove(llave, f);
            return 0;
        }
        return f.cuantos();
    }

    private void sumarFallo(String llave) {
        Instant ahora = reloj.instant();
        fallos.compute(llave, (k, f) -> f == null || f.desde().plusSeconds(VENTANA_SEGUNDOS).isBefore(ahora)
            ? new Fallos(1, ahora) : new Fallos(f.cuantos() + 1, f.desde()));
    }

    /**
     * {@code check_password} de Django para el hasher por defecto: {@code pbkdf2_sha256$iteraciones$sal$hash}.
     * Una contraseña no utilizable («!…», las cuentas creadas por la app) u otro algoritmo nunca coinciden.
     */
    static boolean contrasenaDjango(String clave, String guardado) {
        if (guardado == null) return false;
        String[] partes = guardado.split("\\$", -1);
        if (partes.length != 4 || !"pbkdf2_sha256".equals(partes[0])) return false;
        try {
            int iteraciones = Integer.parseInt(partes[1]);
            byte[] esperado = Base64.getDecoder().decode(partes[3]);
            PBEKeySpec spec = new PBEKeySpec(clave.toCharArray(), partes[2].getBytes(StandardCharsets.UTF_8), iteraciones, esperado.length * 8);
            byte[] calculado = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return MessageDigest.isEqual(esperado, calculado);
        } catch (IllegalArgumentException | java.security.GeneralSecurityException e) {
            return false;
        }
    }

    // Igual que RegistroMovilController: 403 si el token falta, está mal formado o no es válido.
    private VerificadorFirebase.Identidad identidad(HttpServletRequest peticion) {
        String cabecera = peticion.getHeader(HttpHeaders.AUTHORIZATION);
        String[] partes = cabecera == null ? new String[0] : cabecera.strip().split("[ \\t\\n\\x0B\\f\\r]+");
        boolean bienFormada = partes.length == 2 && partes[0].equalsIgnoreCase("bearer")
            && partes[1].chars().allMatch(c -> c < 128);
        return (bienFormada ? verificador.identidad(partes[1]) : java.util.Optional.<VerificadorFirebase.Identidad>empty())
            .orElseThrow(() -> RespuestaError.detalle(403, "El token no es válido o venció."));
    }
}
