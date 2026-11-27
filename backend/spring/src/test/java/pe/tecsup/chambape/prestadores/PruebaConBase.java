package pe.tecsup.chambape.prestadores;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base temporal con el esquema de Django y los mismos prestadores que servicios/tests.py.
 * La aplicación la abre en solo lectura, como la db.sqlite3 real; las pruebas escriben los
 * datos por una conexión aparte, que se cierra al terminar cada clase.
 */
abstract class PruebaConBase {

    // Punto a exactamente 5 km al norte de (0, 0): prueba el borde inclusivo del radio.
    static final double LATITUD_5_KM = Math.toDegrees(5 / Distancias.RADIO_TIERRA_KM);

    private static final Path BASE = baseTemporal();

    private static SingleConnectionDataSource conexion;

    static JdbcTemplate jdbc;

    private static Path baseTemporal() {
        try {
            Path base = Files.createTempFile("chambape-spring-", ".sqlite3");
            base.toFile().deleteOnExit();
            return base;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void baseSoloLectura(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", () -> "jdbc:sqlite:" + BASE.toAbsolutePath() + "?busy_timeout=5000");
    }

    @BeforeAll
    static void abrirConexion() {
        conexion = new SingleConnectionDataSource("jdbc:sqlite:" + BASE.toAbsolutePath(), true);
        jdbc = new JdbcTemplate(conexion);
    }

    @AfterAll
    static void cerrarConexion() {
        conexion.destroy();
    }

    @BeforeEach
    void cargarDatos() {
        jdbc.execute((java.sql.Connection c) -> {
            ScriptUtils.executeSqlScript(c, new ClassPathResource("esquema-django.sql"));
            return null;
        });
        jdbc.update("insert into prestadores_rubro (id, nombre) values (1, 'Gasfitería'), (2, 'Electricidad'), (3, 'Albañilería')");
        prestador(1, "Ana", "-12.044861", "-77.026582", 5, true, "verificado", 1, 3);
        prestador(2, "Beto", "-12.044861", "-77.026582", 5, true, "pendiente", 1);
        prestador(3, "Carla", "-12.044861", "-77.026582", 5, false, "verificado", 1);
        prestador(4, "Dino", "-12.044861", "-77.026582", 5, true, "verificado", 2);
        prestador(5, "Eva", "-12.064861", "-77.026582", 5, true, "verificado", 1);
        prestador(6, "Félix", "-11.000000", "-77.026582", 5, true, "rechazado", 1);
        prestador(7, "Gina", "-11.000000", "-76.000000", 5, true, "verificado", 1);
        prestador(8, "Hugo", String.valueOf(LATITUD_5_KM), "0", 5, true, "verificado", 1);
    }

    /** Cuenta de Django activa, sin contraseña utilizable (como create_user sin clave). */
    static void cuenta(long id, String usuario) {
        jdbc.update("""
            insert into auth_user (id, password, is_superuser, username, first_name, last_name, email, is_staff,
              is_active, date_joined)
            values (?, '!', 0, ?, '', '', '', 0, 1, '2026-11-23 10:00:00')""", id, usuario);
    }

    static void prestador(long id, String nombre, String latitud, String longitud, int radio,
                          boolean disponible, String estado, long... rubros) {
        cuenta(id, "usuario" + id);
        jdbc.update("""
            insert into prestadores_prestador (id, nombres, apellidos, dni, telefono, latitud, longitud,
              radio_cobertura_km, disponible, estado, creado, usuario_id, calificaciones_total)
            values (?, ?, 'Prueba', ?, '', ?, ?, ?, ?, ?, '2025-01-15 10:00:00', ?, 0)""",
            id, nombre, String.format("%08d", id), Double.parseDouble(latitud), Double.parseDouble(longitud),
            radio, disponible ? 1 : 0, estado, id);
        for (long rubro : rubros) {
            jdbc.update("insert into prestadores_prestador_rubros (prestador_id, rubro_id) values (?, ?)", id, rubro);
        }
    }
}
