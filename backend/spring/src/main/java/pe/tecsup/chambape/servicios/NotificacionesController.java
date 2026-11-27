package pe.tecsup.chambape.servicios;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import pe.tecsup.chambape.comun.ComoDrf;
import pe.tecsup.chambape.comun.CuerpoComoDrf;
import pe.tecsup.chambape.comun.ErrorValidacion;
import pe.tecsup.chambape.comun.FechasDjango;
import pe.tecsup.chambape.usuarios.Cuentas;
import pe.tecsup.chambape.usuarios.CsrfDjango;
import pe.tecsup.chambape.usuarios.SesionDjango;

/**
 * HU-8: el prestador registra (POST) o quita (DELETE) el token FCM de su navegador, como
 * {@code TokenFCMView} de {@code servicios/notificaciones.py}. El token queda ligado a la sesión de
 * Django con la que se registró: {@link Notificador} solo avisa mientras esa sesión siga vigente.
 */
@RestController
@RequestMapping("/api/servicios/notificaciones/token")
public class NotificacionesController {

    // TokenSerializer: RegexField(r'^[A-Za-z0-9_:\-]+$', min_length=20, max_length=2048).
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_:\\-]+");
    private static final int MINIMO = 20;
    private static final int MAXIMO = 2048;
    private static final String PATRON = "This value does not match the required pattern.";

    private final JdbcTemplate jdbc;
    private final FechasDjango fechas;
    private final SesionDjango sesiones;
    private final Cuentas cuentas;

    public NotificacionesController(JdbcTemplate jdbc, FechasDjango fechas, SesionDjango sesiones, Cuentas cuentas) {
        this.jdbc = jdbc;
        this.fechas = fechas;
        this.sesiones = sesiones;
        this.cuentas = cuentas;
    }

    @PostMapping("/")
    @Transactional
    public Map<String, Object> registrar(HttpServletRequest peticion) {
        Cuentas.Usuario prestador = SolicitudesController.prestador(peticion);
        String token = token(peticion);
        // update_or_create(token=..., defaults={'usuario': ..., 'sesion': request.session.session_key}).
        // Sin sesión de Django la columna queda nula y la base la rechaza (500), igual que en Django.
        String sesion = claveDeSesion(peticion);
        Object ahora = fechas.parametro(FechasDjango.ahora());
        int cambiadas = jdbc.update("update servicios_tokenfcm set usuario_id = ?, sesion = ?, actualizado = ? where token = ?",
            prestador.id(), sesion, ahora, token);
        if (cambiadas == 0) {
            jdbc.update("insert into servicios_tokenfcm (usuario_id, token, sesion, actualizado) values (?, ?, ?, ?)",
                prestador.id(), token, sesion, ahora);
        }
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("registrado", true);
        return cuerpo;
    }

    @DeleteMapping("/")
    @Transactional
    public ResponseEntity<Void> quitar(HttpServletRequest peticion) {
        Cuentas.Usuario prestador = SolicitudesController.prestador(peticion);
        String token = token(peticion);
        jdbc.update("delete from servicios_tokenfcm where usuario_id = ? and token = ?", prestador.id(), token);
        return ResponseEntity.noContent().build();
    }

    /**
     * DRF revisa la sesión y el permiso antes que el método: un cliente o un visitante recibe 403 y
     * solo el prestador ve el 405 (con {@code Allow} como el de la vista de Django).
     */
    @RequestMapping(value = "/", method = {RequestMethod.GET, RequestMethod.PUT, RequestMethod.PATCH})
    public ResponseEntity<Map<String, String>> otroMetodo(HttpServletRequest peticion) {
        SolicitudesController.prestador(peticion);
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).header(HttpHeaders.ALLOW, "POST, DELETE, OPTIONS")
            .body(Map.of("detail", "Method \"" + peticion.getMethod() + "\" not allowed."));
    }

    /** El token del cuerpo, validado como el TokenSerializer de Django (mismos mensajes y orden). */
    private static String token(HttpServletRequest peticion) {
        JsonNode datos = CuerpoComoDrf.leer(peticion);
        if (!datos.isObject()) {
            throw ComoDrf.noEsDiccionario(datos);
        }
        Map<String, List<String>> errores = new LinkedHashMap<>();
        String token = ComoDrf.campo(datos, "token", errores, NotificacionesController::validar);
        if (!errores.isEmpty()) {
            throw new ErrorValidacion(new LinkedHashMap<>(errores));
        }
        return token;
    }

    // CharField.run_validation (blank, tipo y strip) y luego sus validadores en el orden en que DRF
    // los agrega: MaxLength, MinLength, sin nulos, sin sustitutos y al final el RegexValidator.
    static String validar(JsonNode valor) throws ComoDrf.Invalido {
        if (!valor.isBoolean() && (valor.isString() || valor.isNumber())
            && ComoDrf.soloEspacios(ComoDrf.comoTextoPython(valor))) {
            throw new ComoDrf.Invalido("This field may not be blank.");
        }
        if (valor.isBoolean() || !(valor.isString() || valor.isNumber())) {
            // RegexField cambia el mensaje 'invalid' de CharField: un booleano, una lista o un objeto
            // reciben el mismo texto que un token con caracteres no permitidos.
            throw new ComoDrf.Invalido(PATRON);
        }
        String texto = ComoDrf.stripPython(ComoDrf.comoTextoPython(valor));
        int largo = ComoDrf.largo(texto);
        List<String> mensajes = new ArrayList<>();
        if (largo > MAXIMO) {
            mensajes.add("Ensure this field has no more than " + MAXIMO + " characters.");
        }
        if (largo < MINIMO) {
            mensajes.add("Ensure this field has at least " + MINIMO + " characters.");
        }
        if (texto.indexOf('\0') >= 0) {
            mensajes.add("Null characters are not allowed.");
        }
        texto.codePoints().filter(c -> c >= 0xD800 && c <= 0xDFFF).findFirst().ifPresent(c ->
            mensajes.add(String.format("Surrogate characters are not allowed: U+%X.", c)));
        if (!TOKEN.matcher(texto).matches()) {
            mensajes.add(PATRON);
        }
        if (!mensajes.isEmpty()) {
            throw new ComoDrf.Invalido(mensajes);
        }
        return texto;
    }

    /**
     * {@code request.session.session_key} después de autenticar: la cookie {@code sessionid} si es
     * una sesión vigente; null si no existe, venció o no está bien firmada (Django la descarta al
     * leerla) y también cuando Django la vacía con {@code flush()} porque el hash de la contraseña no
     * coincide (backend correcto, cuenta activa y sin sanción).
     */
    private String claveDeSesion(HttpServletRequest peticion) {
        String cookie = CsrfDjango.cookie(peticion, SesionDjango.COOKIE);
        Optional<SesionDjango.Contenido> sesion = sesiones.leer(cookie);
        if (sesion.isEmpty()) {
            return null;
        }
        SesionDjango.Contenido contenido = sesion.get();
        boolean vaciada = sesiones.usuario(contenido).isEmpty() && sesiones.backendConfigurado(contenido)
            && cuentas.activaPorId(contenido.usuarioId()).filter(u -> cuentas.sancionVigente(u.id()).isEmpty()).isPresent();
        return vaciada ? null : cookie;
    }
}
