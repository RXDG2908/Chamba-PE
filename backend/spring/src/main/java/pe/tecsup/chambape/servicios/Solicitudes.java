package pe.tecsup.chambape.servicios;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;

import pe.tecsup.chambape.comun.FechasDjango;

/**
 * Tablas de Django {@code servicios_solicitud} y {@code servicios_cambioestado}. Spring escribe
 * filas con los mismos formatos que Django (fechas en UTC, coordenadas con 6 decimales), así que
 * Django y Spring leen lo que escribe el otro.
 */
@Component
public class Solicitudes {

    // Estados y cómo los muestra Django (HU-4 y HU-11).
    public static final Map<String, String> ESTADOS = Map.of(
        "pendiente", "Pendiente", "aceptada", "Confirmado", "rechazada", "Rechazada",
        "en_camino", "En camino", "en_servicio", "En servicio", "finalizada", "Finalizada");
    // HU-11: el prestador avanza solo en este orden.
    public static final Map<String, String> SEGUIMIENTO = Map.of(
        "aceptada", "en_camino", "en_camino", "en_servicio", "en_servicio", "finalizada");

    private final JdbcTemplate jdbc;
    private final FechasDjango fechas;

    public Solicitudes(JdbcTemplate jdbc, FechasDjango fechas) {
        this.jdbc = jdbc;
        this.fechas = fechas;
    }

    public record Cambio(String estado, Instant fecha) {
    }

    public record Solicitud(
        long id, long prestadorId, long prestadorUsuarioId, String prestadorNombres, String prestadorApellidos,
        long rubroId, String rubro, Long clienteId, String clienteNombre, String clienteTelefono,
        String descripcion, BigDecimal latitud, BigDecimal longitud, String estado, Instant creada,
        Instant respondida, List<Cambio> historial) {
    }

    private static final String SELECT = """
        select s.id, s.prestador_id, p.usuario_id as prestador_usuario, p.nombres, p.apellidos, s.rubro_id,
               r.nombre as rubro, s.cliente_id, s.cliente_nombre, s.cliente_telefono, s.descripcion,
               s.latitud, s.longitud, s.estado, s.creada, s.respondida
        from servicios_solicitud s
        join prestadores_prestador p on p.id = s.prestador_id
        join prestadores_rubro r on r.id = s.rubro_id
        """;

    public Optional<Solicitud> buscar(long id) {
        List<Solicitud> filas = jdbc.query(SELECT + " where s.id = ?", this::fila, id);
        return filas.isEmpty() ? Optional.empty() : Optional.of(conHistorial(filas).get(0));
    }

    public Optional<Solicitud> buscarDelPrestador(long id, long prestadorId) {
        return buscar(id).filter(s -> s.prestadorId() == prestadorId);
    }

    /** Bandeja del prestador: más recientes primero, como {@code ordering = ['-creada']}. */
    public List<Solicitud> delPrestador(long prestadorId) {
        return conHistorial(jdbc.query(SELECT + " where s.prestador_id = ? order by s.creada desc", this::fila, prestadorId));
    }

    /** HU-12: las que el cliente envió con su cuenta, más recientes primero. */
    public List<Solicitud> delCliente(long clienteId) {
        return conHistorial(jdbc.query(SELECT + " where s.cliente_id = ? order by s.creada desc", this::fila, clienteId));
    }

    public long crear(long prestadorId, long rubroId, Long clienteId, String nombre, String telefono,
                      String descripcion, BigDecimal latitud, BigDecimal longitud, Instant creada) {
        GeneratedKeyHolder llave = new GeneratedKeyHolder();
        jdbc.update(conexion -> {
            PreparedStatement sentencia = conexion.prepareStatement("""
                insert into servicios_solicitud (prestador_id, rubro_id, cliente_id, cliente_nombre, cliente_telefono,
                  descripcion, latitud, longitud, estado, creada, respondida)
                values (?, ?, ?, ?, ?, ?, ?, ?, 'pendiente', ?, null)""", Statement.RETURN_GENERATED_KEYS);
            sentencia.setLong(1, prestadorId);
            sentencia.setLong(2, rubroId);
            sentencia.setObject(3, clienteId);
            sentencia.setString(4, nombre);
            sentencia.setString(5, telefono);
            sentencia.setString(6, descripcion);
            // Como Django en SQLite: el decimal se guarda como texto (la columna lo vuelve número).
            sentencia.setString(7, latitud.toPlainString());
            sentencia.setString(8, longitud.toPlainString());
            sentencia.setObject(9, fechas.parametro(creada));
            return sentencia;
        }, llave);
        Map<String, Object> claves = llave.getKeyList().get(0);
        Object id = claves.containsKey("id") ? claves.get("id") : claves.values().iterator().next();
        return ((Number) id).longValue();
    }

    /** Cambia el estado solo si sigue en {@code desde} (dos clics o pestañas a la vez). */
    public boolean cambiarEstado(long id, String desde, String hacia, boolean marcarRespondida, Instant fecha) {
        Object cuando = fechas.parametro(fecha);
        int filas = marcarRespondida
            ? jdbc.update("update servicios_solicitud set estado = ?, respondida = ? where id = ? and estado = ?",
                hacia, cuando, id, desde)
            : jdbc.update("update servicios_solicitud set estado = ? where id = ? and estado = ?", hacia, id, desde);
        if (filas == 0) {
            return false;
        }
        jdbc.update("insert into servicios_cambioestado (solicitud_id, estado, fecha) values (?, ?, ?)", id, hacia, cuando);
        return true;
    }

    private Solicitud fila(ResultSet fila, int numero) throws SQLException {
        return new Solicitud(fila.getLong("id"), fila.getLong("prestador_id"), fila.getLong("prestador_usuario"),
            fila.getString("nombres"), fila.getString("apellidos"), fila.getLong("rubro_id"), fila.getString("rubro"),
            fila.getObject("cliente_id") == null ? null : fila.getLong("cliente_id"),
            fila.getString("cliente_nombre"), fila.getString("cliente_telefono"), fila.getString("descripcion"),
            decimal(fila.getObject("latitud")), decimal(fila.getObject("longitud")), fila.getString("estado"),
            FechasDjango.leer(fila.getObject("creada")), FechasDjango.leer(fila.getObject("respondida")), List.of());
    }

    private List<Solicitud> conHistorial(List<Solicitud> solicitudes) {
        if (solicitudes.isEmpty()) {
            return solicitudes;
        }
        Map<Long, List<Cambio>> porSolicitud = new LinkedHashMap<>();
        solicitudes.forEach(s -> porSolicitud.put(s.id(), new ArrayList<>()));
        String marcas = String.join(",", java.util.Collections.nCopies(solicitudes.size(), "?"));
        jdbc.query("select solicitud_id, estado, fecha from servicios_cambioestado where solicitud_id in (" + marcas
                + ") order by fecha, id",
            (ResultSet fila) -> {
                porSolicitud.get(fila.getLong("solicitud_id"))
                    .add(new Cambio(fila.getString("estado"), FechasDjango.leer(fila.getObject("fecha"))));
            },
            porSolicitud.keySet().toArray());
        return solicitudes.stream().map(s -> new Solicitud(s.id(), s.prestadorId(), s.prestadorUsuarioId(),
            s.prestadorNombres(), s.prestadorApellidos(), s.rubroId(), s.rubro(), s.clienteId(), s.clienteNombre(),
            s.clienteTelefono(), s.descripcion(), s.latitud(), s.longitud(), s.estado(), s.creada(), s.respondida(),
            porSolicitud.get(s.id()))).toList();
    }

    /**
     * Como el conversor de DecimalField de Django en SQLite: el número guardado (double) se lleva a
     * 15 cifras significativas y luego a 6 decimales.
     */
    static BigDecimal decimal(Object valor) {
        if (valor == null) {
            return null;
        }
        BigDecimal numero = valor instanceof BigDecimal exacto ? exacto
            : new BigDecimal(((Number) valor).doubleValue()).round(new MathContext(15, RoundingMode.HALF_EVEN));
        return numero.setScale(6, RoundingMode.HALF_EVEN);
    }
}
