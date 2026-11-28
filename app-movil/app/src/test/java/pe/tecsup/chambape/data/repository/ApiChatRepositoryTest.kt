package pe.tecsup.chambape.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import pe.tecsup.chambape.data.auth.AuthRepository
import pe.tecsup.chambape.data.remote.ApiService
import pe.tecsup.chambape.data.remote.ChatDto
import pe.tecsup.chambape.data.remote.ContraparteDto
import pe.tecsup.chambape.data.remote.MensajeDto
import pe.tecsup.chambape.data.remote.NuevoMensajeDto
import pe.tecsup.chambape.domain.model.ErrorDatos
import pe.tecsup.chambape.domain.model.EstadoSesion
import pe.tecsup.chambape.domain.model.TipoError
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

/** Chat de la solicitud contra la API (Spring y Django responden igual: servicios/test_chat.py). */
class ApiChatRepositoryTest {
    private val llamadas = mutableListOf<List<Any?>>()
    private var respuesta: () -> Any = { error("sin respuesta") }
    private var token: String? = "tk"

    /** ApiService falso: solo chat y enviarMensaje; lo demás no debe llamarse. */
    private val api = Proxy.newProxyInstance(ApiService::class.java.classLoader, arrayOf(ApiService::class.java)) { _, metodo, args ->
        require(metodo.name == "chat" || metodo.name == "enviarMensaje") { "No se esperaba ${metodo.name}" }
        llamadas += listOf(metodo.name) + args!!.dropLast(1)
        val r = respuesta()
        if (r is Throwable) {
            @Suppress("UNCHECKED_CAST")
            (args.last() as Continuation<Any?>).resumeWith(Result.failure(r))
            COROUTINE_SUSPENDED
        } else {
            r
        }
    } as ApiService

    private val auth = object : AuthRepository {
        override val sesion: StateFlow<EstadoSesion> = MutableStateFlow(EstadoSesion.SinSesion)
        override suspend fun ingresar(correo: String, clave: String) = Unit
        override suspend fun registrarCliente(nombre: String, correo: String, clave: String) = Unit
        override suspend fun registrarPrestador(
            nombre: String, correo: String, clave: String, registrarEnApi: suspend (token: String) -> Unit,
        ) = Unit
        override fun cerrarSesion() = Unit
        override suspend fun tokenDeAcceso() = token
    }

    private val repo = ApiChatRepository(api, auth)

    private fun http(codigo: Int) = HttpException(Response.error<Any>(codigo, "{}".toResponseBody(null)))

    private suspend fun error(bloque: suspend () -> Unit): TipoError {
        try {
            bloque()
        } catch (e: ErrorDatos) {
            return e.tipo
        }
        fail("se esperaba ErrorDatos")
        throw AssertionError()
    }

    @Test
    fun consulta_el_chat_y_marca_los_mensajes_propios() = runTest {
        respuesta = {
            ChatDto(
                41, "en_camino", "En camino", true, "cliente", ContraparteDto("Ana Pérez", "987654321"),
                listOf(
                    MensajeDto(1, "prestador", "Hola", "2026-11-28T15:01:00Z"),
                    MensajeDto(2, "cliente", "¿A qué hora?", "2026-11-28T15:02:00.123456Z"),
                    MensajeDto(null, "cliente", "sin id", null),
                ),
            )
        }
        val chat = repo.consultar(41)

        assertEquals(listOf("chat", "Bearer tk", 41), llamadas.single())
        assertEquals(41, chat.solicitudId)
        assertEquals("En camino", chat.estadoTexto)
        assertTrue(chat.activo)
        assertEquals("Ana Pérez" to "987654321", chat.contraparte to chat.telefono)
        assertEquals(listOf(false, true), chat.mensajes.map { it.mio })
        assertEquals(listOf("Hola", "¿A qué hora?"), chat.mensajes.map { it.texto })
    }

    @Test
    fun finalizado_sin_telefono_queda_solo_para_leer() = runTest {
        respuesta = { ChatDto(41, "finalizada", "Finalizada", false, "cliente", ContraparteDto("Ana", null), emptyList()) }
        val chat = repo.consultar(41)
        assertFalse(chat.activo)
        assertNull(chat.telefono)
        respuesta = { ChatDto(41, "aceptada", "Confirmado", true, "cliente", ContraparteDto("Ana", ""), emptyList()) }
        assertNull(repo.consultar(41).telefono)
    }

    @Test
    fun envia_el_texto_tal_cual() = runTest {
        respuesta = { MensajeDto(7, "prestador", "  Llego en 20  ", "2026-11-28T15:05:00Z") }
        val mensaje = repo.enviar(41, "  Llego en 20  ")
        assertEquals(listOf("enviarMensaje", "Bearer tk", 41, NuevoMensajeDto("  Llego en 20  ")), llamadas.single())
        assertEquals(7, mensaje.id)
        assertTrue(mensaje.mio)
    }

    @Test
    fun errores() = runTest {
        respuesta = { IOException("sin red") }
        assertEquals(TipoError.SIN_CONEXION, error { repo.consultar(1) })
        respuesta = { http(409) } // el chat aún no se abre o el servicio ya finalizó
        assertEquals(TipoError.SERVIDOR, error { repo.enviar(1, "Hola") })
        respuesta = { http(404) }
        assertEquals(TipoError.SERVIDOR, error { repo.consultar(1) })
        respuesta = { http(403) }
        assertEquals(TipoError.CUENTA_NO_VINCULADA, error { repo.consultar(1) })
        respuesta = { ChatDto(null, null, null, null, null, null, null) }
        assertEquals(TipoError.DATOS_INVALIDOS, error { repo.consultar(1) })
        token = null
        assertEquals(TipoError.SIN_CONEXION, error { repo.consultar(1) })
    }
}
