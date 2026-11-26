package pe.tecsup.chambape.comun;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Fechas como las guarda y las muestra Django (USE_TZ = True, TIME_ZONE = 'UTC').
 * En SQLite Django guarda texto «2026-11-25 21:47:01.123456» en UTC; en PostgreSQL, timestamptz.
 * En la API, DRF las escribe en ISO 8601 con «Z» y microsegundos solo si los hay.
 */
@Component
public class FechasDjango {

    private static final DateTimeFormatter TEXTO_SQLITE = new DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd HH:mm:ss")
        .optionalStart().appendFraction(ChronoField.MICRO_OF_SECOND, 0, 6, true).optionalEnd()
        .toFormatter();

    private final boolean sqlite;

    public FechasDjango(@Value("${spring.datasource.url}") String url) {
        this.sqlite = url.startsWith("jdbc:sqlite:");
    }

    /** Ahora, con la precisión de Django (microsegundos). */
    public static Instant ahora() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    /** Valor para guardar o comparar una fecha en la base, en el formato de Django. */
    public Object parametro(Instant fecha) {
        if (!sqlite) {
            return OffsetDateTime.ofInstant(fecha, ZoneOffset.UTC);
        }
        LocalDateTime local = LocalDateTime.ofInstant(fecha, ZoneOffset.UTC);
        String texto = local.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        int micros = local.getNano() / 1000;
        // Django escribe los microsegundos solo si no son cero (isoformat).
        return micros == 0 ? texto : texto + String.format(".%06d", micros);
    }

    /** Lee una fecha de la base (texto en SQLite, timestamp en PostgreSQL). */
    public static Instant leer(Object valor) {
        if (valor == null) {
            return null;
        }
        if (valor instanceof Timestamp marca) {
            return marca.toInstant();
        }
        if (valor instanceof OffsetDateTime conZona) {
            return conZona.toInstant();
        }
        String texto = valor.toString().replace('T', ' ');
        int zona = Math.max(texto.indexOf('+', 10), texto.indexOf('Z'));
        if (zona > 0) {
            texto = texto.substring(0, zona);
        }
        return LocalDateTime.parse(texto, TEXTO_SQLITE).toInstant(ZoneOffset.UTC);
    }

    /** Como DateTimeField de DRF: «2026-11-25T21:47:01.123456Z» o «2026-11-25T21:47:01Z». */
    public static String paraApi(Instant fecha) {
        if (fecha == null) {
            return null;
        }
        LocalDateTime local = LocalDateTime.ofInstant(fecha, ZoneOffset.UTC);
        String texto = local.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        int micros = local.getNano() / 1000;
        return (micros == 0 ? texto : texto + String.format(".%06d", micros)) + "Z";
    }
}
