package pe.tecsup.chambape.prestadores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import pe.tecsup.chambape.servicios.Notificador;
import pe.tecsup.chambape.usuarios.VerificadorFirebase;
import pe.tecsup.chambape.usuarios.VerificadorFirebase.Identidad;

/**
 * Misma cuenta en la web y la app: {@code POST /api/usuarios/vincular/} con las reglas de Django
 * (usuarios/test_cuentas_compartidas.py). La contraseña se compara con el hash PBKDF2 de Django.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD) // límite de intentos en memoria
class VincularCuentaApiTest extends PruebaConBase {

    private static final String RUTA = "/api/usuarios/vincular/";
    // make_password('clave-de-la-web-1', salt='salPrueba123') de Django 6.
    private static final String HASH = "pbkdf2_sha256$1500000$salPrueba123$2mBxrSufuRq2/8Pbx3dOOFBwVjsyTM6Gfw5nb0inIIw=";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private VerificadorFirebase verificador;

    @MockitoBean
    private Notificador notificador;

    @BeforeEach
    void cuentaDeLaWeb() {
        when(verificador.configurado()).thenReturn(true);
        identidad("token-lucia", "uid-lucia", "Lucia@Ejemplo.pe");
        identidad("token-otro", "uid-otro", "lucia@ejemplo.pe");
        identidad("token-nadie", "uid-nadie", "nadie@ejemplo.pe");
        cuenta(60, "lucia");
        jdbc.update("update auth_user set email = 'lucia@ejemplo.pe', password = ?, first_name = 'Lucía' where id = 60", HASH);
        jdbc.update("insert into usuarios_perfil (rol, usuario_id, firebase_uid) values ('cliente', 60, null)");
    }

    private void identidad(String token, String uid, String correo) {
        when(verificador.identidad(token)).thenReturn(Optional.of(new Identidad(uid, correo, null)));
        when(verificador.uid(token)).thenReturn(Optional.of(uid));
    }

    private ResultActions vincular(String token, String clave) throws Exception {
        return mvc.perform(post(RUTA).header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"password\": \"" + clave + "\"}"));
    }

    private String uidVinculado() {
        return jdbc.queryForObject("select firebase_uid from usuarios_perfil where usuario_id = 60", String.class);
    }

    @Test
    void conLaContrasenaDeLaWebVinculaYDevuelveLaCuenta() throws Exception {
        vincular("token-lucia", "clave-de-la-web-1").andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("lucia")).andExpect(jsonPath("$.nombres").value("Lucía"))
            .andExpect(jsonPath("$.rol").value("cliente"));
        assertEquals("uid-lucia", uidVinculado());
        // Otra cuenta de Firebase ya no puede tomarla; la misma tampoco vincula dos veces.
        vincular("token-otro", "clave-de-la-web-1").andExpect(status().isConflict());
        vincular("token-lucia", "clave-de-la-web-1").andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("Esta cuenta de la app ya está vinculada."));
    }

    @Test
    void elCorreoSoloNoBastaYUnCorreoSinCuentaDaElMismoMensaje() throws Exception {
        String mala = vincular("token-lucia", "otra").andExpect(status().isBadRequest())
            .andReturn().getResponse().getContentAsString();
        String nadie = vincular("token-nadie", "clave-de-la-web-1").andExpect(status().isBadRequest())
            .andReturn().getResponse().getContentAsString();
        assertEquals(mala, nadie);
        assertNull(uidVinculado());
    }

    @Test
    void diezFallosBloqueanEseCorreo() throws Exception {
        for (int i = 0; i < 10; i++) {
            vincular("token-lucia", "mala").andExpect(status().isBadRequest());
        }
        vincular("token-lucia", "clave-de-la-web-1").andExpect(status().isTooManyRequests());
        assertNull(uidVinculado());
    }

    @Test
    void sinTokenValidoNoHaceNada() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("{\"password\": \"x\"}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.detail").value("El token no es válido o venció."));
    }

    @Test
    void comparaElHashDeDjango() {
        assertTrue(pe.tecsup.chambape.usuarios.VincularCuentaControllerAcceso.contrasena("clave-de-la-web-1", HASH));
        assertFalse(pe.tecsup.chambape.usuarios.VincularCuentaControllerAcceso.contrasena("Clave-de-la-web-1", HASH));
        assertFalse(pe.tecsup.chambape.usuarios.VincularCuentaControllerAcceso.contrasena("!", "!xYz"));
    }
}
