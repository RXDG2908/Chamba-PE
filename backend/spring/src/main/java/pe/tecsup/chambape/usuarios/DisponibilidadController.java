package pe.tecsup.chambape.usuarios;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import pe.tecsup.chambape.comun.ComoDrf;
import pe.tecsup.chambape.comun.CuerpoComoDrf;
import pe.tecsup.chambape.comun.ErrorValidacion;
import pe.tecsup.chambape.servicios.SolicitudesController;

/** HU-6 (T6.1): el prestador consulta y cambia su disponibilidad, como {@code DisponibilidadView}. */
@RestController
@RequestMapping("/api/usuarios/disponibilidad")
public class DisponibilidadController {

    private final JdbcTemplate jdbc;

    public DisponibilidadController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/")
    public Map<String, Object> consultar(HttpServletRequest peticion) {
        return estado(SolicitudesController.prestador(peticion).prestadorId());
    }

    @PatchMapping("/")
    @Transactional
    public Map<String, Object> cambiar(HttpServletRequest peticion) {
        long prestador = SolicitudesController.prestador(peticion).prestadorId();
        JsonNode datos = CuerpoComoDrf.leer(peticion);
        if (!datos.isObject()) {
            throw new ErrorValidacion(Map.of("non_field_errors",
                List.of("Invalid data. Expected a dictionary, but got " + (datos.isArray() ? "list" : "str") + ".")));
        }
        JsonNode valor = datos.get("disponible");
        boolean disponible;
        try {
            if (valor == null) {
                throw new ComoDrf.Invalido(ComoDrf.REQUERIDO);
            }
            if (valor.isNull()) {
                throw new ComoDrf.Invalido(ComoDrf.NULO);
            }
            disponible = ComoDrf.booleano(valor);
        } catch (ComoDrf.Invalido invalido) {
            throw new ErrorValidacion(Map.of("disponible", List.of(invalido.getMessage())));
        }
        jdbc.update("update prestadores_prestador set disponible = ? where id = ?", disponible, prestador);
        return estado(prestador);
    }

    private Map<String, Object> estado(long prestador) {
        return jdbc.queryForObject("select disponible, estado from prestadores_prestador where id = ?", (fila, n) -> {
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("disponible", fila.getBoolean("disponible"));
            cuerpo.put("estado", fila.getString("estado"));
            return cuerpo;
        }, prestador);
    }
}
