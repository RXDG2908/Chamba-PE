package pe.tecsup.chambape.prestadores;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Un error inesperado responde como DRF y no muestra el detalle (queda en el registro). */
@SpringBootTest
@AutoConfigureMockMvc
class ErrorInternoTest extends PruebaConBase {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private BusquedaService busqueda;

    @Test
    void errorInesperadoDa500SinDetalles() throws Exception {
        when(busqueda.buscar(any(), any(), any())).thenThrow(new IllegalStateException("detalle-interno"));
        mvc.perform(get("/api/prestadores/buscar/?rubro=1&latitud=0&longitud=0"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.detail").value("A server error occurred."))
            .andExpect(content().string(not(containsString("detalle-interno"))));
    }
}
