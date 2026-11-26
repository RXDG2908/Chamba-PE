package pe.tecsup.chambape.prestadores;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * La web de usuario entra a Spring con la sesión de Django, como SessionAuthentication de DRF.
 * Las sesiones y el token CSRF los generó Django (SessionStore.encode y _mask_cipher_secret) con
 * la clave de prueba de abajo y la contraseña «!» de las cuentas de PruebaConBase.
 */
@SpringBootTest(properties = "chambape.django.secret-key=clave-de-prueba-spring")
@AutoConfigureMockMvc
class SesionWebApiTest extends PruebaConBase {

    private static final String SESION_CLIENTE = ".eJxVjEsKAjEQBe-StQz5f1w6R_AAodPdkCBmYDKzEu-ugwi6elCvqIfIsG8174PX3EichZPi9AsL4I378QDdW29jWwHb0qcB_Vge0-WjzEu_ftl_o8Ko7wAWVh4YQaXERqMupCx5iaSDN8qRctbGgMaT8Q4jRvApgnRJFx1IiecLGsM5jQ:1xWd7c:tcIGXBsMCRPQGxF-tuDO46ZYvJGYrW4ht0gwjF6Yxvo";
    private static final String CSRF = "abcdefghijklmnopqrstuvwxyzABCDEF";
    private static final String CSRF_ENMASCARADO = "e9vWaPr9FyN1aokuzCuQVbwKi1zAaUiaeaxZeUxgNHXcmByJPTM9fwS7GqZ1CnMF";
    private static final String MIS_SERVICIOS = "/api/calificaciones/mis-servicios/";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private Notificador notificador;

    @BeforeEach
    void clienteConSesion() {
        cuenta(50, "cliente_web");
        jdbc.update("insert into usuarios_perfil (rol, usuario_id, firebase_uid) values ('cliente', 50, null)");
        sesion("vigente", SESION_CLIENTE, "2999-01-01 00:00:00");
        sesion("vencida", SESION_CLIENTE, "2020-01-01 00:00:00");
        sesion("alterada", SESION_CLIENTE.substring(0, SESION_CLIENTE.length() - 1) + "x", "2999-01-01 00:00:00");
    }

    private static void sesion(String clave, String datos, String vence) {
        jdbc.update("insert into django_session (session_key, session_data, expire_date) values (?, ?, ?)", clave, datos, vence);
    }

    private static MockHttpServletRequestBuilder conSesion(MockHttpServletRequestBuilder peticion, String clave) {
        return peticion.cookie(new Cookie("sessionid", clave));
    }

    @Test
    void sesionVigenteAutenticaComoElCliente() throws Exception {
        mvc.perform(conSesion(get(MIS_SERVICIOS), "vigente")).andExpect(status().isOk());
    }

    @Test
    void sesionVencidaAlteradaOConOtraContrasenaNoAutentica() throws Exception {
        for (String clave : new String[] {"vencida", "alterada", "inexistente"}) {
            mvc.perform(conSesion(get(MIS_SERVICIOS), clave)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Authentication credentials were not provided."));
        }
        // Cambiar la contraseña cierra las sesiones abiertas (hash de la sesión).
        jdbc.update("update auth_user set password = 'otra' where id = 50");
        mvc.perform(conSesion(get(MIS_SERVICIOS), "vigente")).andExpect(status().isForbidden());
    }

    @Test
    void escriturasConSesionExigenCsrf() throws Exception {
        mvc.perform(conSesion(post("/api/calificaciones/"), "vigente").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.detail").value("CSRF Failed: CSRF cookie not set."));
        mvc.perform(conSesion(post("/api/calificaciones/"), "vigente").cookie(new Cookie("csrftoken", CSRF))
                .header("X-CSRFToken", "b".repeat(64)).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.detail").value("CSRF Failed: CSRF token from the 'X-Csrftoken' HTTP header incorrect."));
        mvc.perform(conSesion(post("/api/calificaciones/"), "vigente").cookie(new Cookie("csrftoken", CSRF))
                .header("X-CSRFToken", CSRF_ENMASCARADO).header("Origin", "http://otro-sitio.example")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.detail").value(
                "CSRF Failed: Origin checking failed - http://otro-sitio.example does not match any trusted origins."));
        // Con el token correcto y el origen de la web pasa a validar los datos.
        mvc.perform(conSesion(post("/api/calificaciones/"), "vigente").cookie(new Cookie("csrftoken", CSRF))
                .header("X-CSRFToken", CSRF_ENMASCARADO).header("Origin", "http://localhost:5173")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.solicitud[0]").value("Indica qué solicitud calificas."));
    }
}
