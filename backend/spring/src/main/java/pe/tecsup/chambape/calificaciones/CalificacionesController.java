package pe.tecsup.chambape.calificaciones;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.tecsup.chambape.comun.CuerpoComoDrf;
import pe.tecsup.chambape.comun.FechasDjango;
import pe.tecsup.chambape.comun.RespuestaError;
import pe.tecsup.chambape.servicios.Solicitudes;
import pe.tecsup.chambape.servicios.SolicitudesController;
import pe.tecsup.chambape.usuarios.Cuentas;
import pe.tecsup.chambape.usuarios.FiltroFirebase;

/** HU-12: mismas rutas y respuestas que {@code /api/calificaciones/} de Django. */
@RestController
@RequestMapping("/api/calificaciones")
public class CalificacionesController {

    private final CalificacionesService servicio;
    private final Solicitudes solicitudes;

    public CalificacionesController(CalificacionesService servicio, Solicitudes solicitudes) {
        this.servicio = servicio;
        this.solicitudes = solicitudes;
    }

    /** Solicitudes que el cliente envió con su cuenta, con su calificación si ya la dio. */
    @GetMapping("/mis-servicios/")
    public List<Map<String, Object>> misServicios(HttpServletRequest peticion) {
        Cuentas.Usuario cliente = cliente(peticion);
        List<Solicitudes.Solicitud> suyas = solicitudes.delCliente(cliente.id());
        Map<Long, CalificacionesService.Calificacion> calificaciones =
            servicio.deSolicitudes(suyas.stream().map(Solicitudes.Solicitud::id).toList());
        return suyas.stream().map(s -> {
            CalificacionesService.Calificacion calificacion = calificaciones.get(s.id());
            Map<String, Object> prestador = new LinkedHashMap<>();
            prestador.put("id", s.prestadorId());
            prestador.put("nombre", s.prestadorNombres() + " " + s.prestadorApellidos());
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("id", s.id());
            cuerpo.put("prestador", prestador);
            cuerpo.put("rubro", s.rubro());
            cuerpo.put("descripcion", s.descripcion());
            cuerpo.put("estado", s.estado());
            cuerpo.put("estado_display", Solicitudes.ESTADOS.get(s.estado()));
            cuerpo.put("creada", FechasDjango.paraApi(s.creada()));
            cuerpo.put("calificacion", calificacion == null ? null : calificacion(calificacion));
            cuerpo.put("puede_calificar", "finalizada".equals(s.estado()) && calificacion == null);
            cuerpo.put("historial", SolicitudesController.historial(s));
            return cuerpo;
        }).toList();
    }

    /** El cliente califica una solicitud suya ya Finalizada, una sola vez. */
    @PostMapping("/")
    public ResponseEntity<Map<String, Object>> calificar(HttpServletRequest peticion) {
        Cuentas.Usuario cliente = cliente(peticion);
        CalificacionesService.Resultado resultado = servicio.calificar(cliente, CuerpoComoDrf.leer(peticion));
        Map<String, Object> prestador = new LinkedHashMap<>();
        prestador.put("id", resultado.prestadorId());
        prestador.put("calificacion_promedio", resultado.promedio().doubleValue());
        prestador.put("calificaciones_total", resultado.total());
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("solicitud", resultado.solicitudId());
        cuerpo.put("calificacion", calificacion(resultado.calificacion()));
        cuerpo.put("prestador", prestador);
        return ResponseEntity.status(HttpStatus.CREATED).body(cuerpo);
    }

    /** Permiso EsCliente de Django. */
    static Cuentas.Usuario cliente(HttpServletRequest peticion) {
        Cuentas.Usuario usuario = FiltroFirebase.usuario(peticion).orElseThrow(RespuestaError::sinCredenciales);
        if (!Cuentas.CLIENTE.equals(usuario.rol())) {
            throw RespuestaError.detalle(403, "Esta acción es solo para clientes.");
        }
        return usuario;
    }

    // CalificacionSerializer
    private static Map<String, Object> calificacion(CalificacionesService.Calificacion c) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("estrellas", c.estrellas());
        cuerpo.put("comentario", c.comentario());
        cuerpo.put("creada", FechasDjango.paraApi(c.creada()));
        return cuerpo;
    }
}
