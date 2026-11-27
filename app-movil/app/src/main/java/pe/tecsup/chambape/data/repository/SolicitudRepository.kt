package pe.tecsup.chambape.data.repository

import com.google.gson.JsonParseException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import pe.tecsup.chambape.data.auth.AuthRepository
import pe.tecsup.chambape.data.remote.ApiService
import pe.tecsup.chambape.data.remote.AvanzarDto
import pe.tecsup.chambape.data.remote.CalificarDto
import pe.tecsup.chambape.data.remote.CrearSolicitudDto
import pe.tecsup.chambape.data.remote.EtapaDto
import pe.tecsup.chambape.data.remote.MiServicioDto
import pe.tecsup.chambape.data.remote.RegistroClienteDto
import pe.tecsup.chambape.data.remote.SolicitudRecibidaDto
import pe.tecsup.chambape.domain.model.ErrorDatos
import pe.tecsup.chambape.domain.model.EstadoSolicitud
import pe.tecsup.chambape.domain.model.Etapa
import pe.tecsup.chambape.domain.model.NuevaSolicitud
import pe.tecsup.chambape.domain.model.Prestador
import pe.tecsup.chambape.domain.model.Rol
import pe.tecsup.chambape.domain.model.Sesion
import pe.tecsup.chambape.domain.model.Solicitud
import pe.tecsup.chambape.domain.model.TipoError
import retrofit2.HttpException
import java.io.IOException
import java.util.Locale

interface SolicitudRepository {
    /** Solicitudes de la sesión, al día: las que envió el cliente o las que recibió el prestador. */
    fun deLaSesion(sesion: Sesion): Flow<List<Solicitud>>
    suspend fun crear(sesion: Sesion, prestador: Prestador, datos: NuevaSolicitud)
    suspend fun responder(solicitudId: Int, aceptar: Boolean)
    suspend fun avanzar(solicitudId: Int, estado: EstadoSolicitud)
    suspend fun calificar(solicitudId: Int, estrellas: Int, comentario: String)
}

/**
 * Solicitudes en la API Django, las mismas que ve la web: lo que pide el cliente desde la app
 * llega a la bandeja web del prestador y al revés. La app se identifica con el token de Firebase.
 *
 * Un cliente que se registró en la app todavía no tiene cuenta en Django: si la API no lo
 * reconoce, se crea su cuenta de cliente (registro-movil, idempotente) y se reintenta una vez.
 * El estado se consulta cada [intervaloMs], como la web (HU-11).
 */
class ApiSolicitudRepository(
    private val api: ApiService,
    private val auth: AuthRepository,
    private val intervaloMs: Long = 5_000,
) : SolicitudRepository {

    override fun deLaSesion(sesion: Sesion): Flow<List<Solicitud>> = flow {
        while (true) {
            emit(
                when (sesion.rol) {
                    Rol.CLIENTE -> comoCliente(sesion) { api.misServicios(it) }.mapNotNull { it.aModelo() }
                    Rol.PRESTADOR -> llamar { api.solicitudesRecibidas(it) }.mapNotNull { it.aModelo() }
                },
            )
            delay(intervaloMs)
        }
    }

    override suspend fun crear(sesion: Sesion, prestador: Prestador, datos: NuevaSolicitud) {
        val cuerpo = CrearSolicitudDto(
            prestador = prestador.id,
            rubro = datos.rubroId,
            clienteNombre = sesion.nombre.ifBlank { sesion.correo }.take(100),
            clienteTelefono = datos.telefono,
            descripcion = datos.descripcion.trim(),
            latitud = String.format(Locale.ROOT, "%.6f", datos.latitud),
            longitud = String.format(Locale.ROOT, "%.6f", datos.longitud),
        )
        comoCliente(sesion) { api.crearSolicitud(it, cuerpo) }
    }

    override suspend fun responder(solicitudId: Int, aceptar: Boolean) {
        llamar { if (aceptar) api.aceptarSolicitud(it, solicitudId) else api.rechazarSolicitud(it, solicitudId) }
    }

    override suspend fun avanzar(solicitudId: Int, estado: EstadoSolicitud) {
        llamar { api.avanzarSolicitud(it, solicitudId, AvanzarDto(estado.api)) }
    }

    override suspend fun calificar(solicitudId: Int, estrellas: Int, comentario: String) {
        llamar { api.calificar(it, CalificarDto(solicitudId, estrellas, comentario.trim())) }
    }

    /** Como [llamar], pero si Django aún no conoce a este cliente crea su cuenta y reintenta. */
    private suspend fun <T> comoCliente(sesion: Sesion, peticion: suspend (String) -> T): T = try {
        llamar(peticion)
    } catch (e: ErrorDatos) {
        if (e.tipo != TipoError.CUENTA_NO_VINCULADA) throw e
        llamar { api.registrarClienteMovil(it, RegistroClienteDto(sesion.nombre)) }
        llamar(peticion)
    }

    private suspend fun <T> llamar(peticion: suspend (String) -> T): T {
        val token = auth.tokenDeAcceso() ?: throw ErrorDatos(TipoError.SIN_CONEXION)
        return try {
            peticion("Bearer $token")
        } catch (e: IOException) {
            throw ErrorDatos(TipoError.SIN_CONEXION, e)
        } catch (e: HttpException) {
            // 401/403: Django no reconoce esta cuenta de la app; 409: otra pestaña ya la respondió.
            throw ErrorDatos(if (e.code() == 401 || e.code() == 403) TipoError.CUENTA_NO_VINCULADA else TipoError.SERVIDOR, e)
        } catch (e: JsonParseException) {
            throw ErrorDatos(TipoError.DATOS_INVALIDOS, e)
        }
    }
}

private fun List<EtapaDto>?.aEtapas(): List<Etapa> =
    orEmpty().mapNotNull { e -> EstadoSolicitud.de(e.estado)?.let { Etapa(it, e.fecha.orEmpty()) } }

internal fun MiServicioDto.aModelo(): Solicitud? {
    val estado = EstadoSolicitud.de(estado) ?: return null
    return Solicitud(
        id = id ?: return null,
        contraparte = prestador?.nombre.orEmpty(),
        telefono = null,
        rubro = rubro.orEmpty(),
        descripcion = descripcion.orEmpty(),
        estado = estado,
        estadoTexto = estadoDisplay ?: estado.api,
        historial = historial.aEtapas(),
        puedeCalificar = puedeCalificar == true,
        estrellas = calificacion?.estrellas,
    )
}

internal fun SolicitudRecibidaDto.aModelo(): Solicitud? {
    val estado = EstadoSolicitud.de(estado) ?: return null
    return Solicitud(
        id = id ?: return null,
        contraparte = clienteNombre.orEmpty(),
        telefono = clienteTelefono,
        rubro = rubro.orEmpty(),
        descripcion = descripcion.orEmpty(),
        estado = estado,
        estadoTexto = estadoDisplay ?: estado.api,
        siguienteEstado = EstadoSolicitud.de(siguienteEstado),
        historial = historial.aEtapas(),
    )
}

/**
 * Rubro y ubicación de la última búsqueda: la solicitud se envía con ellos (Django comprueba que
 * el prestador ofrezca ese rubro y cubra esa ubicación). Vive mientras la app está abierta.
 */
class ContextoBusqueda {
    /** [direccion]: la línea que vio el cliente («Av. Arequipa 1200, Lince»); vacía si no tiene nombre. */
    data class Datos(val rubroId: Int, val latitud: Double, val longitud: Double, val direccion: String = "")

    @Volatile
    var ultima: Datos? = null
}
