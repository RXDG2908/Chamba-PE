package pe.tecsup.chambape.prestadores;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** HU-10: la recomendación de Spring responde igual que la de Django (ia/tests.py, RecomendacionApiTests). */
@SpringBootTest
@AutoConfigureMockMvc
class RecomendacionApiTest extends PruebaConBase {

    private static final double LATITUD = -12.046374;
    private static final double LONGITUD = -77.042793;
    // 1 km al norte del cliente en grados de latitud, como en ia/tests.py.
    private static final double KM = 1 / 111.195;
    private static final long RUBRO = 10;

    @Autowired
    private MockMvc mvc;

    private long siguiente;

    @BeforeEach
    void rubroPropio() {
        jdbc.update("insert into prestadores_rubro (id, nombre) values (10, 'Gasfitería HU-10'), (11, 'Pintura HU-10')");
        siguiente = 20;
    }

    private long prestador(String nombre, double km, String promedio, int total) {
        long id = siguiente++;
        String latitud = String.format(Locale.ROOT, "%.6f", LATITUD + km * KM);
        prestador(id, nombre, latitud, String.valueOf(LONGITUD), 15, true, "verificado", RUBRO);
        jdbc.update("update prestadores_prestador set calificacion_promedio = ?, calificaciones_total = ? where id = ?",
            promedio == null ? null : new java.math.BigDecimal(promedio), total, id);
        return id;
    }

    private void enCurso(long prestador, int cantidad, String estado) {
        for (int i = 0; i < cantidad; i++) {
            jdbc.update("""
                insert into servicios_solicitud (prestador_id, rubro_id, cliente_nombre, cliente_telefono, descripcion,
                  latitud, longitud, estado, creada)
                values (?, ?, 'Cliente', '987654321', 'Trabajo', ?, ?, ?, '2026-11-26 10:00:00')""",
                prestador, RUBRO, LATITUD, LONGITUD, estado);
        }
    }

    private ResultActions pedir(String consulta) throws Exception {
        return mvc.perform(get(URI.create("/api/ia/recomendacion/?" + consulta)));
    }

    private ResultActions pedir() throws Exception {
        return pedir("rubro=" + RUBRO + "&latitud=" + LATITUD + "&longitud=" + LONGITUD);
    }

    @Test
    void recomiendaAlMejorPuntajeConMotivoYTiempo() throws Exception {
        prestador("Cerca", 0.5, null, 0);
        long mejor = prestador("Valorado", 2, "4.90", 25);
        pedir()
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recomendado.id").value(mejor))
            .andExpect(jsonPath("$.recomendado.puntaje").value(87.8))
            .andExpect(jsonPath("$.recomendado.tiempo_estimado_min").value(16))
            .andExpect(jsonPath("$.recomendado.servicios_en_curso").value(0))
            .andExpect(jsonPath("$.recomendado.motivo")
                .value("Está a 2,0 km de ti, tiene ★ 4,9 con 25 calificaciones y no tiene servicios en curso."))
            .andExpect(jsonPath("$.alternativas[*].nombres", contains("Cerca")))
            .andExpect(jsonPath("$.alternativas[0].puntaje").value(84.4))
            .andExpect(jsonPath("$.alternativas[0].motivo").value(
                "Es el más cercano (a 0,5 km de ti), aún no tiene calificaciones y no tiene servicios en curso."))
            .andExpect(jsonPath("$.recomendado.dni").doesNotExist())
            .andExpect(jsonPath("$.recomendado.telefono").doesNotExist())
            // Mismo orden de campos que Django: los de la búsqueda y luego los de la recomendación.
            .andExpect(content().string(containsString(
                "\"calificaciones_total\":25,\"tipo\":\"independiente\",\"nombre_publico\":\"Valorado Prueba\",\"descripcion\":\"\",\"foto\":null,\"nuevo\":false,\"puntaje\":87.8,\"tiempo_estimado_min\":16,\"servicios_en_curso\":0,\"motivo\"")));
    }

    @Test
    void conCalificacionesParecidasGanaElMasCercano() throws Exception {
        long cerca = prestador("Cerca", 1, "4.50", 10);
        prestador("Lejos", 6, "4.60", 1);
        pedir()
            .andExpect(jsonPath("$.recomendado.id").value(cerca))
            .andExpect(jsonPath("$.recomendado.motivo", startsWith("Es el más cercano (a 1,0 km de ti)")))
            .andExpect(jsonPath("$.alternativas[0].motivo", containsString("con 1 calificación y")));
    }

    @Test
    void losServiciosEnCursoBajanElPuntajeYAlarganElTiempo() throws Exception {
        long ocupado = prestador("Ocupado", 1, null, 0);
        long libre = prestador("Libre", 1.5, null, 0);
        enCurso(ocupado, 1, "aceptada");
        enCurso(ocupado, 1, "en_servicio");
        enCurso(ocupado, 1, "finalizada");  // los terminados no cuentan
        enCurso(ocupado, 1, "pendiente");
        pedir()
            .andExpect(jsonPath("$.recomendado.id").value(libre))
            .andExpect(jsonPath("$.alternativas[0].servicios_en_curso").value(2))
            .andExpect(jsonPath("$.alternativas[0].tiempo_estimado_min").value(73))
            .andExpect(jsonPath("$.alternativas[0].motivo", containsString("tiene 2 servicios en curso")));
    }

    @Test
    void soloConsideraLosQueAparecenEnLaBusqueda() throws Exception {
        long inactivo = prestador("Inactivo", 0, "5.00", 50);
        jdbc.update("update prestadores_prestador set disponible = 0 where id = ?", inactivo);
        long pendiente = prestador("Pendiente", 0, null, 0);
        jdbc.update("update prestadores_prestador set estado = 'pendiente' where id = ?", pendiente);
        long otroRubro = prestador("OtroRubro", 0, null, 0);
        jdbc.update("update prestadores_prestador_rubros set rubro_id = 11 where prestador_id = ?", otroRubro);
        long fuera = prestador("FueraDeRadio", 8, null, 0);
        jdbc.update("update prestadores_prestador set radio_cobertura_km = 5 where id = ?", fuera);
        long valido = prestador("Valido", 3, null, 0);
        pedir()
            .andExpect(jsonPath("$.recomendado.id").value(valido))
            .andExpect(jsonPath("$.alternativas", hasSize(0)));
    }

    @Test
    void sinPrestadoresDevuelveNull() throws Exception {
        pedir()
            .andExpect(status().isOk())
            .andExpect(content().json("{\"recomendado\":null,\"alternativas\":[]}", org.springframework.test.json.JsonCompareMode.STRICT))
            .andExpect(jsonPath("$.recomendado").value(nullValue()));
    }

    @Test
    void maximoTresAlternativasEnOrden() throws Exception {
        for (int n = 0; n < 6; n++) {
            prestador("P" + n, n + 0.5, null, 0);
        }
        pedir()
            .andExpect(jsonPath("$.recomendado.nombres").value("P0"))
            .andExpect(jsonPath("$.alternativas[*].nombres", contains("P1", "P2", "P3")));
    }

    @Test
    void empateExactoLoResuelveElId() throws Exception {
        long primero = prestador("Uno", 1, null, 0);
        prestador("Dos", 1, null, 0);
        pedir()
            .andExpect(jsonPath("$.recomendado.id").value(primero))
            // Los dos están a la misma distancia: los dos son «el más cercano».
            .andExpect(jsonPath("$.alternativas[0].motivo", startsWith("Es el más cercano")));
    }

    @Test
    void validaIgualQueLaBusqueda() throws Exception {
        pedir("latitud=0")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.rubro[0]").value("El parámetro rubro es obligatorio."))
            .andExpect(jsonPath("$.longitud[0]").value("Este parámetro es obligatorio."))
            .andExpect(jsonPath("$.latitud").doesNotExist());
        pedir("rubro=999&latitud=0&longitud=0").andExpect(jsonPath("$.rubro[0]").value("No existe un rubro con ese ID."));
    }

    @Test
    void esPublicaYSoloAceptaGet() throws Exception {
        prestador("Uno", 1, null, 0);
        // Como Django (authentication_classes = []): un token inválido no la bloquea.
        mvc.perform(get(URI.create("/api/ia/recomendacion/?rubro=10&latitud=-12.046374&longitud=-77.042793"))
                .header("Authorization", "Bearer token-falso"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.recomendado.nombres").value("Uno"));
        mvc.perform(post("/api/ia/recomendacion/"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.detail").value("Method \"POST\" not allowed."))
            .andExpect(header().string("Allow", containsString("GET")));
    }
}
