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
 * Inicio de sesión de la app con {@code Authorization: Bearer <token de Firebase>}, como
 * {@code AutenticacionFirebase} de Django. Sin esa cabecera la petición sigue como anónima; con un
 * token inválido, de una cuenta no vinculada o sancionada (HU-13) responde 403 con el motivo.
 */
@Component
public class FiltroFirebase extends OncePerRequestFilter {

    public static final String ATRIBUTO = "chambape.usuario";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final VerificadorFirebase verificador;
    private final Cuentas cuentas;

    public FiltroFirebase(VerificadorFirebase verificador, Cuentas cuentas) {
        this.verificador = verificador;
        this.cuentas = cuentas;
    }

    /** La cuenta de la petición, si inició sesión. */
    public static Optional<Cuentas.Usuario> usuario(HttpServletRequest peticion) {
        return Optional.ofNullable((Cuentas.Usuario) peticion.getAttribute(ATRIBUTO));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest peticion) {
        String ruta = peticion.getRequestURI();
        // La búsqueda de Django no autentica (authentication_classes = []): ignora el token.
        return !ruta.startsWith("/api/") || ruta.startsWith("/api/prestadores/buscar");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
        throws ServletException, IOException {
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
