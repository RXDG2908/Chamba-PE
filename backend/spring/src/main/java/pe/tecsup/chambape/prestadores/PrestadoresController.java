package pe.tecsup.chambape.prestadores;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import pe.tecsup.chambape.comun.ConsultaComoDjango;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Mismas rutas y respuestas que {@code /api/prestadores/} de Django. Son públicas. */
@RestController
@RequestMapping("/api/prestadores")
public class PrestadoresController {

    private final BusquedaService busqueda;

    public PrestadoresController(BusquedaService busqueda) {
        this.busqueda = busqueda;
    }

    @GetMapping({"/rubros/", "/rubros"})
    public List<BusquedaService.RubroDto> rubros() {
        return busqueda.rubros();
    }

    @GetMapping({"/buscar/", "/buscar"})
    public ResponseEntity<List<BusquedaService.PrestadorDto>> buscar(HttpServletRequest peticion) {
        long inicio = System.nanoTime();
        // La consulta se lee como Django y no con getParameter() de Tomcat (ver ConsultaComoDjango).
        Map<String, List<String>> consulta = ConsultaComoDjango.leer(peticion.getQueryString());
        List<BusquedaService.PrestadorDto> resultados = busqueda.buscar(ConsultaComoDjango.ultimo(consulta, "rubro"),
            ConsultaComoDjango.ultimo(consulta, "latitud"), ConsultaComoDjango.ultimo(consulta, "longitud"));
        String milisegundos = String.format(Locale.ROOT, "%.3f", (System.nanoTime() - inicio) / 1_000_000.0);
        return ResponseEntity.ok().header("X-Execution-Time-Ms", milisegundos).body(resultados);
    }
}
