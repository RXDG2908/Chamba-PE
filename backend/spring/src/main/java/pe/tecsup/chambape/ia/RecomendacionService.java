package pe.tecsup.chambape.ia;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.tecsup.chambape.prestadores.BusquedaService;
import pe.tecsup.chambape.prestadores.BusquedaService.PrestadorDto;
import pe.tecsup.chambape.prestadores.BusquedaService.RubroDto;

/**
 * HU-10: recomendación del prestador, con la misma fórmula, orden y textos que {@code ia/recomendacion.py}
 * de Django. Contrato y ejemplos: docs/API_RECOMENDACION.md.
 *
 * <p>Puntaje de 0 a 100 sobre los prestadores que ya pasan la búsqueda: 50 % cercanía, 35 % calificación
 * (promedio bayesiano) y 15 % disponibilidad real (servicios confirmados, en camino o en servicio). Las
 * operaciones van en el mismo orden que en Python para que los decimales salgan idénticos.
 */
@Service
public class RecomendacionService {

    static final double PESO_DISTANCIA = 0.50;
    static final double PESO_CALIFICACION = 0.35;
    static final double PESO_DISPONIBILIDAD = 0.15;
    static final double DISTANCIA_REFERENCIA_KM = 10.0;
    static final double CALIFICACION_PREVIA = 3.5;
    static final int PESO_PREVIA = 3;
    static final int MINUTOS_PREPARACION = 10;
    static final double VELOCIDAD_KM_H = 20.0;
    static final int MINUTOS_POR_SERVICIO_EN_CURSO = 30;
    static final int MAXIMO_ALTERNATIVAS = 3;

    private final BusquedaService busqueda;
    private final JdbcTemplate jdbc;

    public RecomendacionService(BusquedaService busqueda, JdbcTemplate jdbc) {
        this.busqueda = busqueda;
        this.jdbc = jdbc;
    }

    /** Un prestador de la búsqueda con su puntaje, en el orden de campos de Django. */
    public record Detalle(
        long id, String nombres, String apellidos, List<RubroDto> rubros,
        double latitud, double longitud,
        @JsonProperty("distancia_km") double distanciaKm,
        @JsonProperty("calificacion_promedio") Double calificacionPromedio,
        @JsonProperty("calificaciones_total") int calificacionesTotal,
        String tipo,
        @JsonProperty("nombre_publico") String nombrePublico,
        String descripcion, String foto, boolean nuevo,
        double puntaje,
        @JsonProperty("tiempo_estimado_min") long tiempoEstimadoMin,
        @JsonProperty("servicios_en_curso") int serviciosEnCurso,
        String motivo) {
    }

    public record Recomendacion(Detalle recomendado, List<Detalle> alternativas) {
    }

    record Puntaje(double total, int enCurso, long minutosEstimados) {
    }

    private record Puntuado(PrestadorDto prestador, Puntaje puntaje) {
    }

    @Transactional
    public Recomendacion recomendar(String rubro, String latitud, String longitud) {
        // Misma validación y mismos prestadores que la búsqueda (verificados, disponibles, del rubro y con cobertura).
        List<PrestadorDto> prestadores = busqueda.buscar(rubro, latitud, longitud);
        if (prestadores.isEmpty()) {
            return new Recomendacion(null, List.of());
        }
        Map<Long, Integer> enCurso = serviciosEnCurso(prestadores);
        List<Puntuado> puntuados = new ArrayList<>();
        for (PrestadorDto p : prestadores) {
            puntuados.add(new Puntuado(p, puntuar(p, enCurso.getOrDefault(p.id(), 0))));
        }
        // Mayor puntaje primero; a igual puntaje, el más cercano y luego el ID más bajo.
        puntuados.sort(Comparator.comparingDouble((Puntuado x) -> -x.puntaje().total())
            .thenComparingDouble(x -> x.prestador().distanciaKm())
            .thenComparingLong(x -> x.prestador().id()));
        double masCercano = Collections.min(prestadores, Comparator.comparingDouble(PrestadorDto::distanciaKm))
            .distanciaKm();
        List<Detalle> detalles = puntuados.stream()
            .map(x -> detalle(x.prestador(), x.puntaje(), x.prestador().distanciaKm() == masCercano))
            .toList();
        return new Recomendacion(detalles.get(0),
            detalles.subList(1, Math.min(detalles.size(), 1 + MAXIMO_ALTERNATIVAS)));
    }

    /** Solicitudes confirmadas, en camino o en servicio de cada prestador (los que no tienen, no aparecen). */
    private Map<Long, Integer> serviciosEnCurso(List<PrestadorDto> prestadores) {
        String marcas = String.join(", ", Collections.nCopies(prestadores.size(), "?"));
        List<Object> parametros = new ArrayList<>(prestadores.stream().map(p -> (Object) p.id()).toList());
        Map<Long, Integer> enCurso = new HashMap<>();
        jdbc.query("select prestador_id, count(id) from servicios_solicitud where prestador_id in (" + marcas
                + ") and estado in ('aceptada', 'en_camino', 'en_servicio') group by prestador_id",
            fila -> {
                enCurso.put(fila.getLong(1), fila.getInt(2));
            }, parametros.toArray());
        return enCurso;
    }

    static double factorDistancia(double distanciaKm) {
        return Math.max(0.0, 1.0 - distanciaKm / DISTANCIA_REFERENCIA_KM);
    }

    /** Promedio bayesiano en estrellas (1 a 5). */
    static double calificacionAjustada(Double promedio, int total) {
        double suma = promedio != null && total != 0 ? promedio * total : 0.0;
        return (suma + CALIFICACION_PREVIA * PESO_PREVIA) / (total + PESO_PREVIA);
    }

    static double factorCalificacion(Double promedio, int total) {
        return (calificacionAjustada(promedio, total) - 1) / 4;
    }

    static double factorDisponibilidad(int enCurso) {
        return 1.0 / (1 + enCurso);
    }

    /** {@code round()} de Python a entero: mitad al par sobre el valor exacto. */
    static long minutosEstimados(double distanciaKm, int enCurso) {
        double trayecto = distanciaKm / VELOCIDAD_KM_H * 60;
        return (long) Math.rint(MINUTOS_PREPARACION + trayecto + MINUTOS_POR_SERVICIO_EN_CURSO * enCurso);
    }

    static Puntaje puntuar(PrestadorDto prestador, int enCurso) {
        double distancia = factorDistancia(prestador.distanciaKm());
        double calificacion = factorCalificacion(prestador.calificacionPromedio(), prestador.calificacionesTotal());
        double disponibilidad = factorDisponibilidad(enCurso);
        double total = 100 * (PESO_DISTANCIA * distancia + PESO_CALIFICACION * calificacion
            + PESO_DISPONIBILIDAD * disponibilidad);
        return new Puntaje(redondear(total, 1), enCurso, minutosEstimados(prestador.distanciaKm(), enCurso));
    }

    /** {@code round(valor, cifras)} de Python: sobre el valor binario exacto, mitad al par. */
    static double redondear(double valor, int cifras) {
        return new BigDecimal(valor).setScale(cifras, RoundingMode.HALF_EVEN).doubleValue();
    }

    /** {@code f'{valor:.1f}'} de Python con coma decimal: mismo redondeo que {@link #redondear}. */
    static String unDecimal(double valor) {
        return new BigDecimal(valor).setScale(1, RoundingMode.HALF_EVEN).toPlainString().replace('.', ',');
    }

    /** Explicación corta, en palabras simples, de por qué se recomienda. */
    static String motivo(PrestadorDto prestador, Puntaje puntaje, boolean esElMasCercano) {
        List<String> partes = new ArrayList<>();
        String distancia = "a " + unDecimal(prestador.distanciaKm()) + " km de ti";
        partes.add(esElMasCercano ? "Es el más cercano (" + distancia + ")" : "Está " + distancia);
        int total = prestador.calificacionesTotal();
        if (total != 0) {
            String opiniones = total == 1 ? "calificación" : "calificaciones";
            partes.add("tiene ★ " + unDecimal(prestador.calificacionPromedio()) + " con " + total + " " + opiniones);
        } else {
            partes.add("aún no tiene calificaciones");
        }
        if (puntaje.enCurso() == 0) {
            partes.add("no tiene servicios en curso");
        } else {
            String servicios = puntaje.enCurso() == 1 ? "servicio" : "servicios";
            partes.add("tiene " + puntaje.enCurso() + " " + servicios + " en curso");
        }
        String texto = String.join(", ", partes.subList(0, partes.size() - 1)) + " y " + partes.get(partes.size() - 1);
        return texto.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + texto.substring(1) + ".";
    }

    private static Detalle detalle(PrestadorDto p, Puntaje puntaje, boolean esElMasCercano) {
        return new Detalle(p.id(), p.nombres(), p.apellidos(), p.rubros(), p.latitud(), p.longitud(), p.distanciaKm(),
            p.calificacionPromedio(), p.calificacionesTotal(), p.tipo(), p.nombrePublico(), p.descripcion(), p.foto(), p.nuevo(),
            puntaje.total(), puntaje.minutosEstimados(),
            puntaje.enCurso(), motivo(p, puntaje, esElMasCercano));
    }
}
