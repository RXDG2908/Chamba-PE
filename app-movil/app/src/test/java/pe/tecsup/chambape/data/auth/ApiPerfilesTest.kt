package pe.tecsup.chambape.data.auth

import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import pe.tecsup.chambape.data.remote.ApiService
import pe.tecsup.chambape.data.remote.UsuarioDto
import pe.tecsup.chambape.domain.model.ErrorAuth
import pe.tecsup.chambape.domain.model.Rol
import pe.tecsup.chambape.domain.model.TipoErrorAuth
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

/** El perfil (rol y prestador) sale de Django, no de Firestore: migración del 26/11. */
class ApiPerfilesTest {

    /** ApiService falso: cada método responde con lo que diga [respuestas] (o lanza lo que devuelva). */
    private fun api(respuestas: Map<String, () -> Any>): ApiService =
        Proxy.newProxyInstance(ApiService::class.java.classLoader, arrayOf(ApiService::class.java)) { _, metodo, args ->
            llamadas += metodo.name to (args?.firstOrNull() as? String)
            val r = respuestas[metodo.name]?.invoke() ?: error("No se esperaba ${metodo.name}")
            if (r is Throwable) {
                // Un método suspend no declara excepciones: Proxy envolvería la IOException. Se reanuda
                // la corrutina con el error, como hace Retrofit.
                @Suppress("UNCHECKED_CAST")
                (args!!.last() as Continuation<Any?>).resumeWith(Result.failure(r))
                COROUTINE_SUSPENDED
            } else {
                r
            }
        } as ApiService

    private val llamadas = mutableListOf<Pair<String, String?>>()

    private fun http(codigo: Int, cuerpo: String) =
        HttpException(Response.error<Any>(codigo, cuerpo.toResponseBody("application/json".toMediaType())))

    @Test
    fun `prestador vinculado trae su id sin vinculo manual`() = runTest {
        val perfiles = ApiPerfiles(api(mapOf("yo" to { UsuarioDto("a@x.pe", "Ana", "Pérez", "prestador", 7) })))
        assertEquals(PerfilRemoto("Ana Pérez", Rol.PRESTADOR, 7), perfiles.perfil("tk"))
        assertEquals("yo" to "Bearer tk", llamadas.single())
    }

    @Test
    fun `cuenta sin vincular devuelve null y perfilOCliente la crea como cliente`() = runTest {
        val perfiles = ApiPerfiles(api(mapOf(
            "yo" to { http(403, """{"detail":"Esta cuenta de la app aún no está vinculada.","codigo":"cuenta_no_vinculada"}""") },
            "registrarClienteMovil" to { UsuarioDto("c@x.pe", "Carla", "", "cliente", null) },
        )))
        assertNull(perfiles.perfil("tk"))
        assertEquals(PerfilRemoto("Carla", Rol.CLIENTE, null), perfiles.perfilOCliente("tk", "carla"))
        assertEquals(listOf("yo", "yo", "registrarClienteMovil"), llamadas.map { it.first })
    }

    @Test
    fun `cuenta sancionada no se convierte en cliente`() = runTest {
        val perfiles = ApiPerfiles(api(mapOf(
            "yo" to { http(403, """{"detail":"Tu cuenta está bloqueada.","codigo":"cuenta_sancionada"}""") },
        )))
        assertError(TipoErrorAuth.CUENTA_SANCIONADA) { perfiles.perfilOCliente("tk", "x") }
        assertTrue(llamadas.none { it.first == "registrarClienteMovil" })
    }

    @Test
    fun `sin red o error del servidor se traducen`() = runTest {
        assertError(TipoErrorAuth.SIN_CONEXION) { ApiPerfiles(api(mapOf("yo" to { IOException("sin red") }))).perfil("tk") }
        assertError(TipoErrorAuth.DESCONOCIDO) { ApiPerfiles(api(mapOf("yo" to { http(500, "{}") }))).perfil("tk") }
        // 403 sin código (cuerpo inesperado): no se asume que se pueda crear la cuenta.
        assertError(TipoErrorAuth.DESCONOCIDO) { ApiPerfiles(api(mapOf("yo" to { http(403, "<html>") }))).perfil("tk") }
    }

    @Test
    fun `correo con cuenta web sin vincular se informa como correo en uso`() = runTest {
        val perfiles = ApiPerfiles(api(mapOf("registrarClienteMovil" to { http(400, """{"email":["Ya existe"]}""") })))
        assertError(TipoErrorAuth.CORREO_EN_USO) { perfiles.crearCliente("tk", "Ana") }
    }

    @Test
    fun `sin nombres usa la parte del correo`() = runTest {
        val perfiles = ApiPerfiles(api(mapOf("yo" to { UsuarioDto("carla@x.pe", null, null, "cliente", null) })))
        assertEquals("carla", perfiles.perfil("tk")?.nombre)
    }

    private suspend fun assertError(tipo: TipoErrorAuth, bloque: suspend () -> Unit) {
        try {
            bloque()
            fail("Se esperaba ErrorAuth($tipo)")
        } catch (e: ErrorAuth) {
            assertEquals(tipo, e.tipo)
        }
    }
}
