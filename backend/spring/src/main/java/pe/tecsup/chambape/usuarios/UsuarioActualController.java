package pe.tecsup.chambape.usuarios;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.tecsup.chambape.comun.RespuestaError;

/**
 * {@code GET /api/usuarios/yo/}, como {@code UsuarioActualView} de Django: quién hizo la petición y con
 * qué rol. La app lee de aquí su perfil (rol y prestador) con el token de Firebase; antes lo leía de
 * Firestore. Sin cuenta, 403; una cuenta sin vincular o sancionada ya la rechaza {@link FiltroFirebase}
 * con su {@code codigo}.
 */
@RestController
public class UsuarioActualController {

    private final RegistroMovil registro;

    public UsuarioActualController(RegistroMovil registro) {
        this.registro = registro;
    }

    @GetMapping("/api/usuarios/yo/")
    public Map<String, Object> yo(HttpServletRequest peticion) {
        Cuentas.Usuario usuario = FiltroFirebase.usuario(peticion).orElseThrow(RespuestaError::sinCredenciales);
        return registro.usuarioActual(usuario.id());
    }
}
