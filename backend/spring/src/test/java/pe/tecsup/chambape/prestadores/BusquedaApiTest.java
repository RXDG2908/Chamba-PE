package pe.tecsup.chambape.prestadores;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** La búsqueda de Spring responde igual que la de Django (servicios/tests.py, BusquedaPrestadoresTests). */
@SpringBootTest
@AutoConfigureMockMvc
class BusquedaApiTest {

    // Punto a exactamente 5 km al norte de (0, 0): prueba el borde inclusivo del radio.
    private static final double LATITUD_5_KM = Math.toDegrees(5 / Distancias.RADIO_TIERRA_KM);

    private static final Path BASE = baseTemporal();

    // La aplicación abre la base en solo lectura, como con la db.sqlite3 real de Django;
    // las pruebas escriben los datos por una conexión aparte.
    private final JdbcTemplate jdbc = new JdbcTemplate(
        new SingleConnectionDataSource("jdbc:sqlite:" + BASE.toAbsolutePath(), true));

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
        registro.add("spring.datasource.url", () -> "jdbc:sqlite:" + BASE.toAbsolutePath() + "?open_mode=1");
    }

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        jdbc.execute((java.sql.Connection conexion) -> {
            ScriptUtils.executeSqlScript(conexion, new ClassPathResource("esquema-django.sql"));
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

    private void prestador(long id, String nombre, String latitud, String longitud, int radio,
                           boolean disponible, String estado, long... rubros) {
        jdbc.update("insert into auth_user (id, username) values (?, ?)", id, "usuario" + id);
        jdbc.update("""
            insert into prestadores_prestador (id, nombres, apellidos, dni, telefono, latitud, longitud,
              radio_cobertura_km, disponible, estado, creado, usuario_id, calificaciones_total)
            values (?, ?, 'Prueba', ?, '', ?, ?, ?, ?, ?, '2026-11-23 10:00:00', ?, 0)""",
            id, nombre, String.format("%08d", id), Double.parseDouble(latitud), Double.parseDouble(longitud),
            radio, disponible ? 1 : 0, estado, id);
        for (long rubro : rubros) {
            jdbc.update("insert into prestadores_prestador_rubros (prestador_id, rubro_id) values (?, ?)", id, rubro);
        }
    }

    private ResultActions buscar(String consulta) throws Exception {
        // URI.create para que MockMvc no vuelva a codificar los «%» de la consulta.
        return mvc.perform(get(URI.create("/api/prestadores/buscar/?" + consulta)));
    }

    @Test
    void rubrosOrdenadosPorNombre() throws Exception {
        mvc.perform(get("/api/prestadores/rubros/"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].nombre", contains("Albañilería", "Electricidad", "Gasfitería")))
            .andExpect(jsonPath("$[0].id").value(3));
    }

    @Test
    void soloVerificadosDisponiblesDelRubroYDentroDelRadioOrdenadosPorDistancia() throws Exception {
        buscar("rubro=1&latitud=-12.044861&longitud=-77.026582")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].nombres", contains("Ana", "Eva")))
            .andExpect(jsonPath("$[0].distancia_km").value(0.0))
            .andExpect(header().string("X-Execution-Time-Ms", matchesPattern("\\d+\\.\\d{3}")));
    }

    @Test
    void respuestaSoloConDatosPublicosYCoordenadasCon3Decimales() throws Exception {
        buscar("rubro=1&latitud=-12.044861&longitud=-77.026582")
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].apellidos").value("Prueba"))
            .andExpect(jsonPath("$[0].latitud").value(-12.045))
            .andExpect(jsonPath("$[0].longitud").value(-77.027))
            .andExpect(jsonPath("$[0].rubros[*].nombre", contains("Albañilería", "Gasfitería")))
            .andExpect(jsonPath("$[0].dni").doesNotExist())
            .andExpect(jsonPath("$[0].telefono").doesNotExist())
            .andExpect(jsonPath("$[0].estado").doesNotExist())
            .andExpect(jsonPath("$[0].disponible").doesNotExist());
    }

    @Test
    void muestraPromedioYTotalDeCalificaciones() throws Exception {
        // Así lo deja Django (HU-12) al guardar calificaciones de 5, 4 y 4.
        jdbc.update("update prestadores_prestador set calificacion_promedio = 4.33, calificaciones_total = 3 where id = 1");
        buscar("rubro=1&latitud=-12.044861&longitud=-77.026582")
            .andExpect(jsonPath("$[0].calificacion_promedio").value(4.33))
            .andExpect(jsonPath("$[0].calificaciones_total").value(3))
            // Sin calificaciones: promedio null (no 0) y total 0.
            .andExpect(jsonPath("$[1].nombres").value("Eva"))
            .andExpect(jsonPath("$[1].calificacion_promedio").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$[1].calificaciones_total").value(0));
    }

    @Test
    void elBordeDelRadioEsInclusivo() throws Exception {
        buscar("rubro=1&latitud=0&longitud=0")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].nombres", contains("Hugo")));
    }

    @Test
    void sinResultadosDevuelveListaVacia() throws Exception {
        buscar("rubro=2&latitud=0&longitud=0").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void sinParametrosExplicaCadaCampo() throws Exception {
        buscar("")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.rubro[0]").value("El parámetro rubro es obligatorio."))
            .andExpect(jsonPath("$.latitud[0]").value("Este parámetro es obligatorio."))
            .andExpect(jsonPath("$.longitud[0]").value("Este parámetro es obligatorio."));
    }

    @Test
    void rubroInvalido() throws Exception {
        String base = "&latitud=0&longitud=0";
        buscar("rubro=abc" + base).andExpect(jsonPath("$.rubro[0]").value("El rubro debe ser un ID entero válido."));
        buscar("rubro=" + base).andExpect(jsonPath("$.rubro[0]").value("El rubro debe ser un ID entero válido."));
        buscar("rubro=0" + base).andExpect(jsonPath("$.rubro[0]").value("El rubro debe ser un ID entero positivo."));
        buscar("rubro=99" + base).andExpect(jsonPath("$.rubro[0]").value("No existe un rubro con ese ID."));
        buscar("rubro=99999999999999999999" + base)
            .andExpect(jsonPath("$.rubro[0]").value("No existe un rubro con ese ID."));
        buscar("rubro=" + "1".repeat(1001) + base)
            .andExpect(jsonPath("$.rubro[0]").value("El ID del rubro es demasiado largo."));
    }

    @Test
    void rubroAceptaLasMismasFormasQueDjango() throws Exception {
        for (String rubro : new String[] {"1.0", "1.00", "+1", "%201%20"}) {
            buscar("rubro=" + rubro + "&latitud=-12.044861&longitud=-77.026582")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombres").value("Ana"));
        }
    }

    @Test
    void coordenadasInvalidas() throws Exception {
        buscar("rubro=1&latitud=91&longitud=0")
            .andExpect(jsonPath("$.latitud[0]").value("La coordenada debe estar entre -90 y 90 grados."));
        buscar("rubro=1&latitud=0&longitud=-180.5")
            .andExpect(jsonPath("$.longitud[0]").value("La coordenada debe estar entre -180 y 180 grados."));
        // Igual que Django: DRF rechaza nan e inf como coordenada inválida.
        buscar("rubro=1&latitud=nan&longitud=inf")
            .andExpect(jsonPath("$.latitud[0]").value("Ingresa una coordenada numérica válida."))
            .andExpect(jsonPath("$.longitud[0]").value("Ingresa una coordenada numérica válida."));
        // Java aceptaría «1d» y «0x10», Python no: Spring tampoco.
        buscar("rubro=1&latitud=1d&longitud=0x10")
            .andExpect(jsonPath("$.latitud[0]").value("Ingresa una coordenada numérica válida."))
            .andExpect(jsonPath("$.longitud[0]").value("Ingresa una coordenada numérica válida."));
    }

    @Test
    void parametroRepetidoUsaElUltimo() throws Exception {
        buscar("rubro=2&rubro=1&latitud=-12.044861&longitud=-77.026582")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].nombres").value("Ana"));
    }

    @Test
    void soloLectura() throws Exception {
        mvc.perform(post("/api/prestadores/buscar/")).andExpect(status().isMethodNotAllowed());
    }
}
