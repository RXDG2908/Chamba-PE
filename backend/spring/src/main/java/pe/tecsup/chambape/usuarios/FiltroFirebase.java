package pe.tecsup.chambape.usuarios;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Quién hace la petición, en el mismo orden que DRF ({@code DEFAULT_AUTHENTICATION_CLASSES}):
 * <ol>
 *   <li>La sesión de Django de la web (cookie {@code sessionid}), como {@code SessionAuthentication}:
 *       una cuenta activa y sin sanción; en POST/PATCH exige el CSRF (si falla, 403).</li>
 *   <li>El token de la app ({@code Authorization: Bearer <token de Firebase>}), como
 *       {@code AutenticacionFirebase}: con un token inválido, de una cuenta no vinculada o sancionada
 *       (HU-13) responde 403 con el motivo.</li>
 * </ol>
 * Sin ninguno de los dos la petición sigue como anónima.
 */
@Component
public class FiltroFirebase extends OncePerRequestFilter {

    public static final String ATRIBUTO = "chambape.usuario";
    /** El id de cuenta guardado en la sesión, aunque no autentique (cuenta sancionada). */
    public static final String ID_EN_SESION = "chambape.id-en-sesion";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final VerificadorFirebase verificador;
    private final Cuentas cuentas;
    private final SesionDjango sesiones;
    private final CsrfDjango csrf;

    public FiltroFirebase(VerificadorFirebase verificador, Cuentas cuentas, SesionDjango sesiones, CsrfDjango csrf) {
        this.verificador = verificador;
        this.cuentas = cuentas;
        this.sesiones = sesiones;
        this.csrf = csrf;
    }

    /** El id de cuenta de la sesión de la web, si la petición trae una vigente. */
    public static Optional<Long> idEnSesion(HttpServletRequest peticion) {
        return Optional.ofNullable((Long) peticion.getAttribute(ID_EN_SESION));
    }

    /** La cuenta de la petición, si inició sesión. */
    public static Optional<Cuentas.Usuario> usuario(HttpServletRequest peticion) {
        return Optional.ofNullable((Cuentas.Usuario) peticion.getAttribute(ATRIBUTO));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest peticion) {
        String ruta = peticion.getRequestURI();
        // La búsqueda y los registros de la app no autentican en Django (authentication_classes = []):
        // el registro lee el token por su cuenta, porque la cuenta todavía no está vinculada.
        return !ruta.startsWith("/api/") || ruta.startsWith("/api/prestadores/buscar")
            || ruta.equals(RegistroMovilController.CLIENTE) || ruta.equals(RegistroMovilController.PRESTADOR);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
        throws ServletException, IOException {
        Optional<SesionDjango.Contenido> sesion = sesiones.leer(CsrfDjango.cookie(peticion, SesionDjango.COOKIE));
        if (sesion.isPresent()) {
            peticion.setAttribute(ID_EN_SESION, sesion.get().usuarioId());
            // BackendConSanciones.get_user(): activa y sin sanción vigente; luego el hash de la contraseña.
            Optional<Cuentas.Usuario> usuario = sesiones.usuario(sesion.get()).flatMap(cuentas::activaPorId)
                .filter(u -> cuentas.sancionVigente(u.id()).isEmpty());
            if (usuario.isPresent()) {
                String motivo = csrf.motivo(peticion);
                if (motivo != null) {
                    rechazar(respuesta, "CSRF Failed: " + motivo);
                    return;
                }
                peticion.setAttribute(ATRIBUTO, usuario.get());
                cadena.doFilter(peticion, respuesta);
                return;
            }
        }
        String cabecera = peticion.getHeader(HttpHeaders.AUTHORIZATION);
        String[] partes = cabecera == null ? new String[0] : cabecera.trim().split("\\s+");
        if (partes.length == 0 || !partes[0].equalsIgnoreCase("bearer")) {
            cadena.doFilter(peticion, respuesta);  // sin token: anónima
            return;
        }
        if (partes.length != 2 || !verificador.configurado()) {
            rechazar(respuesta, "El acceso con token no está disponible.");
            return;
        }
        Optional<String> uid = verificador.uid(partes[1]);
        if (uid.isEmpty()) {
            rechazar(respuesta, "El token no es válido o venció.");
            return;
        }
        Optional<Cuentas.Usuario> usuario = cuentas.porFirebase(uid.get());
        if (usuario.isEmpty()) {
            rechazar(respuesta, "Esta cuenta de la app aún no está vinculada.");
            return;
        }
        Optional<String> sancion = cuentas.sancionVigente(usuario.get().id());
        if (sancion.isPresent()) {
            rechazar(respuesta, sancion.get());
            return;
        }
        peticion.setAttribute(ATRIBUTO, usuario.get());
        cadena.doFilter(peticion, respuesta);
    }

    private static void rechazar(HttpServletResponse respuesta, String detalle) throws IOException {
        // DRF responde 403 (no 401) porque la sesión es la primera autenticación configurada.
        respuesta.setStatus(HttpServletResponse.SC_FORBIDDEN);
        respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        respuesta.setCharacterEncoding("UTF-8");
        respuesta.getWriter().write(JSON.writeValueAsString(Map.of("detail", detalle)));
    }
}
