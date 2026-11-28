package pe.tecsup.chambape.prestadores;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

import pe.tecsup.chambape.servicios.Notificador;
import pe.tecsup.chambape.usuarios.VerificadorFirebase;

/** Chat de la solicitud, con los mismos casos que servicios/test_chat.py de Django. */
@SpringBootTest(properties = "chambape.django.secret-key=clave-de-prueba-spring")
@AutoConfigureMockMvc
class ChatApiTest extends PruebaConBase {

    private static final String API = "/api/servicios/solicitudes/";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private VerificadorFirebase verificador;

    @MockitoBean
    private Notificador notificador;

    @BeforeEach
    void cuentas() {
        when(verificador.configurado()).thenReturn(true);
        when(verificador.uid("token-ana")).thenReturn(Optional.of("uid-ana"));
        when(verificador.uid("token-eva")).thenReturn(Optional.of("uid-eva"));
        when(verificador.uid("token-cliente")).thenReturn(Optional.of("uid-cliente"));
        when(verificador.uid("token-intruso")).thenReturn(Optional.of("uid-intruso"));
        perfil(1, "prestador", "uid-ana");
        perfil(5, "prestador", "uid-eva");
        cuenta(50, "carlos");
        perfil(50, "cliente", "uid-cliente");
        cuenta(51, "intruso");
        perfil(51, "cliente", "uid-intruso");
        jdbc.update("update prestadores_prestador set telefono = '987654321', apellidos = 'Pérez' where id = 1");
    }

    private static void perfil(long usuario, String rol, String uid) {
        jdbc.update("insert into usuarios_perfil (rol, usuario_id, firebase_uid) values (?, ?, ?)", rol, usuario, uid);
    }

    /** Solicitud a Ana (prestador 1) del cliente 50, en el estado indicado. */
    private static long solicitud(String estado) {
        jdbc.update("""
            insert into servicios_solicitud (prestador_id, rubro_id, cliente_id, cliente_nombre, cliente_telefono,
              descripcion, latitud, longitud, estado, creada)
            values (1, 1, 50, 'Carlos', '999888777', 'Tubería rota.', 0, 0, ?, '2026-11-28 10:00:00')""", estado);
        return jdbc.queryForObject("select max(id) from servicios_solicitud", Long.class);
    }

    private ResultActions leer(long id, String token) throws Exception {
        return mvc.perform(get(API + id + "/chat/").header("Authorization", "Bearer " + token));
    }

    private ResultActions enviar(long id, String token, String cuerpo) throws Exception {
        var peticion = post(API + id + "/chat/").contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        if (token != null) {
            peticion.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(peticion);
    }

    private static String texto(String texto) {
        return "{\"texto\": \"" + texto + "\"}";
    }

    @Test
    void conversacionEntreClienteYPrestador() throws Exception {
        long id = solicitud("aceptada");
        enviar(id, "token-cliente", texto("Hola, ¿a qué hora llega?"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.de").value("cliente"))
            .andExpect(jsonPath("$.texto").value("Hola, ¿a qué hora llega?"))
            .andExpect(jsonPath("$.enviado").value(matchesPattern("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\d(\\.\\d{6})?Z")));
        enviar(id, "token-ana", texto("En 20 minutos.")).andExpect(jsonPath("$.de").value("prestador"));

        leer(id, "token-cliente")
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "private, no-store"))
            .andExpect(jsonPath("$.solicitud").value(id))
            .andExpect(jsonPath("$.estado_display").value("Confirmado"))
            .andExpect(jsonPath("$.activo").value(true))
            .andExpect(jsonPath("$.yo").value("cliente"))
            .andExpect(jsonPath("$.contraparte.nombre").value("Ana Pérez"))
            .andExpect(jsonPath("$.contraparte.telefono").value("987654321"))
            .andExpect(jsonPath("$.mensajes[*].texto").value(contains("Hola, ¿a qué hora llega?", "En 20 minutos.")))
            .andExpect(jsonPath("$.mensajes[*].de").value(contains("cliente", "prestador")));
        leer(id, "token-ana")
            .andExpect(jsonPath("$.yo").value("prestador"))
            .andExpect(jsonPath("$.contraparte.nombre").value("Carlos"))
            .andExpect(jsonPath("$.contraparte.telefono").value("999888777"))
            .andExpect(jsonPath("$.mensajes", hasSize(2)));
    }

    @Test
    void finalizadaQuedaSoloParaLeer() throws Exception {
        long id = solicitud("en_servicio");
        enviar(id, "token-cliente", texto("Gracias")).andExpect(status().isCreated());
        jdbc.update("update servicios_solicitud set estado = 'finalizada' where id = ?", id);
        leer(id, "token-cliente")
            .andExpect(jsonPath("$.activo").value(false))
            .andExpect(jsonPath("$.contraparte.telefono").value(nullValue()))
            .andExpect(jsonPath("$.mensajes[*].texto").value(contains("Gracias")));
        leer(id, "token-ana").andExpect(jsonPath("$.contraparte.telefono").value("999888777"));
        enviar(id, "token-ana", texto("Otro"))
            .andExpect(status().isConflict())
            .andExpect(content().json("{\"chat\": \"El servicio ya finalizó; el chat queda solo para leer.\"}", true));
    }

    @Test
    void pendienteYRechazadaNoTienenChat() throws Exception {
        long pendiente = solicitud("pendiente");
        long rechazada = solicitud("rechazada");
        leer(pendiente, "token-cliente").andExpect(status().isConflict())
            .andExpect(jsonPath("$.chat").value("El chat se abre cuando el prestador acepta la solicitud."));
        enviar(rechazada, "token-ana", texto("Hola")).andExpect(status().isConflict())
            .andExpect(jsonPath("$.chat").value("La solicitud fue rechazada y no tiene chat."));
        assertEquals(0, jdbc.queryForObject("select count(*) from servicios_mensaje", Integer.class));
    }

    @Test
    void soloLasPartesEntran() throws Exception {
        long id = solicitud("aceptada");
        mvc.perform(get(API + id + "/chat/")).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.detail").value("Authentication credentials were not provided."));
        enviar(id, null, texto("Hola")).andExpect(status().isForbidden());
        for (String token : new String[] {"token-intruso", "token-eva"}) {
            leer(id, token).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No Solicitud matches the given query."));
            enviar(id, token, texto("Hola")).andExpect(status().isNotFound());
        }
        leer(999, "token-cliente").andExpect(status().isNotFound());
        assertEquals(0, jdbc.queryForObject("select count(*) from servicios_mensaje", Integer.class));
    }

    @Test
    void validacionDelTexto() throws Exception {
        long id = solicitud("en_camino");
        String[][] casos = {
            {"{}", "{\"texto\": [\"This field is required.\"]}"},
            {texto(""), "{\"texto\": [\"Escribe un mensaje.\"]}"},
            {texto("   \\n"), "{\"texto\": [\"Escribe un mensaje; no uses solo espacios.\"]}"},
            {"{\"texto\": null}", "{\"texto\": [\"This field may not be null.\"]}"},
            {texto("x".repeat(501)), "{\"texto\": [\"Ensure this field has no more than 500 characters.\"]}"},
            {"{\"texto\": [\"Hola\"]}", "{\"texto\": [\"Not a valid string.\"]}"},
            {"{\"texto\": true}", "{\"texto\": [\"Not a valid string.\"]}"},
            {texto("a\\u0000"), "{\"texto\": [\"Null characters are not allowed.\"]}"},
            {texto("x".repeat(501) + "\\u0000"),
                "{\"texto\": [\"Ensure this field has no more than 500 characters.\", \"Null characters are not allowed.\"]}"},
            {"\"hola\"", "{\"non_field_errors\": [\"Invalid data. Expected a dictionary, but got str.\"]}"},
        };
        for (String[] caso : casos) {
            enviar(id, "token-cliente", caso[0]).andExpect(status().isBadRequest()).andExpect(content().json(caso[1], true));
        }
        enviar(id, "token-cliente", texto("x".repeat(500))).andExpect(status().isCreated());
        enviar(id, "token-cliente", "{\"texto\": 12}").andExpect(jsonPath("$.texto").value("12"));
        enviar(id, "token-cliente", texto("  Hola  ")).andExpect(jsonPath("$.texto").value("  Hola  "));
    }

    @Test
    void empresaSeMuestraConSuNombreComercial() throws Exception {
        long id = solicitud("aceptada");
        jdbc.update("update prestadores_prestador set tipo = 'empresa', nombre_comercial = 'Gasfiteros SAC' where id = 1");
        leer(id, "token-cliente").andExpect(jsonPath("$.contraparte.nombre").value("Gasfiteros SAC"));
        jdbc.update("update prestadores_prestador set telefono = '' where id = 1");
        leer(id, "token-cliente").andExpect(jsonPath("$.contraparte.telefono").value(nullValue()));
    }
}
