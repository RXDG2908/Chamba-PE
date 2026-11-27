package pe.tecsup.chambape.ia;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.tecsup.chambape.comun.ConsultaComoDjango;

/**
 * HU-10: {@code GET /api/ia/recomendacion/?rubro=&latitud=&longitud=}, igual que {@code RecomendacionView} de
 * Django. Pública y sin autenticación (como la búsqueda), con los mismos parámetros y mensajes de error.
 */
@RestController
public class RecomendacionController {

    public static final String RUTA = "/api/ia/recomendacion";

    private final RecomendacionService recomendacion;

    public RecomendacionController(RecomendacionService recomendacion) {
        this.recomendacion = recomendacion;
    }

    @GetMapping({RUTA + "/", RUTA})
    public RecomendacionService.Recomendacion recomendar(HttpServletRequest peticion) {
        // La consulta se lee como Django y no con getParameter() de Tomcat (ver ConsultaComoDjango).
        Map<String, List<String>> consulta = ConsultaComoDjango.leer(peticion.getQueryString());
        return recomendacion.recomendar(ConsultaComoDjango.ultimo(consulta, "rubro"),
            ConsultaComoDjango.ultimo(consulta, "latitud"), ConsultaComoDjango.ultimo(consulta, "longitud"));
    }
}
