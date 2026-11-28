package pe.tecsup.chambape.data.repository

import com.google.gson.JsonParseException
import pe.tecsup.chambape.data.auth.AuthRepository
import pe.tecsup.chambape.data.remote.ApiService
import pe.tecsup.chambape.data.remote.ChatDto
import pe.tecsup.chambape.data.remote.MensajeDto
import pe.tecsup.chambape.data.remote.NuevoMensajeDto
import pe.tecsup.chambape.domain.model.Chat
import pe.tecsup.chambape.domain.model.ErrorDatos
import pe.tecsup.chambape.domain.model.MensajeChat
import pe.tecsup.chambape.domain.model.TipoError
import retrofit2.HttpException
import java.io.IOException

/** Chat de una solicitud aceptada: el cliente y el prestador coordinan (la misma ruta que usa la web). */
interface ChatRepository {
    suspend fun consultar(solicitudId: Int): Chat
    suspend fun enviar(solicitudId: Int, texto: String): MensajeChat
}

class ApiChatRepository(
    private val api: ApiService,
    private val auth: AuthRepository,
) : ChatRepository {

    override suspend fun consultar(solicitudId: Int): Chat =
        llamar { api.chat(it, solicitudId) }.aModelo() ?: throw ErrorDatos(TipoError.DATOS_INVALIDOS)

    override suspend fun enviar(solicitudId: Int, texto: String): MensajeChat =
        llamar { api.enviarMensaje(it, solicitudId, NuevoMensajeDto(texto)) }.aModelo(mio = true)
            ?: throw ErrorDatos(TipoError.DATOS_INVALIDOS)

    private suspend fun <T> llamar(peticion: suspend (String) -> T): T {
        val token = auth.tokenDeAcceso() ?: throw ErrorDatos(TipoError.SIN_CONEXION)
        return try {
            peticion("Bearer $token")
        } catch (e: IOException) {
            throw ErrorDatos(TipoError.SIN_CONEXION, e)
        } catch (e: HttpException) {
            // 409: el chat aún no se abre o el servicio ya finalizó; 404: no es parte de la solicitud.
            throw ErrorDatos(if (e.code() == 401 || e.code() == 403) TipoError.CUENTA_NO_VINCULADA else TipoError.SERVIDOR, e)
        } catch (e: JsonParseException) {
            throw ErrorDatos(TipoError.DATOS_INVALIDOS, e)
        }
    }
}

internal fun MensajeDto.aModelo(mio: Boolean): MensajeChat? =
    MensajeChat(id = id ?: return null, mio = mio, texto = texto ?: return null, enviado = enviado.orEmpty())

internal fun ChatDto.aModelo(): Chat? {
    val yo = yo ?: return null
    return Chat(
        solicitudId = solicitud ?: return null,
        estadoTexto = estadoDisplay ?: estado.orEmpty(),
        activo = activo == true,
        contraparte = contraparte?.nombre.orEmpty(),
        telefono = contraparte?.telefono?.takeIf { it.isNotBlank() },
        mensajes = mensajes.orEmpty().mapNotNull { it.aModelo(mio = it.de == yo) },
    )
}
