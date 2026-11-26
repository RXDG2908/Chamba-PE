package pe.tecsup.chambape.servicios;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.tecsup.chambape.comun.ConsultaComoDjango;
import pe.tecsup.chambape.comun.CuerpoComoDrf;
import pe.tecsup.chambape.comun.FechasDjango;
import pe.tecsup.chambape.comun.RespuestaError;
import pe.tecsup.chambape.usuarios.Cuentas;
import pe.tecsup.chambape.usuarios.FiltroFirebase;

/** Mismas rutas y respuestas que {@code /api/servicios/solicitudes/} de Django. */
@RestController
@RequestMapping("/api/servicios/solicitudes")
public class SolicitudesController {

    private final SolicitudesService servicio;
    private final Solicitudes solicitudes;
    private final CodigoSeguimiento codigos;

    public SolicitudesController(SolicitudesService servicio, Solicitudes solicitudes, CodigoSeguimiento codigos) {
        this.servicio = servicio;
        this.solicitudes = solicitudes;
        this.codigos = codigos;
    }

    /** HU-4: el cliente (con o sin cuenta) envía una solicitud; queda Pendiente. */
    @PostMapping("/")
    public ResponseEntity<Map<String, Object>> crear(HttpServletRequest peticion) {
        long id = servicio.crear(CuerpoComoDrf.leer(peticion), FiltroFirebase.usuario(peticion));
        Solicitudes.Solicitud s = solicitudes.buscar(id).orElseThrow();
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("id", s.id());
        cuerpo.put("prestador", s.prestadorId());
        cuerpo.put("rubro", s.rubroId());
        cuerpo.put("cliente_nombre", s.clienteNombre());
        cuerpo.put("cliente_telefono", s.clienteTelefono());
        cuerpo.put("descripcion", s.descripcion());
        cuerpo.put("latitud", s.latitud().toPlainString());
        cuerpo.put("longitud", s.longitud().toPlainString());
        cuerpo.put("estado", s.estado());
        cuerpo.put("creada", FechasDjango.paraApi(s.creada()));
        // HU-11: solo se entrega a quien la crea; con él sigue el estado sin cuenta.
        cuerpo.put("codigo_seguimiento", codigos.de(s.id()));
        return ResponseEntity.status(HttpStatus.CREATED).body(cuerpo);
    }

    /**
     * HU-11: el estado y su historial. Solo la ven el cliente dueño, el prestador asignado o quien
     * tenga el código de seguimiento; para cualquier otro es 404, como si no existiera.
     */
    @GetMapping("/{id:\\d+}/")
    public ResponseEntity<Map<String, Object>> detalle(@PathVariable long id, HttpServletRequest peticion) {
        ResponseEntity.BodyBuilder privado = ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, no-store");
        Optional<Solicitudes.Solicitud> encontrada = solicitudes.buscar(id);
        if (encontrada.isEmpty()) {
            return conCache(RespuestaError.noEncontrada());
        }
        Solicitudes.Solicitud s = encontrada.get();
        Optional<Cuentas.Usuario> usuario = FiltroFirebase.usuario(peticion);
        boolean suya = usuario.map(u -> u.id() == s.prestadorUsuarioId()
            || (s.clienteId() != null && u.id() == s.clienteId())).orElse(false);
        String codigo = ConsultaComoDjango.ultimo(ConsultaComoDjango.leer(peticion.getQueryString()), "codigo");
        if (!suya && !codigos.valido(s.id(), codigo)) {
            return conCache(RespuestaError.detalle(404, "Not found."));
        }
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("id", s.id());
        cuerpo.put("prestador", s.prestadorId());
        cuerpo.put("prestador_nombres", s.prestadorNombres());
        cuerpo.put("prestador_apellidos", s.prestadorApellidos());
        cuerpo.put("rubro", s.rubro());
        cuerpo.put("estado", s.estado());
        cuerpo.put("estado_display", Solicitudes.ESTADOS.get(s.estado()));
        cuerpo.put("creada", FechasDjango.paraApi(s.creada()));
        cuerpo.put("respondida", FechasDjango.paraApi(s.respondida()));
        cuerpo.put("historial", historial(s));
        return privado.body(cuerpo);
    }

    /** Bandeja del prestador (HU-4). */
    @GetMapping("/recibidas/")
    public List<Map<String, Object>> recibidas(HttpServletRequest peticion) {
        Cuentas.Usuario prestador = prestador(peticion);
        return solicitudes.delPrestador(prestador.prestadorId()).stream().map(SolicitudesController::recibida).toList();
    }

    @GetMapping("/recibidas/{id:\\d+}/")
    public Map<String, Object> recibida(@PathVariable long id, HttpServletRequest peticion) {
        return recibida(servicio.delPrestador(id, prestador(peticion)));
    }

    @PostMapping("/{id:\\d+}/aceptar/")
    public Map<String, Object> aceptar(@PathVariable long id, HttpServletRequest peticion) {
        return recibida(servicio.responder(id, prestador(peticion), "aceptada"));
    }

    @PostMapping("/{id:\\d+}/rechazar/")
    public Map<String, Object> rechazar(@PathVariable long id, HttpServletRequest peticion) {
        return recibida(servicio.responder(id, prestador(peticion), "rechazada"));
    }

    /** HU-11: Confirmado → En camino → En servicio → Finalizada. */
    @PostMapping("/{id:\\d+}/avanzar/")
    public Map<String, Object> avanzar(@PathVariable long id, HttpServletRequest peticion) {
        Cuentas.Usuario prestador = prestador(peticion);
        servicio.delPrestador(id, prestador);  // 404 antes de leer el cuerpo, como Django
        return recibida(servicio.avanzar(id, prestador, CuerpoComoDrf.leer(peticion)));
    }

    /** Permiso EsPrestador de Django. */
    public static Cuentas.Usuario prestador(HttpServletRequest peticion) {
        Cuentas.Usuario usuario = FiltroFirebase.usuario(peticion).orElseThrow(RespuestaError::sinCredenciales);
        if (!usuario.esPrestador()) {
            throw RespuestaError.detalle(403, "Esta acción es solo para prestadores.");
        }
        return usuario;
    }

    // SolicitudRecibidaSerializer: incluye el contacto del cliente para atenderlo.
    private static Map<String, Object> recibida(Solicitudes.Solicitud s) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("id", s.id());
        cuerpo.put("rubro", s.rubro());
        cuerpo.put("cliente_nombre", s.clienteNombre());
        cuerpo.put("cliente_telefono", s.clienteTelefono());
        cuerpo.put("descripcion", s.descripcion());
        cuerpo.put("latitud", s.latitud().toPlainString());
        cuerpo.put("longitud", s.longitud().toPlainString());
        cuerpo.put("estado", s.estado());
        cuerpo.put("estado_display", Solicitudes.ESTADOS.get(s.estado()));
        cuerpo.put("siguiente_estado", Solicitudes.SEGUIMIENTO.get(s.estado()));
        cuerpo.put("historial", historial(s));
        cuerpo.put("creada", FechasDjango.paraApi(s.creada()));
        cuerpo.put("respondida", FechasDjango.paraApi(s.respondida()));
        return cuerpo;
    }

    private static List<Map<String, Object>> historial(Solicitudes.Solicitud s) {
        List<Map<String, Object>> etapas = new ArrayList<>();
        for (Solicitudes.Cambio cambio : s.historial()) {
            Map<String, Object> etapa = new LinkedHashMap<>();
            etapa.put("estado", cambio.estado());
            etapa.put("estado_display", Solicitudes.ESTADOS.get(cambio.estado()));
            etapa.put("fecha", FechasDjango.paraApi(cambio.fecha()));
            etapas.add(etapa);
        }
        return etapas;
    }

    private static ResponseEntity<Map<String, Object>> conCache(RespuestaError error) {
        return ResponseEntity.status(error.getEstado()).header(HttpHeaders.CACHE_CONTROL, "private, no-store")
            .body(error.getCuerpo());
    }
}
