package pe.tecsup.chambape.prestadores;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.tecsup.chambape.comun.ErrorValidacion;
import pe.tecsup.chambape.comun.NumerosComoPython;

/**
 * Búsqueda de prestadores (HU-3, HU-7 y HU-9), con las mismas reglas que
 * {@code BuscarPrestadoresView} de Django. Contrato: docs/API_BUSQUEDA.md.
 */
@Service
public class BusquedaService {

    private static final int MAXIMO_TEXTO = 1000;
    private static final BigInteger MAXIMO_ID = BigInteger.valueOf(Long.MAX_VALUE);
    // Tolerancia de 1 micrómetro en el borde del radio, como en Django.
    private static final double TOLERANCIA_KM = 1e-9;

    private final PrestadorRepository prestadores;
    private final RubroRepository rubros;

    public BusquedaService(PrestadorRepository prestadores, RubroRepository rubros) {
        this.prestadores = prestadores;
        this.rubros = rubros;
    }

    public record RubroDto(long id, String nombre) {
        static RubroDto de(Rubro rubro) {
            return new RubroDto(rubro.getId(), rubro.getNombre());
        }
    }

    public record PrestadorDto(
        long id, String nombres, String apellidos, List<RubroDto> rubros,
        double latitud, double longitud,
        @com.fasterxml.jackson.annotation.JsonProperty("distancia_km") double distanciaKm,
        // HU-12: null mientras no tenga calificaciones, igual que Django.
        @com.fasterxml.jackson.annotation.JsonProperty("calificacion_promedio") Double calificacionPromedio,
        @com.fasterxml.jackson.annotation.JsonProperty("calificaciones_total") int calificacionesTotal) {
    }

    private record Parametros(long rubro, double latitud, double longitud) {
    }

    // Sin readOnly=true: el driver de SQLite no deja cambiar ese flag con la conexión abierta;
    // la base ya se abre en solo lectura (open_mode=1).
    @Transactional
    public List<RubroDto> rubros() {
        return rubros.findAllByOrderByNombreAsc().stream().map(RubroDto::de).toList();
    }

    @Transactional
    public List<PrestadorDto> buscar(String rubro, String latitud, String longitud) {
        Parametros datos = validar(rubro, latitud, longitud);
        List<PrestadorDto> resultados = new ArrayList<>();
        for (Prestador prestador : prestadores.candidatos(datos.rubro(), Prestador.VERIFICADO)) {
            double distancia = Distancias.km(
                prestador.getLatitud().doubleValue(), prestador.getLongitud().doubleValue(),
                datos.latitud(), datos.longitud());
            double radio = prestador.getRadioCoberturaKm();
            // El límite es inclusivo.
            if (distancia <= radio || Math.abs(distancia - radio) <= TOLERANCIA_KM) {
                resultados.add(new PrestadorDto(
                    prestador.getId(), prestador.getNombres(), prestador.getApellidos(),
                    prestador.getRubros().stream().map(RubroDto::de).toList(),
                    // Solo la salida pública se redondea; el cálculo usa las coordenadas originales.
                    redondear3(prestador.getLatitud().doubleValue()),
                    redondear3(prestador.getLongitud().doubleValue()),
                    distancia,
                    prestador.getCalificacionPromedio() == null ? null : prestador.getCalificacionPromedio().doubleValue(),
                    prestador.getCalificacionesTotal()));
            }
        }
        // Orden estable del más cercano al más lejano (T3.3).
        resultados.sort(Comparator.comparingDouble(PrestadorDto::distanciaKm));
        return resultados;
    }

    private Parametros validar(String rubroTexto, String latitudTexto, String longitudTexto) {
        Map<String, List<String>> errores = new LinkedHashMap<>();
        Long rubro = validarRubro(rubroTexto, errores);
        Double latitud = validarCoordenada("latitud", latitudTexto, 90, errores);
        Double longitud = validarCoordenada("longitud", longitudTexto, 180, errores);
        if (!errores.isEmpty()) {
            throw new ErrorValidacion(errores);
        }
        return new Parametros(rubro, latitud, longitud);
    }

    private Long validarRubro(String texto, Map<String, List<String>> errores) {
        String error;
        BigInteger valor = null;
        if (texto == null) {
            error = "El parámetro rubro es obligatorio.";
        } else if (texto.length() > MAXIMO_TEXTO) {
            error = "El ID del rubro es demasiado largo.";
        } else if ((valor = NumerosComoPython.entero(texto)) == null) {
            error = "El rubro debe ser un ID entero válido.";
        } else if (valor.signum() < 1) {
            error = "El rubro debe ser un ID entero positivo.";
        } else if (valor.compareTo(MAXIMO_ID) > 0 || !rubros.existsById(valor.longValue())) {
            error = "No existe un rubro con ese ID.";
        } else {
            return valor.longValue();
        }
        errores.put("rubro", List.of(error));
        return null;
    }

    private Double validarCoordenada(String campo, String texto, int limite, Map<String, List<String>> errores) {
        String error;
        Double valor = null;
        if (texto == null) {
            error = "Este parámetro es obligatorio.";
        } else if (texto.length() > MAXIMO_TEXTO) {
            error = "String value too large.";
        } else if ((valor = NumerosComoPython.real(texto)) == null || !Double.isFinite(valor)) {
            // FloatField de DRF ya rechaza nan e inf como «inválido», antes del chequeo de «finita».
            error = "Ingresa una coordenada numérica válida.";
        } else if (valor < -limite || valor > limite) {
            error = "La coordenada debe estar entre " + -limite + " y " + limite + " grados.";
        } else {
            return valor;
        }
        errores.put(campo, List.of(error));
        return null;
    }

    /** Como {@code round(valor, 3)} de Python: sobre el valor binario exacto, mitad al par. */
    static double redondear3(double valor) {
        return new BigDecimal(valor).setScale(3, RoundingMode.HALF_EVEN).doubleValue();
    }
}
