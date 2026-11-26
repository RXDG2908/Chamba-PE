package pe.tecsup.chambape.data.auth

import android.content.Context
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import pe.tecsup.chambape.data.remote.ApiService
import pe.tecsup.chambape.data.remote.RegistroClienteDto
import pe.tecsup.chambape.data.remote.UsuarioDto
import pe.tecsup.chambape.domain.model.ErrorAuth
import pe.tecsup.chambape.domain.model.Rol
import pe.tecsup.chambape.domain.model.TipoErrorAuth
import retrofit2.HttpException
import java.io.IOException

/** Rol y datos de la cuenta según Django (la única fuente: la web ve lo mismo). */
data class PerfilRemoto(val nombre: String, val rol: Rol, val prestadorId: Int?)

/**
 * Perfil de la cuenta de Firebase en Django. Antes vivía en Firestore `usuarios/{uid}` y el
 * `prestadorId` lo pegaba un administrador a mano; ahora lo da `GET /api/usuarios/yo/`.
 */
interface PerfilesRemotos {
    /** Perfil vinculado al token, o null si la cuenta de Firebase aún no tiene cuenta en Django. */
    suspend fun perfil(token: String): PerfilRemoto?

    /** Crea (sin duplicar) la cuenta de cliente en Django para este usuario de Firebase. */
    suspend fun crearCliente(token: String, nombre: String): PerfilRemoto
}

/**
 * Lee el perfil y, si la cuenta aún no existe en Django, la crea como cliente (el rol con menos
 * permisos). Un prestador siempre nace vinculado: lo crea `POST /api/prestadores/registro-movil/`.
 */
suspend fun PerfilesRemotos.perfilOCliente(token: String, nombreSugerido: String): PerfilRemoto =
    perfil(token) ?: crearCliente(token, nombreSugerido)

/** Implementación con la API Django. Los fallos se lanzan como [ErrorAuth]. */
class ApiPerfiles(private val api: ApiService) : PerfilesRemotos {

    override suspend fun perfil(token: String): PerfilRemoto? =
        try {
            api.yo(bearer(token)).aPerfil()
        } catch (e: HttpException) {
            // El cuerpo del error solo se puede leer una vez: el código se saca aquí y se pasa.
            val codigo = codigoDe(e)
            if (codigo == CUENTA_NO_VINCULADA) null else throw traducir(e, codigo)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw traducir(e)
        }

    override suspend fun crearCliente(token: String, nombre: String): PerfilRemoto =
        try {
            api.registrarClienteMovil(bearer(token), RegistroClienteDto(nombre)).aPerfil()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw traducir(e)
        }

    private fun bearer(token: String) = "Bearer $token"

    private fun UsuarioDto.aPerfil() = PerfilRemoto(
        nombre = listOfNotNull(nombres, apellidos).joinToString(" ").trim()
            .ifEmpty { email.orEmpty().substringBefore('@') },
        rol = if (rol == "prestador") Rol.PRESTADOR else Rol.CLIENTE,
        prestadorId = prestadorId,
    )

    private fun traducir(e: Exception, codigo: String? = (e as? HttpException)?.let(::codigoDe)): ErrorAuth = when {
        e is ErrorAuth -> e
        e is IOException -> ErrorAuth(TipoErrorAuth.SIN_CONEXION, e)
        e is HttpException && codigo == CUENTA_SANCIONADA ->
            ErrorAuth(TipoErrorAuth.CUENTA_SANCIONADA, e)
        // 400 de registro-movil: el correo ya tiene una cuenta web sin vincular.
        e is HttpException && e.code() == 400 -> ErrorAuth(TipoErrorAuth.CORREO_EN_USO, e)
        else -> ErrorAuth(TipoErrorAuth.DESCONOCIDO, e)
    }

    private fun codigoDe(e: HttpException): String? =
        runCatching {
            val cuerpo = e.response()?.errorBody()?.string() ?: return null
            JsonParser.parseString(cuerpo).asJsonObject.get("codigo")?.asString
        }.getOrNull()

    private companion object {
        const val CUENTA_NO_VINCULADA = "cuenta_no_vinculada"
        const val CUENTA_SANCIONADA = "cuenta_sancionada"
    }
}

/** Último perfil conocido por UID: sin red, la app abre con el rol correcto (como hacía la caché de Firestore). */
interface AlmacenPerfil {
    fun leer(uid: String): PerfilRemoto?
    fun guardar(uid: String, perfil: PerfilRemoto)
    fun borrar()
}

class AlmacenPerfilPreferencias(context: Context) : AlmacenPerfil {
    private val prefs = context.getSharedPreferences("perfil_sesion", Context.MODE_PRIVATE)

    override fun leer(uid: String): PerfilRemoto? {
        if (prefs.getString("uid", null) != uid) return null
        val rol = runCatching { Rol.valueOf(prefs.getString("rol", "") ?: "") }.getOrNull() ?: return null
        return PerfilRemoto(
            nombre = prefs.getString("nombre", "").orEmpty(),
            rol = rol,
            prestadorId = prefs.getInt("prestadorId", -1).takeIf { it > 0 },
        )
    }

    override fun guardar(uid: String, perfil: PerfilRemoto) {
        prefs.edit()
            .putString("uid", uid)
            .putString("nombre", perfil.nombre)
            .putString("rol", perfil.rol.name)
            .putInt("prestadorId", perfil.prestadorId ?: -1)
            .apply()
    }

    override fun borrar() {
        prefs.edit().clear().apply()
    }
}
