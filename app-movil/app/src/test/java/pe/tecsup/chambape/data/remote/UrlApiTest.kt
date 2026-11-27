package pe.tecsup.chambape.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlApiTest {

    @Test
    fun `en el emulador la PC local pasa a 10_0_2_2`() {
        assertEquals("http://10.0.2.2:8080/", RetrofitClient.urlParaEsteEquipo("http://127.0.0.1:8080/", enEmulador = true))
        assertEquals("http://10.0.2.2:8000/", RetrofitClient.urlParaEsteEquipo("http://localhost:8000/", enEmulador = true))
    }

    @Test
    fun `en un telefono o con una URL publica no cambia`() {
        assertEquals("http://127.0.0.1:8080/", RetrofitClient.urlParaEsteEquipo("http://127.0.0.1:8080/", enEmulador = false))
        assertEquals("https://api.chamba.pe/", RetrofitClient.urlParaEsteEquipo("https://api.chamba.pe/", enEmulador = true))
        // Un dominio que solo empieza parecido no se toca.
        assertEquals("http://localhost.example.com/", RetrofitClient.urlParaEsteEquipo("http://localhost.example.com/", enEmulador = true))
    }
}
