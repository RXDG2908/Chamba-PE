package pe.tecsup.chambape.data.auth

import android.util.Log
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
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

    /**
     * Crea la cuenta y, con su token, ejecuta [registrarEnApi] (el alta del prestador en Django).
     * Si ese alta falla, la cuenta recién creada se elimina y el error se propaga tal cual.
     */
    suspend fun registrarPrestador(
        nombre: String,
        correo: String,
        clave: String,
        registrarEnApi: suspend (token: String) -> Unit,
    )
    fun cerrarSesion()

    /** Token de identidad de Firebase para llamar a la API Django; null si no hay sesión o red. */
    suspend fun tokenDeAcceso(): String?
}

/**
 * Firebase Authentication (correo y contraseña) para la identidad + perfil en Django.
 *
 * El rol y el ID de prestador ya no se leen de Firestore: los da `GET /api/usuarios/yo/` con el
 * token de Firebase, la misma cuenta que ve la web. Un cliente que se registra en la app crea su
 * cuenta en Django al instante (`registro-movil`); si eso falla, la cuenta de Firebase se deshace,
 * igual que con el prestador. El último perfil se guarda en el teléfono para abrir sin red.
 */
class FirebaseAuthRepository(
    private val auth: FirebaseAuth,
    private val perfiles: PerfilesRemotos,
    private val almacen: AlmacenPerfil,
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
                } catch (e: ErrorAuth) {
                    if (e.tipo == TipoErrorAuth.CUENTA_SANCIONADA) {
                        // HU-13: una cuenta sancionada no sigue dentro al reabrir la app.
                        cerrarSesion()
                        EstadoSesion.SinSesion
                    } else {
                        // Sin red al abrir: el último perfil conocido o, si no hay, el de cliente.
                        EstadoSesion.Activa(sesionDe(actual, almacen.leer(actual.uid) ?: perfilMinimo(actual)))
                    }
                } catch (e: Exception) {
                    EstadoSesion.Activa(sesionDe(actual, almacen.leer(actual.uid) ?: perfilMinimo(actual)))
                }
            }
        }
    }

    override suspend fun ingresar(correo: String, clave: String) {
        val usuario = try {
            traducir { auth.signInWithEmailAndPassword(correo.trim(), clave).await().user }
                ?: throw ErrorAuth(TipoErrorAuth.DESCONOCIDO)
        } catch (e: ErrorAuth) {
            if (e.tipo != TipoErrorAuth.CREDENCIALES) throw e
            // Quizá es una cuenta de la web (sin cuenta de Firebase todavía): se intenta vincular con
            // el mismo correo y contraseña. Si no resulta, la persona ve el mismo «incorrectos».
            _sesion.value = EstadoSesion.Activa(entrarConCuentaWeb(correo.trim(), clave, e))
            return
        }
        val sesion = try {
            leerPerfil(usuario)
        } catch (e: Exception) {
            // Sin perfil no se entra a medias (ni sancionado ni sin red para saber el rol).
            auth.signOut()
            throw e
        }
        _sesion.value = EstadoSesion.Activa(sesion)
    }

    /**
     * Cuenta creada en la web: se crea su cuenta de Firebase con ese correo y contraseña y se vincula
     * en la API, que exige la contraseña de la web. Si la API la rechaza, la cuenta de Firebase se borra.
     */
    private suspend fun entrarConCuentaWeb(correo: String, clave: String, original: ErrorAuth): Sesion {
        val nuevo = try {
            auth.createUserWithEmailAndPassword(correo, clave).await().user
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Ya existe en Firebase (era la contraseña equivocada), clave corta o sin red: el error original.
            if (e is FirebaseNetworkException) throw ErrorAuth(TipoErrorAuth.SIN_CONEXION, e)
            throw original
        } ?: throw original
        return deshacerSiFalla(nuevo) {
            val perfil = perfiles.vincularWeb(token(nuevo), clave)
            almacen.guardar(nuevo.uid, perfil)
            sesionDe(nuevo, perfil)
        }
    }

    override suspend fun registrarCliente(nombre: String, correo: String, clave: String) {
        val usuario = traducir { auth.createUserWithEmailAndPassword(correo.trim(), clave).await().user }
            ?: throw ErrorAuth(TipoErrorAuth.DESCONOCIDO)
        val sesion = deshacerSiFalla(usuario) {
            val perfil = perfiles.crearCliente(token(usuario), nombre.trim())
            almacen.guardar(usuario.uid, perfil)
            sesionDe(usuario, perfil)
        }
        _sesion.value = EstadoSesion.Activa(sesion)
    }

    override suspend fun registrarPrestador(
        nombre: String,
        correo: String,
        clave: String,
        registrarEnApi: suspend (token: String) -> Unit,
    ) {
        val usuario = traducir { auth.createUserWithEmailAndPassword(correo.trim(), clave).await().user }
            ?: throw ErrorAuth(TipoErrorAuth.DESCONOCIDO)
        // Sin prestador en Django no debe quedar una cuenta a medias: se deshace.
        deshacerSiFalla(usuario) { registrarEnApi(token(usuario)) }
        // Django ya lo dejó vinculado: el ID de prestador llega sin pasos manuales.
        val perfil = runCatching { perfiles.perfil(token(usuario)) }.getOrNull()
            ?: PerfilRemoto(nombre, Rol.PRESTADOR, prestadorId = null)
        almacen.guardar(usuario.uid, perfil)
        _sesion.value = EstadoSesion.Activa(sesionDe(usuario, perfil))
    }

    override fun cerrarSesion() {
        auth.signOut()
        almacen.borrar()
        _sesion.value = EstadoSesion.SinSesion
    }

    override suspend fun tokenDeAcceso(): String? =
        try {
            auth.currentUser?.getIdToken(false)?.await()?.token
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    private suspend fun token(usuario: FirebaseUser): String =
        traducir { usuario.getIdToken(false).await().token } ?: throw ErrorAuth(TipoErrorAuth.DESCONOCIDO)

    private suspend fun leerPerfil(usuario: FirebaseUser): Sesion {
        val perfil = perfiles.perfilOCliente(token(usuario), nombrePorCorreo(usuario))
        almacen.guardar(usuario.uid, perfil)
        return sesionDe(usuario, perfil)
    }

    private suspend fun <T> deshacerSiFalla(usuario: FirebaseUser, bloque: suspend () -> T): T =
        try {
            bloque()
        } catch (e: Exception) {
            withContext(NonCancellable) {
                runCatching { usuario.delete().await() }
                auth.signOut()
            }
            throw e
        }

    private fun sesionDe(usuario: FirebaseUser, perfil: PerfilRemoto) = Sesion(
        uid = usuario.uid,
        correo = usuario.email.orEmpty(),
        nombre = perfil.nombre.ifBlank { nombrePorCorreo(usuario) },
        rol = perfil.rol,
        prestadorId = perfil.prestadorId,
    )

    /** Sin perfil conocido: se trata como cliente, el rol con menos permisos. */
    private fun perfilMinimo(usuario: FirebaseUser) = PerfilRemoto(nombrePorCorreo(usuario), Rol.CLIENTE, null)

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

}
