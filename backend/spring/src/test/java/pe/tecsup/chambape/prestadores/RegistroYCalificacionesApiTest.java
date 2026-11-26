package pe.tecsup.chambape.prestadores;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import pe.tecsup.chambape.servicios.Notificador;
import pe.tecsup.chambape.usuarios.VerificadorFirebase;
import pe.tecsup.chambape.usuarios.VerificadorFirebase.Identidad;

/**
 * Registro de la app móvil y calificaciones (HU-12) en Spring, con las reglas de Django
 * (usuarios/test_registro_movil.py, prestadores/tests.py y calificaciones/tests.py). La paridad
 * campo a campo con Django la prueba tools/paridad_solicitudes/correr.py.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RegistroYCalificacionesApiTest extends PruebaConBase {

    private static final String CLIENTE = "/api/usuarios/registro-movil/";
    private static final String PRESTADOR = "/api/prestadores/registro-movil/";
    private static final String CALIFICAR = "/api/calificaciones/";
    private static final String MIS_SERVICIOS = "/api/calificaciones/mis-servicios/";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private VerificadorFirebase verificador;

    @MockitoBean
    private Notificador notificador;

    @BeforeEach
    void cuentasDeLaApp() {
        when(verificador.configurado()).thenReturn(true);
        identidad("token-nuevo", new Identidad("uid-nuevo", "Nueva@Ejemplo.PE", "Nombre del token"));
        identidad("token-ana", new Identidad("uid-ana", null, null));
        identidad("token-cliente", new Identidad("uid-cliente", null, null));
        identidad("token-repetido", new Identidad("uid-repetido", "CLIENTE@ejemplo.pe", null));
        jdbc.update("insert into usuarios_perfil (rol, usuario_id, firebase_uid) values ('prestador', 1, 'uid-ana')");
        cuenta(50, "cliente_spring");
        jdbc.update("update auth_user set email = 'cliente@ejemplo.pe' where id = 50");
        jdbc.update("insert into usuarios_perfil (rol, usuario_id, firebase_uid) values ('cliente', 50, 'uid-cliente')");
    }

    private void identidad(String token, Identidad identidad) {
        when(verificador.identidad(token)).thenReturn(Optional.of(identidad));
        when(verificador.uid(token)).thenReturn(Optional.of(identidad.uid()));
    }

    private ResultActions enviar(MockHttpServletRequestBuilder peticion, String cuerpo, String token) throws Exception {
        peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        if (token != null) {
            peticion.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(peticion);
    }

    @Test
    void clienteNuevoSeRegistraUnaSolaVez() throws Exception {
        enviar(post(CLIENTE), "{\"nombre\": \"  Luz  \"}", "token-nuevo")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.username").value("fb-uid-nuevo"))
            .andExpect(jsonPath("$.email").value("Nueva@ejemplo.pe"))
            .andExpect(jsonPath("$.nombres").value("Luz"))
            .andExpect(jsonPath("$.rol").value("cliente"))
            .andExpect(jsonPath("$.prestador_id").value(nullValue()));
        // Idempotente: la segunda vez devuelve la misma cuenta.
        enviar(post(CLIENTE), "{}", "token-nuevo").andExpect(status().isOk()).andExpect(jsonPath("$.nombres").value("Luz"));
        assertEquals("!", jdbc.queryForObject("select substr(password, 1, 1) from auth_user where username = 'fb-uid-nuevo'",
            String.class));
        assertEquals(41, jdbc.queryForObject("select length(password) from auth_user where username = 'fb-uid-nuevo'",
            Integer.class));
        // Ya puede usar las rutas con su token.
        enviar(get(MIS_SERVICIOS), "", "token-nuevo").andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void registroRechazaTokenCuentaDeOtroRolYCorreoEnUso() throws Exception {
        enviar(post(CLIENTE), "{}", null).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.detail").value("El token no es válido o venció."));
        enviar(post(CLIENTE), "{}", "token-ana").andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("Esta cuenta de la app no es de cliente."));
        enviar(post(CLIENTE), "{}", "token-repetido").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.email[0]").value(startsWith("Ya existe una cuenta con ese correo.")));
        enviar(post(PRESTADOR), "{}", "token-cliente").andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("Esta cuenta de la app ya está registrada."));
    }

    @Test
    void prestadorNuevoQuedaPendienteConSusRubros() throws Exception {
        String cuerpo = "{\"nombres\": \" Pedro \", \"apellidos\": \"Pérez\", \"dni\": \"20000001\", \"rubros\": [3, 1],"
            + " \"latitud\": \"-12.05\", \"longitud\": -77.04, \"radio_cobertura_km\": \"10\"}";
        enviar(post(PRESTADOR), cuerpo, "token-nuevo")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.nombres").value("Pedro"))
            .andExpect(jsonPath("$.telefono").value(""))
            // En el orden de los rubros por nombre: Albañilería (3) y Gasfitería (1).
            .andExpect(jsonPath("$.rubros", contains(3, 1)))
            .andExpect(jsonPath("$.latitud").value("-12.050000"))
            .andExpect(jsonPath("$.radio_cobertura_km").value(10))
            .andExpect(jsonPath("$.estado").value("pendiente"));
        assertEquals("prestador", jdbc.queryForObject("select rol from usuarios_perfil where firebase_uid = 'uid-nuevo'",
            String.class));
        enviar(get("/api/usuarios/disponibilidad/"), "", "token-nuevo")
            .andExpect(jsonPath("$.disponible").value(false)).andExpect(jsonPath("$.estado").value("pendiente"));
    }

    @Test
    void prestadorConDatosInvalidosRecibeLosMensajesDeDjango() throws Exception {
        String cuerpo = "{\"nombres\": \"  \", \"apellidos\": \"Pérez\", \"dni\": \"123456789\", \"rubros\": [],"
            + " \"latitud\": \"100\", \"longitud\": \"abc\", \"radio_cobertura_km\": 0}";
        enviar(post(PRESTADOR), cuerpo, "token-nuevo")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.nombres[0]").value("This field may not be blank."))
            .andExpect(jsonPath("$.dni", contains("El DNI debe tener 8 dígitos.",
                "Ensure this value has at most 8 characters (it has 9).", "Ensure this field has no more than 8 characters.")))
            .andExpect(jsonPath("$.rubros[0]").value("Elige al menos un rubro."))
            .andExpect(jsonPath("$.latitud[0]").value("Ensure this value is less than or equal to 90."))
            .andExpect(jsonPath("$.longitud[0]").value("A valid number is required."))
            .andExpect(jsonPath("$.radio_cobertura_km[0]").value("Ensure this value is greater than or equal to 1."));
        enviar(post(PRESTADOR), cuerpo.replace("123456789", "00000001"), "token-nuevo")
            .andExpect(jsonPath("$.dni", contains("Ya existe un prestador con ese DNI.")));
        assertEquals(0, jdbc.queryForObject("select count(*) from auth_user where username = 'fb-uid-nuevo'", Integer.class));
    }

    private long solicitudDelCliente(String estado) {
        jdbc.update("""
            insert into servicios_solicitud (prestador_id, rubro_id, cliente_id, cliente_nombre, cliente_telefono,
              descripcion, latitud, longitud, estado, creada, respondida)
            values (1, 1, 50, 'Carla', '900000000', 'Fuga', -12.044861, -77.026582, ?, '2026-11-26 10:00:00', null)""",
            estado);
        return jdbc.queryForObject("select max(id) from servicios_solicitud", Long.class);
    }

    @Test
    void clienteCalificaUnaVezYSeRecalculaElPromedio() throws Exception {
        long primera = solicitudDelCliente("finalizada");
        long segunda = solicitudDelCliente("finalizada");
        long pendiente = solicitudDelCliente("pendiente");
        enviar(post(CALIFICAR), "{\"solicitud\": " + primera + ", \"estrellas\": 4, \"comentario\": \" Bien \"}",
            "token-cliente")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.calificacion.comentario").value("Bien"))
            .andExpect(jsonPath("$.prestador.calificacion_promedio").value(4.0))
            .andExpect(jsonPath("$.prestador.calificaciones_total").value(1));
        enviar(post(CALIFICAR), "{\"solicitud\": " + primera + ", \"estrellas\": 5}", "token-cliente")
            .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("Ya calificaste este servicio."));
        enviar(post(CALIFICAR), "{\"solicitud\": " + segunda + ", \"estrellas\": 5}", "token-cliente")
            .andExpect(jsonPath("$.prestador.calificacion_promedio").value(4.5));
        enviar(post(CALIFICAR), "{\"solicitud\": " + pendiente + ", \"estrellas\": 5}", "token-cliente")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("Solo puedes calificar un servicio Finalizado."));
        assertEquals("4.5", jdbc.queryForObject("select cast(calificacion_promedio as text) from prestadores_prestador where id = 1",
            String.class));

        enviar(get(MIS_SERVICIOS), "", "token-cliente")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(3)))
            .andExpect(jsonPath("$[0].prestador.nombre").value("Ana Prueba"))
            .andExpect(jsonPath("$[?(@.id == " + primera + ")].calificacion.estrellas").value(contains(4)))
            .andExpect(jsonPath("$[?(@.id == " + pendiente + ")].puede_calificar").value(contains(false)));
    }

    @Test
    void calificarExigeClienteYDatosValidos() throws Exception {
        enviar(post(CALIFICAR), "{}", null).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.detail").value("Authentication credentials were not provided."));
        enviar(post(CALIFICAR), "{}", "token-ana").andExpect(status().isForbidden())
            .andExpect(jsonPath("$.detail").value("Esta acción es solo para clientes."));
        enviar(post(CALIFICAR), "{\"solicitud\": \"5\", \"estrellas\": 6}", "token-cliente")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.solicitud[0]").value("Indica el número de la solicitud."))
            .andExpect(jsonPath("$.estrellas[0]").value("Elige de 1 a 5 estrellas."));
        enviar(post(CALIFICAR), "{\"solicitud\": 999, \"estrellas\": 5}", "token-cliente")
            .andExpect(status().isNotFound());
    }
}
