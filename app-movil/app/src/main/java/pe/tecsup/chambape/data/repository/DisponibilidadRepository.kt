package pe.tecsup.chambape.data.repository

import com.google.gson.JsonParseException
import pe.tecsup.chambape.data.auth.AuthRepository
import pe.tecsup.chambape.data.remote.ApiService
import pe.tecsup.chambape.data.remote.CambioDisponibilidadDto
import pe.tecsup.chambape.data.remote.DisponibilidadDto
import pe.tecsup.chambape.domain.model.Disponibilidad
import pe.tecsup.chambape.domain.model.ErrorDatos
import pe.tecsup.chambape.domain.model.EstadoVerificacion
import pe.tecsup.chambape.domain.model.TipoError
import retrofit2.HttpException
import java.io.IOException

/** Disponibilidad del prestador (Activo o Inactivo). Los fallos se lanzan como [ErrorDatos]. */
interface DisponibilidadRepository {
    suspend fun consultar(): Disponibilidad
    suspend fun cambiar(disponible: Boolean): Disponibilidad
}

/**
 * La disponibilidad vive en la API Django, la misma que usa la web y que filtra la búsqueda.
 * La app se identifica con el token de Firebase; Django lo acepta solo si un administrador
 * vinculó esa cuenta con la del prestador.
 */
class DisponibilidadRepositoryImpl(
    private val api: ApiService,
    private val auth: AuthRepository,
) : DisponibilidadRepository {

    override suspend fun consultar(): Disponibilidad = llamar { api.disponibilidad(it) }

    override suspend fun cambiar(disponible: Boolean): Disponibilidad =
        llamar { api.cambiarDisponibilidad(it, CambioDisponibilidadDto(disponible)) }

    private suspend fun llamar(peticion: suspend (String) -> DisponibilidadDto): Disponibilidad {
        val token = auth.tokenDeAcceso() ?: throw ErrorDatos(TipoError.SIN_CONEXION)
        return try {
            peticion("Bearer $token").aModelo()
        } catch (e: IOException) {
            throw ErrorDatos(TipoError.SIN_CONEXION, e)
        } catch (e: HttpException) {
            // 401/403: Django no reconoce esta cuenta como prestador vinculado.
            throw ErrorDatos(if (e.code() == 401 || e.code() == 403) TipoError.CUENTA_NO_VINCULADA else TipoError.SERVIDOR, e)
        } catch (e: JsonParseException) {
            throw ErrorDatos(TipoError.DATOS_INVALIDOS, e)
        }
    }

    private fun DisponibilidadDto.aModelo() = Disponibilidad(
        disponible = disponible ?: throw ErrorDatos(TipoError.DATOS_INVALIDOS),
        estado = when (estado) {
            "verificado" -> EstadoVerificacion.VERIFICADO
            "rechazado" -> EstadoVerificacion.RECHAZADO
            else -> EstadoVerificacion.PENDIENTE
        },
    )
}
