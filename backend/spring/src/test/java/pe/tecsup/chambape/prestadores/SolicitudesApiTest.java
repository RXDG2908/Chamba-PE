package pe.tecsup.chambape.prestadores;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

import pe.tecsup.chambape.servicios.CodigoSeguimiento;
import pe.tecsup.chambape.servicios.Notificador;
import pe.tecsup.chambape.usuarios.VerificadorFirebase;

/**
 * Solicitudes y disponibilidad en Spring, con las mismas reglas que Django
 * (servicios/tests.py, test_seguimiento.py y usuarios/test_auth_firebase.py).
 * El token de Firebase se simula: cada prueba de VerificadorFirebaseTest cubre la firma real.
 */
@SpringBootTest(properties = "chambape.django.secret-key=clave-de-prueba-spring")
@AutoConfigureMockMvc
class SolicitudesApiTest extends PruebaConBase {

    private static final String API = "/api/servicios/solicitudes/";
    private static final String CENTRO = "\"latitud\": \"-12.044861\", \"longitud\": \"-77.026582\"";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CodigoSeguimiento codigos;

    @MockitoBean
    private VerificadorFirebase verificador;

    @MockitoBean
    private Notificador notificador;

    @BeforeEach
    void cuentasDeLaApp() {
        when(verificador.configurado()).thenReturn(true);
        when(verificador.uid("token-ana")).thenReturn(Optional.of("uid-ana"));
        when(verificador.uid("token-eva")).thenReturn(Optional.of("uid-eva"));
        when(verificador.uid("token-cliente")).thenReturn(Optional.of("uid-cliente"));
        when(verificador.uid("token-otro")).thenReturn(Optional.of("uid-sin-vincular"));
        when(verificador.uid("token-malo")).thenReturn(Optional.empty());
        perfil(1, "prestador", "uid-ana");
        perfil(5, "prestador", "uid-eva");
        cuenta(50, "cliente_spring");
        perfil(50, "cliente", "uid-cliente");
    }

    private static void perfil(long usuario, String rol, String uid) {
        jdbc.update("insert into usuarios_perfil (rol, usuario_id, firebase_uid) values (?, ?, ?)", rol, usuario, uid);
    }

    private static String solicitud(String extra) {
        return "{\"prestador\": 1, \"rubro\": 1, \"cliente_nombre\": \"Carla Cliente\", \"cliente_telefono\": \"900000000\","
            + " \"descripcion\": \"Fuga en el baño.\", " + CENTRO + extra + "}";
    }

    private ResultActions enviar(MockHttpServletRequestBuilder peticion, String cuerpo, String token) throws Exception {
        peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        if (token != null) {
            peticion.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(peticion);
    }

    private long crear(String token) throws Exception {
        String cuerpo = enviar(post(API), solicitud(""), token).andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return Long.parseLong(cuerpo.replaceAll("^\\{\"id\":(\\d+),.*$", "$1"));
    }

    private ResultActions comoAna(MockHttpServletRequestBuilder peticion) throws Exception {
        return mvc.perform(peticion.header("Authorization", "Bearer token-ana"));
    }

    private ResultActions avanzar(long id, String estado) throws Exception {
        return enviar(post(API + id + "/avanzar/"), "{\"estado\": \"" + estado + "\"}", "token-ana");
    }

    @Test
    void visitanteEnviaSolicitudYRecibeSuCodigo() throws Exception {
        long id = crear(null);
        mvc.perform(get(API + id + "/")).andExpect(status().isNotFound());
        String codigo = codigos.de(id);
        enviar(post(API), solicitud(""), null)
            .andExpect(jsonPath("$.prestador").value(1))
            .andExpect(jsonPath("$.latitud").value("-12.044861"))
            .andExpect(jsonPath("$.estado").value("pendiente"))
            .andExpect(jsonPath("$.creada").value(matchesPattern("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d(\\.\\d{6})?Z")));
        mvc.perform(get(API + id + "/?codigo=" + codigo))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "private, no-store"))
            .andExpect(jsonPath("$.prestador_nombres").value("Ana"))
            .andExpect(jsonPath("$.rubro").value("Gasfitería"))
            .andExpect(jsonPath("$.estado_display").value("Pendiente"))
            .andExpect(jsonPath("$.respondida").value(nullValue()))
            .andExpect(jsonPath("$.historial", hasSize(0)));
        // El código de otra solicitud no sirve.
        mvc.perform(get(API + id + "/?codigo=" + codigos.de(id + 1)))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Not found."));
        mvc.perform(get(API + "999/")).andExpect(jsonPath("$.detail").value("No Solicitud matches the given query."));
        verify(notificador).solicitudNueva(id, 1L);
    }

    @Test
    void fechasYDecimalesSeGuardanComoDjango() throws Exception {
        long id = crear(null);
        String creada = jdbc.queryForObject("select creada from servicios_solicitud where id = ?", String.class, id);
        assertEquals(true, creada.matches("\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d(\\.\\d{6})?"), creada);
        assertEquals("real", jdbc.queryForObject("select typeof(latitud) from servicios_solicitud where id = ?", String.class, id));
    }

    @Test
    void clienteConTokenLaVeSinCodigoYOtroNo() throws Exception {
        long id = crear("token-cliente");
        assertEquals(50L, jdbc.queryForObject("select cliente_id from servicios_solicitud where id = ?", Long.class, id));
        mvc.perform(get(API + id + "/").header("Authorization", "Bearer token-cliente")).andExpect(status().isOk());
        mvc.perform(get(API + id + "/").header("Authorization", "Bearer token-eva")).andExpect(status().isNotFound());
        comoAna(get(API + id + "/")).andExpect(status().isOk());
    }

    @Test
    void validacionCampoPorCampo() throws Exception {
        enviar(post(API), "{}", null)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.prestador[0]").value("This field is required."))
            .andExpect(jsonPath("$.longitud[0]").value("This field is required."));
        enviar(post(API), """
            {"prestador": "abc", "rubro": 99, "cliente_nombre": "123", "cliente_telefono": "+51900000000",
             "descripcion": "   ", "latitud": "91", "longitud": "-77.1234567"}""", null)
            .andExpect(jsonPath("$.prestador[0]").value("Incorrect type. Expected pk value, received str."))
            .andExpect(jsonPath("$.rubro[0]").value("Invalid pk \"99\" - object does not exist."))
            .andExpect(jsonPath("$.cliente_nombre[0]")
                .value("Ingresa un nombre con al menos una letra; solo letras, espacios, guiones y apóstrofes."))
            .andExpect(jsonPath("$.cliente_telefono[0]").value("Ingresa exactamente 9 dígitos del 0 al 9, sin +51 ni espacios."))
            .andExpect(jsonPath("$.descripcion[0]").value("Describe el trabajo; no uses solo espacios."))
            .andExpect(jsonPath("$.latitud[0]").value("Ensure this value is less than or equal to 90."))
            .andExpect(jsonPath("$.longitud[0]").value("Ensure that there are no more than 6 decimal places."));
        enviar(post(API), """
            {"prestador": true, "rubro": null, "cliente_nombre": "", "cliente_telefono": 900000000,
             "descripcion": ["a"], "latitud": 1234.5, "longitud": "abc"}""", null)
            .andExpect(jsonPath("$.prestador[0]").value("Incorrect type. Expected pk value, received bool."))
            .andExpect(jsonPath("$.rubro[0]").value("This field may not be null."))
            .andExpect(jsonPath("$.cliente_nombre[0]").value("Ingresa un nombre con al menos una letra."))
            .andExpect(jsonPath("$.cliente_telefono").doesNotExist())
            .andExpect(jsonPath("$.descripcion[0]").value("Not a valid string."))
            .andExpect(jsonPath("$.latitud[0]").value("Ensure that there are no more than 3 digits before the decimal point."))
            .andExpect(jsonPath("$.longitud[0]").value("A valid number is required."));
        enviar(post(API), "[1, 2]", null)
            .andExpect(jsonPath("$.non_field_errors[0]").value("Invalid data. Expected a dictionary, but got list."));
    }

    @Test
    void reglasDelPrestador() throws Exception {
        String[][] casos = {
            {"\"prestador\": 2", "prestador", "El prestador aún no está verificado."},
            {"\"prestador\": 3", "prestador", "El prestador no está disponible."},
            {"\"prestador\": 4", "rubro", "El prestador no ofrece ese rubro."},
            {"\"prestador\": 7", "prestador", "Tu ubicación está fuera del área de cobertura del prestador."},
        };
        for (String[] caso : casos) {
            enviar(post(API), solicitud(", " + caso[0]), null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$." + caso[1] + "[0]").value(caso[2]));
        }
        verify(notificador, never()).solicitudNueva(anyLong(), anyLong());
    }

    @Test
    void prestadorSancionadoNoRecibeNiEntra() throws Exception {
        jdbc.update("""
            insert into administracion_sancioncuenta (tipo, motivo, hasta, creada, motivo_levantamiento, usuario_id)
            values ('bloqueo', 'Cobros fuera de la plataforma.', null, '2026-11-25 10:00:00', '', 1)""");
        enviar(post(API), solicitud(""), null)
            .andExpect(jsonPath("$.prestador[0]").value("El prestador no está disponible."));
        comoAna(get(API + "recibidas/"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.detail").value("Tu cuenta está bloqueada. Motivo: Cobros fuera de la plataforma."));
    }

    @Test
    void suspensionVencidaYaNoImpideEntrar() throws Exception {
        jdbc.update("""
            insert into administracion_sancioncuenta (tipo, motivo, hasta, creada, motivo_levantamiento, usuario_id)
            values ('suspension', 'Ya cumplida.', '2026-01-01 00:00:00', '2025-12-25 10:00:00', '', 1)""");
        comoAna(get(API + "recibidas/")).andExpect(status().isOk());
    }

    @Test
    void bandejaAceptarYAvanzarComoDjango() throws Exception {
        long id = crear(null);
        mvc.perform(get(API + "recibidas/"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.detail").value("Authentication credentials were not provided."));
        mvc.perform(get(API + "recibidas/").header("Authorization", "Bearer token-cliente"))
            .andExpect(jsonPath("$.detail").value("Esta acción es solo para prestadores."));
        comoAna(get(API + "recibidas/"))
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].cliente_telefono").value("900000000"))
            .andExpect(jsonPath("$[0].siguiente_estado").value(nullValue()));

        avanzar(id, "en_camino").andExpect(status().isConflict())
            .andExpect(jsonPath("$.estado").value("Primero acepta la solicitud para empezar el seguimiento."));
        mvc.perform(post(API + id + "/aceptar/").header("Authorization", "Bearer token-eva")).andExpect(status().isNotFound());
        comoAna(post(API + id + "/aceptar/"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado_display").value("Confirmado"))
            .andExpect(jsonPath("$.siguiente_estado").value("en_camino"))
            .andExpect(jsonPath("$.historial[*].estado", contains("aceptada")));
        comoAna(post(API + id + "/rechazar/"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.estado").value("La solicitud ya fue confirmado."));

        avanzar(id, "en_servicio").andExpect(status().isConflict())
            .andExpect(jsonPath("$.estado").value("La solicitud está «Confirmado»; solo puede pasar a «En camino»."))
            .andExpect(jsonPath("$.estado_actual").value("aceptada"));
        avanzar(id, "volando").andExpect(status().isBadRequest()).andExpect(jsonPath("$.estado").value("Indica un estado válido."));
        for (String estado : new String[] {"en_camino", "en_servicio", "finalizada"}) {
            avanzar(id, estado).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value(estado));
        }
        avanzar(id, "finalizada").andExpect(status().isConflict())
            .andExpect(jsonPath("$.estado").value("La solicitud está «Finalizada» y ya no se puede avanzar."));
        comoAna(get(API + "recibidas/" + id + "/"))
            .andExpect(jsonPath("$.historial[*].estado", contains("aceptada", "en_camino", "en_servicio", "finalizada")));
    }

    @Test
    void tokenInvalidoNoVinculadoOMalFormado() throws Exception {
        mvc.perform(get("/api/prestadores/rubros/").header("Authorization", "Bearer token-malo"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.detail").value("El token no es válido o venció."));
        mvc.perform(get("/api/prestadores/rubros/").header("Authorization", "Bearer token-otro"))
            .andExpect(jsonPath("$.detail").value("Esta cuenta de la app aún no está vinculada."));
        mvc.perform(get("/api/prestadores/rubros/").header("Authorization", "Bearer"))
            .andExpect(jsonPath("$.detail").value("El acceso con token no está disponible."));
        // Como en Django, la búsqueda no autentica: ignora el token.
        mvc.perform(get("/api/prestadores/buscar/?rubro=1&latitud=0&longitud=0").header("Authorization", "Bearer token-malo"))
            .andExpect(status().isOk());
        // Otro esquema (Basic) no es un token: la petición sigue como anónima.
        mvc.perform(get("/api/prestadores/rubros/").header("Authorization", "Basic abc")).andExpect(status().isOk());
    }

    @Test
    void disponibilidadDelPrestador() throws Exception {
        comoAna(get("/api/usuarios/disponibilidad/"))
            .andExpect(jsonPath("$.disponible").value(true)).andExpect(jsonPath("$.estado").value("verificado"));
        enviar(patch("/api/usuarios/disponibilidad/"), "{\"disponible\": \"no\"}", "token-ana")
            .andExpect(status().isOk()).andExpect(jsonPath("$.disponible").value(false));
        enviar(patch("/api/usuarios/disponibilidad/"), "{\"disponible\": 2}", "token-ana")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.disponible[0]").value("Must be a valid boolean."));
        enviar(patch("/api/usuarios/disponibilidad/"), "{}", "token-ana")
            .andExpect(jsonPath("$.disponible[0]").value("This field is required."));
        enviar(patch("/api/usuarios/disponibilidad/"), "{\"disponible\": true}", "token-cliente")
            .andExpect(status().isForbidden());
        // Inactiva no sale en la búsqueda.
        enviar(patch("/api/usuarios/disponibilidad/"), "{\"disponible\": 0}", "token-ana").andExpect(status().isOk());
        mvc.perform(get("/api/prestadores/buscar/?rubro=1&latitud=-12.044861&longitud=-77.026582"))
            .andExpect(jsonPath("$[*].nombres", contains("Eva")));
    }

    @Test
    void formularioYTipoNoSoportado() throws Exception {
        mvc.perform(post(API).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("prestador=1&rubro=1&cliente_nombre=Carla&cliente_telefono=900000000&descripcion=Fuga"
                    + "&latitud=-12.044861&longitud=-77.026582"))
            .andExpect(status().isCreated());
        // Bytes que no son UTF-8: Django lee el formulario como ISO-8859-1 y el JSON lo rechaza.
        byte[] latin1 = ("prestador=1&rubro=1&cliente_nombre=Carla&cliente_telefono=900000000&descripcion=Caño"
            + "&latitud=-12.044861&longitud=-77.026582").getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        mvc.perform(post(API).contentType(MediaType.APPLICATION_FORM_URLENCODED).content(latin1))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.descripcion").value("Caño"));
        mvc.perform(post(API).contentType(MediaType.APPLICATION_JSON).content(new byte[] {'{', '"', (byte) 0xff, '"', '}'}))
            .andExpect(status().isBadRequest());
        mvc.perform(post(API).contentType(MediaType.TEXT_PLAIN).content("hola"))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.detail").value("Unsupported media type \"text/plain\" in request."));
    }
}
