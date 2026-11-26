package pe.tecsup.chambape.prestadores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import pe.tecsup.chambape.servicios.Notificador;

/**
 * HU-8: el prestador registra y quita el token FCM de su navegador con la sesión de Django, como
 * {@code TokenFCMView}. Usa la misma sesión firmada de {@link SesionWebApiTest} (cuenta 50), aquí de un prestador.
 */
@SpringBootTest(properties = "chambape.django.secret-key=clave-de-prueba-spring")
@AutoConfigureMockMvc
class NotificacionesApiTest extends PruebaConBase {

    private static final String SESION = ".eJxVjEsKAjEQBe-StQz5f1w6R_AAodPdkCBmYDKzEu-ugwi6elCvqIfIsG8174PX3EichZPi9AsL4I378QDdW29jWwHb0qcB_Vge0-WjzEu_ftl_o8Ko7wAWVh4YQaXERqMupCx5iaSDN8qRctbGgMaT8Q4jRvApgnRJFx1IiecLGsM5jQ:1xWd7c:tcIGXBsMCRPQGxF-tuDO46ZYvJGYrW4ht0gwjF6Yxvo";
    private static final String CSRF = "abcdefghijklmnopqrstuvwxyzABCDEF";
    private static final String CSRF_ENMASCARADO = "e9vWaPr9FyN1aokuzCuQVbwKi1zAaUiaeaxZeUxgNHXcmByJPTM9fwS7GqZ1CnMF";
    private static final String RUTA = "/api/servicios/notificaciones/token/";
    private static final String TOKEN = "fcm-de-prueba_Ab9:-" + "x".repeat(30);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private Notificador notificador;

    @BeforeEach
    void prestadorConSesion() {
        prestador(50, "Web", "-12.044861", "-77.026582", 5, true, "verificado", 1);
        jdbc.update("insert into usuarios_perfil (rol, usuario_id, firebase_uid) values ('prestador', 50, null)");
        jdbc.update("insert into django_session (session_key, session_data, expire_date) values ('vigente', ?, '2999-01-01 00:00:00')",
            SESION);
    }

    private static MockHttpServletRequestBuilder web(MockHttpServletRequestBuilder peticion, String cuerpo) {
        return peticion.cookie(new Cookie("sessionid", "vigente"), new Cookie("csrftoken", CSRF))
            .header("X-CSRFToken", CSRF_ENMASCARADO).header("Origin", "http://localhost:5173")
            .contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    private static int tokens() {
        return jdbc.queryForObject("select count(*) from servicios_tokenfcm where usuario_id = 50 and sesion = 'vigente'",
            Integer.class);
    }

    @Test
    void registraUnaVezYLuegoLoQuita() throws Exception {
        for (int vez = 0; vez < 2; vez++) {
            mvc.perform(web(post(RUTA), "{\"token\": \"  " + TOKEN + "\\n\"}"))
                .andExpect(status().isOk()).andExpect(content().json("{\"registrado\": true}", true));
        }
        assertThat(tokens()).isEqualTo(1);
        mvc.perform(web(delete(RUTA), "{\"token\": \"" + TOKEN + "\"}")).andExpect(status().isNoContent())
            .andExpect(content().string(""));
        assertThat(tokens()).isZero();
    }

    @Test
    void validaElTokenComoDrf() throws Exception {
        mvc.perform(web(post(RUTA), "{}")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.token[0]").value("This field is required."));
        mvc.perform(web(post(RUTA), "{\"token\": \"corto!\"}")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.token[0]").value("Ensure this field has at least 20 characters."))
            .andExpect(jsonPath("$.token[1]").value("This value does not match the required pattern."));
        mvc.perform(web(post(RUTA), "{\"token\": true}")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.token[0]").value("This value does not match the required pattern."));
        assertThat(tokens()).isZero();
    }

    @Test
    void sinSesionOSinCsrfNoEntra() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("{\"token\": \"" + TOKEN + "\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.detail").value("Authentication credentials were not provided."));
        mvc.perform(post(RUTA).cookie(new Cookie("sessionid", "vigente")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"" + TOKEN + "\"}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.detail").value("CSRF Failed: CSRF cookie not set."));
        // Como en DRF, el permiso va antes que el método: el visitante ve 403 y el prestador 405.
        mvc.perform(get(RUTA)).andExpect(status().isForbidden());
        mvc.perform(get(RUTA).cookie(new Cookie("sessionid", "vigente"))).andExpect(status().isMethodNotAllowed())
            .andExpect(header().string("Allow", "POST, DELETE, OPTIONS"))
            .andExpect(jsonPath("$.detail").value("Method \"GET\" not allowed."));
    }
}
