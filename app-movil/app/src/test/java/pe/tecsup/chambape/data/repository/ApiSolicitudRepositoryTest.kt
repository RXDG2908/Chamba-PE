package pe.tecsup.chambape.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import pe.tecsup.chambape.data.auth.AuthRepository
import pe.tecsup.chambape.data.remote.ApiService
import pe.tecsup.chambape.data.remote.AvanzarDto
import pe.tecsup.chambape.data.remote.CalificarDto
import pe.tecsup.chambape.data.remote.CambioDisponibilidadDto
import pe.tecsup.chambape.data.remote.CalificacionDto
import pe.tecsup.chambape.data.remote.CrearSolicitudDto
import pe.tecsup.chambape.data.remote.DisponibilidadDto
import pe.tecsup.chambape.data.remote.EtapaDto
import pe.tecsup.chambape.data.remote.MiServicioDto
import pe.tecsup.chambape.data.remote.PrestadorDto
import pe.tecsup.chambape.data.remote.PrestadorResumenDto
import pe.tecsup.chambape.data.remote.RegistroClienteDto
import pe.tecsup.chambape.data.remote.RegistroPrestadorDto
import pe.tecsup.chambape.data.remote.RubroDto
import pe.tecsup.chambape.data.remote.SolicitudCreadaDto
import pe.tecsup.chambape.data.remote.SolicitudRecibidaDto
import pe.tecsup.chambape.domain.model.ErrorDatos
import pe.tecsup.chambape.domain.model.EstadoSesion
import pe.tecsup.chambape.domain.model.EstadoSolicitud
import pe.tecsup.chambape.domain.model.NuevaSolicitud
import pe.tecsup.chambape.domain.model.Prestador
import pe.tecsup.chambape.domain.model.Rol
import pe.tecsup.chambape.domain.model.Sesion
import pe.tecsup.chambape.domain.model.TipoError
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

private fun http(codigo: Int) = HttpException(Response.error<Any>(codigo, "{}".toResponseBody(null)))

private val CLIENTE = Sesion("uid-carla", "carla@example.invalid", "Carla", Rol.CLIENTE, null)
private val PRESTADOR = Sesion("uid-rosa", "rosa@example.invalid", "Rosa", Rol.PRESTADOR, 7)

/** API falsa: solo lo que usan las solicitudes; el resto no debe llamarse. */
private class ApiFalsa : ApiService {
    var clienteRegistrado = false
    var registros = 0
    var caida: Throwable? = null
    val creadas = mutableListOf<CrearSolicitudDto>()
    val avances = mutableListOf<Pair<Int, String>>()
    var servicios = listOf<MiServicioDto>()
    var recibidas = listOf<SolicitudRecibidaDto>()
    val autorizaciones = mutableListOf<String>()

    private fun comoCliente(autorizacion: String) {
        autorizaciones += autorizacion
        caida?.let { throw it }
        if (!clienteRegistrado) throw http(403)
    }

    override suspend fun registrarClienteMovil(autorizacion: String, datos: RegistroClienteDto) {
        registros++
        clienteRegistrado = true
    }

    override suspend fun crearSolicitud(autorizacion: String, datos: CrearSolicitudDto): SolicitudCreadaDto {
        comoCliente(autorizacion)
        creadas += datos
        return SolicitudCreadaDto(creadas.size)
    }

    override suspend fun misServicios(autorizacion: String): List<MiServicioDto> {
        comoCliente(autorizacion)
        return servicios
    }

    override suspend fun solicitudesRecibidas(autorizacion: String) = recibidas.also { caida?.let { throw it } }
    override suspend fun aceptarSolicitud(autorizacion: String, id: Int): SolicitudRecibidaDto = throw http(409)
    override suspend fun rechazarSolicitud(autorizacion: String, id: Int): SolicitudRecibidaDto = error("no usado")
    override suspend fun avanzarSolicitud(autorizacion: String, id: Int, datos: AvanzarDto): SolicitudRecibidaDto {
        avances += id to datos.estado
        return recibidas.first()
    }

    override suspend fun calificar(autorizacion: String, datos: CalificarDto) = Unit
    override suspend fun rubros(): List<RubroDto> = error("no usado")
    override suspend fun buscar(rubroId: Int, latitud: Double, longitud: Double): List<PrestadorDto> = error("no usado")
    override suspend fun registrarPrestador(autorizacion: String, datos: RegistroPrestadorDto): ResponseBody = error("no usado")
    override suspend fun disponibilidad(autorizacion: String): DisponibilidadDto = error("no usado")
    override suspend fun cambiarDisponibilidad(autorizacion: String, cambio: CambioDisponibilidadDto): DisponibilidadDto =
        error("no usado")
}

private class AuthFalsa(var token: String? = "tk") : AuthRepository {
    override val sesion: StateFlow<EstadoSesion> = MutableStateFlow(EstadoSesion.SinSesion)
    override suspend fun ingresar(correo: String, clave: String) = Unit
    override suspend fun registrarCliente(nombre: String, correo: String, clave: String) = Unit
    override suspend fun registrarPrestador(
        nombre: String, correo: String, clave: String, registrarEnApi: suspend (token: String) -> Unit,
    ) = Unit
    override fun cerrarSesion() = Unit
    override suspend fun tokenDeAcceso() = token
}

class ApiSolicitudRepositoryTest {
    private val api = ApiFalsa()
    private val auth = AuthFalsa()
    private val repo = ApiSolicitudRepository(api, auth, intervaloMs = 1)
    private val rosa = Prestador(7, "Rosa", "Quispe", listOf("Gasfitería"), -12.0, -77.0, 1.0, favorito = false)

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
    fun cliente_nuevo_se_registra_en_django_y_reintenta_una_vez() = runTest {
        repo.crear(CLIENTE, rosa, NuevaSolicitud(3, "  Fuga en la cocina  ", "987654321", -12.0463741, -77.0427934))

        assertEquals(1, api.registros)
        val enviada = api.creadas.single()
        assertEquals(Triple(7, 3, "987654321"), Triple(enviada.prestador, enviada.rubro, enviada.clienteTelefono))
        assertEquals("Fuga en la cocina", enviada.descripcion)
        // Seis decimales con punto, sin importar el idioma del teléfono.
        assertEquals("-12.046374" to "-77.042793", enviada.latitud to enviada.longitud)
        assertTrue(api.autorizaciones.all { it == "Bearer tk" })
    }

    @Test
    fun cliente_ya_registrado_no_vuelve_a_registrarse() = runTest {
        api.clienteRegistrado = true
        repo.crear(CLIENTE, rosa, NuevaSolicitud(3, "Fuga en la cocina", "987654321", -12.0, -77.0))
        assertEquals(0, api.registros)
    }

    @Test
    fun errores_de_red_servidor_y_sin_token() = runTest {
        api.caida = IOException("sin red")
        assertEquals(TipoError.SIN_CONEXION, error { repo.deLaSesion(CLIENTE).first() })
        api.caida = http(500)
        assertEquals(TipoError.SERVIDOR, error { repo.deLaSesion(PRESTADOR).first() })
        assertEquals(TipoError.SERVIDOR, error { repo.responder(1, aceptar = true) }) // 409: ya respondida
        auth.token = null
        assertEquals(TipoError.SIN_CONEXION, error { repo.calificar(1, 5, "") })
    }

    @Test
    fun mapea_mis_servicios_e_ignora_estados_desconocidos() = runTest {
        api.clienteRegistrado = true
        api.servicios = listOf(
            MiServicioDto(
                1, PrestadorResumenDto(7, "Rosa Quispe"), "Gasfitería", "Fuga", "finalizada", "Finalizada",
                listOf(EtapaDto("aceptada", "f1"), EtapaDto("raro", "f2"), EtapaDto("finalizada", null)),
                null, true,
            ),
            MiServicioDto(2, null, null, null, "inventado", null, null, null, null),
            MiServicioDto(3, null, "Electricidad", "Corto", "finalizada", null, null, CalificacionDto(4), false),
        )
        val lista = repo.deLaSesion(CLIENTE).first()

        assertEquals(listOf(1, 3), lista.map { it.id })
        val primera = lista[0]
        assertEquals("Rosa Quispe", primera.contraparte)
        assertEquals(listOf(EstadoSolicitud.ACEPTADA, EstadoSolicitud.FINALIZADA), primera.historial.map { it.estado })
        assertTrue(primera.puedeCalificar)
        assertNull(primera.telefono)
        assertFalse(lista[1].puedeCalificar)
        assertEquals(4, lista[1].estrellas)
        assertEquals("finalizada", lista[1].estadoTexto)
    }

    @Test
    fun prestador_ve_telefono_y_siguiente_etapa_y_avanza() = runTest {
        api.recibidas = listOf(
            SolicitudRecibidaDto(9, "Gasfitería", "Carla", "987654321", "Fuga", "aceptada", "Aceptada", "en_camino", null),
        )
        val s = repo.deLaSesion(PRESTADOR).first().single()
        assertEquals("987654321", s.telefono)
        assertEquals(EstadoSolicitud.EN_CAMINO, s.siguienteEstado)

        repo.avanzar(9, EstadoSolicitud.EN_CAMINO)
        assertEquals(listOf(9 to "en_camino"), api.avances)
    }
}
