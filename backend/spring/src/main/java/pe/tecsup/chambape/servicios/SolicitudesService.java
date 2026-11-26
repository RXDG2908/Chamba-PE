package pe.tecsup.chambape.servicios;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;

import pe.tecsup.chambape.comun.ComoDrf;
import pe.tecsup.chambape.comun.ErrorValidacion;
import pe.tecsup.chambape.comun.FechasDjango;
import pe.tecsup.chambape.comun.RespuestaError;
import pe.tecsup.chambape.prestadores.Distancias;
import pe.tecsup.chambape.usuarios.Cuentas;

/**
 * Solicitudes (HU-4, HU-8 y HU-11) con las mismas reglas y mensajes que {@code servicios/views.py}
 * y {@code servicios/serializers.py} de Django.
 */
@Service
public class SolicitudesService {

    private static final Pattern TELEFONO = Pattern.compile("[0-9]{9}");

    private final Solicitudes solicitudes;
    private final JdbcTemplate jdbc;
    private final Cuentas cuentas;
    private final Notificador notificador;

    public SolicitudesService(Solicitudes solicitudes, JdbcTemplate jdbc, Cuentas cuentas, Notificador notificador) {
        this.solicitudes = solicitudes;
        this.jdbc = jdbc;
        this.cuentas = cuentas;
        this.notificador = notificador;
    }

    private record PrestadorDestino(long id, long usuarioId, String estado, boolean disponible,
                                    double latitud, double longitud, int radio) {
    }

    /** POST /api/servicios/solicitudes/: devuelve el id de la solicitud creada. */
    @Transactional
    public long crear(JsonNode datos, Optional<Cuentas.Usuario> usuario) {
        if (!datos.isObject()) {
            throw ComoDrf.noEsDiccionario(datos);
        }
        Map<String, List<String>> errores = new LinkedHashMap<>();
        PrestadorDestino prestador = ComoDrf.campo(datos, "prestador", errores, valor -> {
            long id = ComoDrf.clavePrimaria(valor);
            return prestador(id).orElseThrow(() -> new ComoDrf.Invalido(ComoDrf.noExiste(valor)));
        });
        Long rubro = ComoDrf.campo(datos, "rubro", errores, valor -> {
            long id = ComoDrf.clavePrimaria(valor);
            Integer existe = jdbc.queryForObject("select count(*) from prestadores_rubro where id = ?", Integer.class, id);
            if (existe == null || existe == 0) {
                throw new ComoDrf.Invalido(ComoDrf.noExiste(valor));
            }
            return id;
        });
        String nombre = ComoDrf.campo(datos, "cliente_nombre", errores, valor -> {
            // Misma regla que validate_cliente_nombre → usuarios.validadores.nombre_de_persona.
            return ComoDrf.nombreDePersona(ComoDrf.texto(valor, 100, "Ingresa un nombre con al menos una letra."));
        });
        String telefono = ComoDrf.campo(datos, "cliente_telefono", errores, valor -> {
            String texto = ComoDrf.texto(valor, null, "Ingresa exactamente 9 dígitos del 0 al 9, sin +51 ni espacios.");
            if (!TELEFONO.matcher(texto).matches()) {
                throw new ComoDrf.Invalido("Ingresa exactamente 9 dígitos del 0 al 9, sin +51 ni espacios.");
            }
            return texto;
        });
        String descripcion = ComoDrf.campo(datos, "descripcion", errores, valor -> {
            String texto = ComoDrf.texto(valor, 500, "Describe el trabajo; no lo dejes en blanco.");
            if (ComoDrf.soloEspacios(texto)) {
                throw new ComoDrf.Invalido("Describe el trabajo; no uses solo espacios.");
            }
            return texto;
        });
        BigDecimal latitud = ComoDrf.campo(datos, "latitud", errores, valor -> ComoDrf.coordenada(valor, 90));
        BigDecimal longitud = ComoDrf.campo(datos, "longitud", errores, valor -> ComoDrf.coordenada(valor, 180));
        if (!errores.isEmpty()) {
            throw new ErrorValidacion(errores);
        }

        // Como SolicitudSerializer.validate(): el primer problema que encuentra.
        if (!"verificado".equals(prestador.estado())) {
            throw error("prestador", "El prestador aún no está verificado.");
        }
        // HU-13: un prestador suspendido o bloqueado tampoco recibe solicitudes.
        if (!prestador.disponible() || cuentas.sancionVigente(prestador.usuarioId()).isPresent()) {
            throw error("prestador", "El prestador no está disponible.");
        }
        Integer ofrece = jdbc.queryForObject(
            "select count(*) from prestadores_prestador_rubros where prestador_id = ? and rubro_id = ?",
            Integer.class, prestador.id(), rubro);
        if (ofrece == null || ofrece == 0) {
            throw error("rubro", "El prestador no ofrece ese rubro.");
        }
        double distancia = Distancias.km(prestador.latitud(), prestador.longitud(),
            latitud.doubleValue(), longitud.doubleValue());
        if (distancia > prestador.radio() + 1e-9) {
            throw error("prestador", "Tu ubicación está fuera del área de cobertura del prestador.");
        }

        // Con sesión de cliente la solicitud queda a su nombre (HU-12: solo él la califica).
        Long cliente = usuario.filter(u -> Cuentas.CLIENTE.equals(u.rol())).map(Cuentas.Usuario::id).orElse(null);
        long id = solicitudes.crear(prestador.id(), rubro, cliente, nombre, telefono, descripcion, latitud, longitud,
            FechasDjango.ahora());
        long destinatario = prestador.usuarioId();
        // HU-8: se avisa al prestador recién cuando la solicitud quedó guardada.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notificador.solicitudNueva(id, destinatario);
            }
        });
        return id;
    }

    /** POST .../aceptar/ y .../rechazar/: solo el prestador dueño y solo si sigue Pendiente. */
    @Transactional
    public Solicitudes.Solicitud responder(long id, Cuentas.Usuario prestador, String nuevoEstado) {
        Solicitudes.Solicitud solicitud = delPrestador(id, prestador);
        if (!solicitudes.cambiarEstado(id, "pendiente", nuevoEstado, true, FechasDjango.ahora())) {
            String actual = solicitudes.buscar(id).orElseThrow().estado();
            throw new RespuestaError(409, Map.of("estado",
                "La solicitud ya fue " + Solicitudes.ESTADOS.get(actual).toLowerCase() + "."));
        }
        return solicitudes.buscar(solicitud.id()).orElseThrow();
    }

    /** POST .../avanzar/ (HU-11): un paso a la vez, en orden. */
    @Transactional
    public Solicitudes.Solicitud avanzar(long id, Cuentas.Usuario prestador, JsonNode datos) {
        Solicitudes.Solicitud solicitud = delPrestador(id, prestador);
        JsonNode valor = datos.isObject() ? datos.get("estado") : null;
        String destino = valor != null && valor.isString() ? valor.asString() : null;
        if (destino == null || !Solicitudes.ESTADOS.containsKey(destino)) {
            throw new RespuestaError(400, Map.of("estado", "Indica un estado válido."));
        }
        String actual = solicitud.estado();
        if (!destino.equals(Solicitudes.SEGUIMIENTO.get(actual))
            || !solicitudes.cambiarEstado(id, actual, destino, false, FechasDjango.ahora())) {
            throw conflicto(solicitudes.buscar(id).orElseThrow());
        }
        return solicitudes.buscar(id).orElseThrow();
    }

    public Solicitudes.Solicitud delPrestador(long id, Cuentas.Usuario prestador) {
        return solicitudes.buscarDelPrestador(id, prestador.prestadorId()).orElseThrow(RespuestaError::noEncontrada);
    }

    private static RespuestaError conflicto(Solicitudes.Solicitud solicitud) {
        String actual = solicitud.estado();
        String siguiente = Solicitudes.SEGUIMIENTO.get(actual);
        String detalle;
        if ("pendiente".equals(actual)) {
            detalle = "Primero acepta la solicitud para empezar el seguimiento.";
        } else if (siguiente != null) {
            detalle = "La solicitud está «" + Solicitudes.ESTADOS.get(actual) + "»; solo puede pasar a «"
                + Solicitudes.ESTADOS.get(siguiente) + "».";
        } else {
            detalle = "La solicitud está «" + Solicitudes.ESTADOS.get(actual) + "» y ya no se puede avanzar.";
        }
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("estado", detalle);
        cuerpo.put("estado_actual", actual);
        return new RespuestaError(409, cuerpo);
    }

    private Optional<PrestadorDestino> prestador(long id) {
        return jdbc.query("""
            select id, usuario_id, estado, disponible, latitud, longitud, radio_cobertura_km
            from prestadores_prestador where id = ?""",
            (fila, n) -> new PrestadorDestino(fila.getLong("id"), fila.getLong("usuario_id"), fila.getString("estado"),
                fila.getBoolean("disponible"), Solicitudes.decimal(fila.getObject("latitud")).doubleValue(),
                Solicitudes.decimal(fila.getObject("longitud")).doubleValue(), fila.getInt("radio_cobertura_km")),
            id).stream().findFirst();
    }

    private static ErrorValidacion error(String campo, String mensaje) {
        return new ErrorValidacion(Map.of(campo, List.of(mensaje)));
    }
}
