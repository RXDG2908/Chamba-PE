package pe.tecsup.chambape.prestadores;

import java.util.List;
import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
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
        List<BusquedaService.PrestadorDto> resultados = busqueda.buscar(
            ultimo(peticion, "rubro"), ultimo(peticion, "latitud"), ultimo(peticion, "longitud"));
        String milisegundos = String.format(Locale.ROOT, "%.3f", (System.nanoTime() - inicio) / 1_000_000.0);
        return ResponseEntity.ok().header("X-Execution-Time-Ms", milisegundos).body(resultados);
    }

    // Si un parámetro se repite, Django usa el último valor; aquí también.
    private static String ultimo(HttpServletRequest peticion, String nombre) {
        String[] valores = peticion.getParameterValues(nombre);
        return valores == null || valores.length == 0 ? null : valores[valores.length - 1];
    }
}
