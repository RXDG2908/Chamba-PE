package pe.tecsup.chambape.prestadores;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;

import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.RequestDispatcher;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** La búsqueda de Spring responde igual que la de Django (servicios/tests.py, BusquedaPrestadoresTests). */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
class BusquedaApiTest extends PruebaConBase {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private EntityManagerFactory fabrica;

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
    void muestraElPerfilPublicoComoDjango() throws Exception {
        // Así lo deja Django al editar el perfil: empresa con nombre comercial y foto de la cuenta.
        jdbc.update("update prestadores_prestador set tipo = 'empresa', nombre_comercial = 'Gasfitería Ana SAC',"
            + " descripcion = 'Atendemos todo Lima.' where id = 1");
        jdbc.update("insert into usuarios_perfil (rol, usuario_id, foto) values ('prestador', 1, 'fotos/usuario-1-abc.jpg')");
        buscar("rubro=1&latitud=-12.044861&longitud=-77.026582")
            .andExpect(jsonPath("$[0].tipo").value("empresa"))
            .andExpect(jsonPath("$[0].nombre_publico").value("Gasfitería Ana SAC"))
            .andExpect(jsonPath("$[0].descripcion").value("Atendemos todo Lima."))
            .andExpect(jsonPath("$[0].foto").value("/media/fotos/usuario-1-abc.jpg"))
            // Sin perfil ni foto: independiente, con su nombre, y foto null.
            .andExpect(jsonPath("$[1].tipo").value("independiente"))
            .andExpect(jsonPath("$[1].nombre_publico").value("Eva Prueba"))
            .andExpect(jsonPath("$[1].foto").value(org.hamcrest.Matchers.nullValue()));
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
    void soloLecturaConElMensajeDeDrf() throws Exception {
        mvc.perform(post("/api/prestadores/buscar/"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(header().string("Allow", containsString("GET")))
            .andExpect(jsonPath("$.detail").value("Method \"POST\" not allowed."));
    }

    @Test
    void rutaInexistenteDaJsonNoHtml() throws Exception {
        mvc.perform(get("/api/no-existe/"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("Not found."));
    }

    @Test
    void erroresDeTomcatTambienSalenComoDetail() throws Exception {
        // Lo que Tomcat rechaza antes de llegar a Spring (URL mal formada) pasa por /error.
        mvc.perform(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 400))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Malformed request."))
            .andExpect(jsonPath("$.timestamp").doesNotExist())
            .andExpect(jsonPath("$.path").doesNotExist());
    }

    @Test
    void rubrosDeCadaPrestadorEnLaMismaConsulta() throws Exception {
        Statistics estadisticas = fabrica.unwrap(SessionFactory.class).getStatistics();
        estadisticas.clear();
        buscar("rubro=1&latitud=-12.044861&longitud=-77.026582").andExpect(jsonPath("$", hasSize(2)));
        long conDos = estadisticas.getPrepareStatementCount();

        for (long id = 20; id < 30; id++) {
            prestador(id, "Extra" + id, "-12.044861", "-77.026582", 5, true, "verificado", 1, 2, 3);
        }
        estadisticas.clear();
        buscar("rubro=1&latitud=-12.044861&longitud=-77.026582").andExpect(jsonPath("$", hasSize(12)));
        // Igual número de consultas con 2 que con 12 resultados: no hay una por prestador.
        assertEquals(conDos, estadisticas.getPrepareStatementCount());
        assertTrue(conDos <= 2, "consultas: " + conDos);
    }

    @Test
    void aIgualDistanciaOrdenaPorId() throws Exception {
        // Se insertan al revés para que el orden no dependa de cómo los guardó SQLite.
        prestador(10, "Zoe", "-12.044861", "-77.026582", 5, true, "verificado", 1);
        prestador(9, "Iván", "-12.044861", "-77.026582", 5, true, "verificado", 1);
        buscar("rubro=1&latitud=-12.044861&longitud=-77.026582")
            .andExpect(jsonPath("$[*].nombres", contains("Ana", "Iván", "Zoe", "Eva")));
    }

    @Test
    void digitosYEspaciosUnicodeComoPython() throws Exception {
        // «١» es el 1 arábigo; U+00A0 y U+3000 son espacios para Python.
        buscar("rubro=%D9%A1&latitud=%C2%A0-12.044861%E3%80%80&longitud=-77.026582")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].nombres").value("Ana"));
        // U+200B no es espacio para Python: coordenada inválida.
        buscar("rubro=1&latitud=-12.044861%E2%80%8B&longitud=-77.026582")
            .andExpect(jsonPath("$.latitud[0]").value("Ingresa una coordenada numérica válida."));
    }

    @Test
    void elLargoSeCuentaEnCaracteresComoPython() throws Exception {
        // Un emoji son 2 unidades UTF-16 en Java pero 1 carácter en Python.
        String mil = "%F0%9F%98%80".repeat(1000);
        buscar("rubro=" + mil + "&latitud=0&longitud=0")
            .andExpect(jsonPath("$.rubro[0]").value("El rubro debe ser un ID entero válido."));
        buscar("rubro=" + mil + "%F0%9F%98%80&latitud=0&longitud=0")
            .andExpect(jsonPath("$.rubro[0]").value("El ID del rubro es demasiado largo."));
    }

    @Test
    void corsAbiertoParaLecturasComoDjango() throws Exception {
        mvc.perform(get(URI.create("/api/prestadores/rubros/")).header("Origin", "http://otro.example"))
            .andExpect(header().string("Access-Control-Allow-Origin", "*"))
            .andExpect(content().string(not(containsString("timestamp"))));
    }
}
