package pe.tecsup.chambape.calificaciones;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import pe.tecsup.chambape.comun.ComoDrf;
import pe.tecsup.chambape.comun.ErrorValidacion;
import pe.tecsup.chambape.comun.FechasDjango;
import pe.tecsup.chambape.comun.PythonRepr;
import pe.tecsup.chambape.comun.RespuestaError;
import pe.tecsup.chambape.servicios.Solicitudes;
import pe.tecsup.chambape.usuarios.Cuentas;

/**
 * Calificaciones (HU-12) con las reglas y mensajes de {@code calificaciones/serializers.py} y
 * {@code calificaciones/servicio.py} de Django: una por solicitud Finalizada, y el promedio del
 * prestador se recalcula en la misma transacción.
 */
@Service
public class CalificacionesService {

    private static final String ESTRELLAS = "Elige de 1 a 5 estrellas.";

    private final JdbcTemplate jdbc;
    private final FechasDjango fechas;
    private final Solicitudes solicitudes;

    public CalificacionesService(JdbcTemplate jdbc, FechasDjango fechas, Solicitudes solicitudes) {
        this.jdbc = jdbc;
        this.fechas = fechas;
        this.solicitudes = solicitudes;
    }

    public record Calificacion(int estrellas, String comentario, Instant creada) {
    }

    public record Resultado(long solicitudId, Calificacion calificacion, long prestadorId, BigDecimal promedio, int total) {
    }

    /** Calificación de cada solicitud que ya la tiene. */
    public Map<Long, Calificacion> deSolicitudes(List<Long> ids) {
        Map<Long, Calificacion> porSolicitud = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return porSolicitud;
        }
        String marcas = String.join(",", Collections.nCopies(ids.size(), "?"));
        jdbc.query("select solicitud_id, estrellas, comentario, creada from calificaciones_calificacion"
                + " where solicitud_id in (" + marcas + ")",
            (java.sql.ResultSet fila) -> {
                porSolicitud.put(fila.getLong("solicitud_id"), new Calificacion(fila.getInt("estrellas"),
                    fila.getString("comentario"), FechasDjango.leer(fila.getObject("creada"))));
            }, ids.toArray());
        return porSolicitud;
    }

    /** POST /api/calificaciones/ */
    @Transactional
    public Resultado calificar(Cuentas.Usuario cliente, JsonNode datos) {
        if (!datos.isObject()) {
            throw ComoDrf.noEsDiccionario(datos);
        }
        Map<String, List<String>> errores = new LinkedHashMap<>();
        Long solicitud = ComoDrf.campoConMensaje(datos, "solicitud", "Indica qué solicitud calificas.", errores,
            valor -> enteroEstricto(valor, "Indica el número de la solicitud.", "Ensure this value is greater than or equal to 1.", null, null));
        Long estrellas = ComoDrf.campoConMensaje(datos, "estrellas", ESTRELLAS, errores,
            valor -> enteroEstricto(valor, ESTRELLAS, ESTRELLAS, 5L, ESTRELLAS));
        String comentario = ComoDrf.opcional(datos, "comentario", "", errores,
            valor -> ComoDrf.textoRecortado(valor, 500, true, "El comentario admite hasta 500 caracteres.", List.of()));
        if (!errores.isEmpty()) {
            throw new ErrorValidacion(errores);
        }

        // Una solicitud de otro cliente, o anónima, se responde igual que una inexistente.
        Solicitudes.Solicitud s = solicitudes.buscar(solicitud)
            .filter(encontrada -> encontrada.clienteId() != null && encontrada.clienteId() == cliente.id())
            .orElseThrow(RespuestaError::noEncontrada);
        if (!"finalizada".equals(s.estado())) {
            throw RespuestaError.detalle(409, "Solo puedes calificar un servicio Finalizado.");
        }
        Integer ya = jdbc.queryForObject("select count(*) from calificaciones_calificacion where solicitud_id = ?",
            Integer.class, s.id());
        if (ya != null && ya > 0) {
            throw RespuestaError.detalle(409, "Ya calificaste este servicio.");
        }
        Instant ahora = FechasDjango.ahora();
        try {
            jdbc.update("""
                insert into calificaciones_calificacion (estrellas, comentario, creada, cliente_id, prestador_id, solicitud_id)
                values (?, ?, ?, ?, ?, ?)""",
                estrellas.intValue(), comentario, fechas.parametro(ahora), cliente.id(), s.prestadorId(), s.id());
        } catch (DataIntegrityViolationException repetida) {
            // Dos envíos a la vez: la restricción única de solicitud deja pasar uno solo.
            throw RespuestaError.detalle(409, "Ya calificaste este servicio.");
        }

        // actualizar_promedio(): Decimal(str(promedio)).quantize(Decimal('0.01'), ROUND_HALF_UP).
        Map<String, Object> resumen = jdbc.queryForMap(
            "select avg(estrellas) as promedio, count(id) as total from calificaciones_calificacion where prestador_id = ?",
            s.prestadorId());
        double promedioReal = ((Number) resumen.get("promedio")).doubleValue();
        BigDecimal promedio = PythonRepr.decimalDeFlotante(promedioReal).setScale(2, RoundingMode.HALF_UP);
        int total = ((Number) resumen.get("total")).intValue();
        jdbc.update("update prestadores_prestador set calificacion_promedio = ?, calificaciones_total = ? where id = ?",
            promedio.toPlainString(), total, s.prestadorId());
        return new Resultado(s.id(), new Calificacion(estrellas.intValue(), comentario, ahora), s.prestadorId(),
            promedio, total);
    }

    // EnteroEstricto de Django: solo enteros JSON (rechaza "5", 4.5, true y listas), con min/max de IntegerField.
    private static long enteroEstricto(JsonNode valor, String invalido, String menor, Long maximo, String mayor)
        throws ComoDrf.Invalido {
        if (!valor.isIntegralNumber()) {
            throw new ComoDrf.Invalido(invalido);
        }
        java.math.BigInteger numero = valor.bigIntegerValue();
        if (maximo != null && numero.compareTo(java.math.BigInteger.valueOf(maximo)) > 0) {
            throw new ComoDrf.Invalido(mayor);
        }
        if (numero.signum() <= 0) {
            throw new ComoDrf.Invalido(menor);
        }
        // Ningún id llega a tanto: se busca uno que no existe (404), como en Django.
        return numero.bitLength() > 63 ? -1 : numero.longValueExact();
    }
}
