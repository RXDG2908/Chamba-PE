package pe.tecsup.chambape.data.repository

import com.google.gson.Gson
import com.google.gson.JsonParseException
import pe.tecsup.chambape.data.remote.ApiService
import pe.tecsup.chambape.data.remote.RegistroPrestadorDto
import pe.tecsup.chambape.domain.model.DatosRegistroPrestador
import pe.tecsup.chambape.domain.model.ErrorDatos
import pe.tecsup.chambape.domain.model.ErrorRegistro
import pe.tecsup.chambape.domain.model.TipoError
import retrofit2.HttpException
import java.io.IOException
import java.util.Locale

/** Alta del prestador en la API Django. Lanza [ErrorRegistro] (errores por campo) o [ErrorDatos]. */
interface RegistroPrestadorRepository {
    suspend fun registrar(token: String, datos: DatosRegistroPrestador)
}

class RegistroPrestadorRepositoryImpl(private val api: ApiService) : RegistroPrestadorRepository {

    override suspend fun registrar(token: String, datos: DatosRegistroPrestador) {
        try {
            api.registrarPrestador(
                "Bearer $token",
                RegistroPrestadorDto(
                    nombres = datos.nombres.trim(),
                    apellidos = datos.apellidos.trim(),
                    dni = datos.dni.trim(),
                    telefono = datos.telefono.trim(),
                    rubros = datos.rubros.sorted(),
                    // Seis decimales y con punto: es la precisión que guarda la API.
                    latitud = String.format(Locale.ROOT, "%.6f", datos.latitud),
                    longitud = String.format(Locale.ROOT, "%.6f", datos.longitud),
                    radioCoberturaKm = datos.radioKm,
                ),
            )
        } catch (e: IOException) {
            throw ErrorDatos(TipoError.SIN_CONEXION, e)
        } catch (e: HttpException) {
            val campos = if (e.code() == 400 || e.code() == 409) erroresPorCampo(e) else emptyMap()
            if (campos.isEmpty()) throw ErrorDatos(TipoError.SERVIDOR, e)
            throw ErrorRegistro(campos)
        } catch (e: JsonParseException) {
            throw ErrorDatos(TipoError.DATOS_INVALIDOS, e)
        }
    }

    /** DRF responde `{"campo": ["mensaje", ...]}` o `{"detail": "mensaje"}`. */
    private fun erroresPorCampo(e: HttpException): Map<String, String> =
        try {
            val cuerpo = e.response()?.errorBody()?.string().orEmpty()
            Gson().fromJson(cuerpo, Map::class.java).orEmpty().mapNotNull { (campo, valor) ->
                val mensaje = when (valor) {
                    is List<*> -> valor.filterIsInstance<String>().joinToString(" ")
                    is String -> valor
                    else -> ""
                }
                if (campo is String && mensaje.isNotBlank()) campo to mensaje else null
            }.toMap()
        } catch (ex: JsonParseException) {
            emptyMap()
        } catch (ex: IOException) {
            emptyMap()
        }
}
