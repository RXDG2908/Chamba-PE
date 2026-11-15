package pe.tecsup.chambape.data.auth

import android.util.Log
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import pe.tecsup.chambape.domain.model.ErrorAuth
import pe.tecsup.chambape.domain.model.EstadoSesion
import pe.tecsup.chambape.domain.model.Rol
import pe.tecsup.chambape.domain.model.Sesion
import pe.tecsup.chambape.domain.model.TipoErrorAuth

/** Sesión de la app. Los fallos se lanzan como [ErrorAuth]. */
interface AuthRepository {
    val sesion: StateFlow<EstadoSesion>
    suspend fun ingresar(correo: String, clave: String)
    suspend fun registrarCliente(nombre: String, correo: String, clave: String)
    fun cerrarSesion()
}

/**
 * Firebase Authentication (correo y contraseña) + perfil en Firestore `usuarios/{uid}`.
 * El rol no lo decide la app: se lee del perfil. Desde la app solo se registran clientes;
 * los prestadores los crea y vincula un administrador en la consola.
 */
class FirebaseAuthRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    scope: CoroutineScope,
) : AuthRepository {

    private val _sesion = MutableStateFlow<EstadoSesion>(EstadoSesion.Cargando)
    override val sesion: StateFlow<EstadoSesion> = _sesion.asStateFlow()

    init {
        // La sesión se mantiene al reabrir la app: Firebase recuerda al usuario.
        val actual = auth.currentUser
        if (actual == null) {
            _sesion.value = EstadoSesion.SinSesion
        } else {
            scope.launch {
                _sesion.value = try {
                    EstadoSesion.Activa(leerPerfil(actual))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Sin red al abrir: se entra con los datos mínimos de la cuenta.
                    EstadoSesion.Activa(perfilMinimo(actual))
                }
            }
        }
    }

    override suspend fun ingresar(correo: String, clave: String) {
        val usuario = traducir { auth.signInWithEmailAndPassword(correo.trim(), clave).await().user }
            ?: throw ErrorAuth(TipoErrorAuth.DESCONOCIDO)
        _sesion.value = EstadoSesion.Activa(traducir { leerPerfil(usuario) })
    }

    override suspend fun registrarCliente(nombre: String, correo: String, clave: String) {
        val usuario = traducir { auth.createUserWithEmailAndPassword(correo.trim(), clave).await().user }
            ?: throw ErrorAuth(TipoErrorAuth.DESCONOCIDO)
        val nombreLimpio = nombre.trim()
        traducir {
            firestore.collection(USUARIOS).document(usuario.uid).set(
                mapOf(
                    "nombre" to nombreLimpio,
                    "correo" to usuario.email.orEmpty(),
                    "rol" to ROL_CLIENTE,
                    "creadoEn" to FieldValue.serverTimestamp(),
                ),
            ).await()
        }
        _sesion.value = EstadoSesion.Activa(
            Sesion(usuario.uid, usuario.email.orEmpty(), nombreLimpio, Rol.CLIENTE, prestadorId = null),
        )
    }

    override fun cerrarSesion() {
        auth.signOut()
        _sesion.value = EstadoSesion.SinSesion
    }

    private suspend fun leerPerfil(usuario: FirebaseUser): Sesion {
        val doc = firestore.collection(USUARIOS).document(usuario.uid).get().await()
        if (!doc.exists()) return perfilMinimo(usuario)
        return Sesion(
            uid = usuario.uid,
            correo = usuario.email.orEmpty(),
            nombre = doc.getString("nombre")?.takeIf { it.isNotBlank() } ?: nombrePorCorreo(usuario),
            rol = if (doc.getString("rol") == ROL_PRESTADOR) Rol.PRESTADOR else Rol.CLIENTE,
            prestadorId = doc.getLong("prestadorId")?.toInt(),
        )
    }

    /** Cuenta sin perfil legible: se trata como cliente, el rol con menos permisos. */
    private fun perfilMinimo(usuario: FirebaseUser) =
        Sesion(usuario.uid, usuario.email.orEmpty(), nombrePorCorreo(usuario), Rol.CLIENTE, prestadorId = null)

    private fun nombrePorCorreo(usuario: FirebaseUser) = usuario.email.orEmpty().substringBefore('@')

    private suspend fun <T> traducir(bloque: suspend () -> T): T =
        try {
            bloque()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ErrorAuth) {
            throw e
        } catch (e: FirebaseAuthWeakPasswordException) {
            throw ErrorAuth(TipoErrorAuth.CLAVE_DEBIL, e)
        } catch (e: FirebaseAuthUserCollisionException) {
            throw ErrorAuth(TipoErrorAuth.CORREO_EN_USO, e)
        } catch (e: FirebaseAuthInvalidUserException) {
            throw ErrorAuth(TipoErrorAuth.CREDENCIALES, e)
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            val tipo = if (e.errorCode == "ERROR_INVALID_EMAIL") TipoErrorAuth.CORREO_INVALIDO else TipoErrorAuth.CREDENCIALES
            throw ErrorAuth(tipo, e)
        } catch (e: FirebaseNetworkException) {
            throw ErrorAuth(TipoErrorAuth.SIN_CONEXION, e)
        } catch (e: Exception) {
            Log.w("ChambaPeAuth", "Error de Firebase sin clasificar: ${e.javaClass.simpleName}", e)
            throw ErrorAuth(TipoErrorAuth.DESCONOCIDO, e)
        }

    private companion object {
        const val USUARIOS = "usuarios"
        const val ROL_CLIENTE = "cliente"
        const val ROL_PRESTADOR = "prestador"
    }
}
