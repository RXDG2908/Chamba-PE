package pe.tecsup.chambape

import android.app.Application
import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import pe.tecsup.chambape.data.auth.AlmacenPerfilPreferencias
import pe.tecsup.chambape.data.auth.ApiPerfiles
import pe.tecsup.chambape.data.auth.AuthRepository
import pe.tecsup.chambape.data.auth.FirebaseAuthRepository
import pe.tecsup.chambape.data.local.AlmacenDireccion
import pe.tecsup.chambape.data.local.AlmacenDireccionPreferencias
import pe.tecsup.chambape.data.local.AppDatabase
import pe.tecsup.chambape.data.remote.RetrofitClient
import pe.tecsup.chambape.data.repository.DisponibilidadRepository
import pe.tecsup.chambape.data.repository.DisponibilidadRepositoryImpl
import pe.tecsup.chambape.data.repository.ApiChatRepository
import pe.tecsup.chambape.data.repository.ApiSolicitudRepository
import pe.tecsup.chambape.data.repository.ChatRepository
import pe.tecsup.chambape.data.repository.ContextoBusqueda
import pe.tecsup.chambape.data.repository.PrestadorRepository
import pe.tecsup.chambape.data.repository.PrestadorRepositoryImpl
import pe.tecsup.chambape.data.repository.RegistroPrestadorRepository
import pe.tecsup.chambape.data.repository.RegistroPrestadorRepositoryImpl
import pe.tecsup.chambape.data.repository.SolicitudRepository
import pe.tecsup.chambape.device.BuscadorDirecciones
import pe.tecsup.chambape.device.GeocoderDirecciones
import pe.tecsup.chambape.device.Notificador
import pe.tecsup.chambape.device.NotificadorAndroid
import pe.tecsup.chambape.device.UbicacionFused
import pe.tecsup.chambape.device.UbicacionProvider

/** Crea la API, la base de datos y los repositorios una sola vez (inyección manual). */
class AppContainer(context: Context) {
    private val alcanceApp = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val baseDatos = AppDatabase.crear(context)
    private val api = RetrofitClient.crear(RetrofitClient.urlParaEsteEquipo(BuildConfig.API_BASE_URL, RetrofitClient.esEmulador()))

    // Firebase solo para la identidad; el rol y el prestador salen de Django (GET /api/usuarios/yo/).
    val authRepository: AuthRepository = FirebaseAuthRepository(
        FirebaseAuth.getInstance(), ApiPerfiles(api), AlmacenPerfilPreferencias(context), alcanceApp,
    )

    val prestadorRepository: PrestadorRepository =
        PrestadorRepositoryImpl(
            api = api,
            rubroDao = baseDatos.rubroDao(),
            prestadorDao = baseDatos.prestadorDao(),
        )

    val disponibilidadRepository: DisponibilidadRepository = DisponibilidadRepositoryImpl(api, authRepository)
    val registroPrestadorRepository: RegistroPrestadorRepository = RegistroPrestadorRepositoryImpl(api)
    // Solicitudes en la API (Spring, sobre la misma base que Django), las mismas que ve la web.
    val solicitudRepository: SolicitudRepository = ApiSolicitudRepository(api, authRepository)
    val chatRepository: ChatRepository = ApiChatRepository(api, authRepository)
    val contextoBusqueda = ContextoBusqueda()
    val ubicacionProvider: UbicacionProvider = UbicacionFused(context)
    val notificador: Notificador = NotificadorAndroid(context)
    // Direcciones escritas por el cliente (sin coordenadas a la vista) y su «Mi dirección».
    val buscadorDirecciones: BuscadorDirecciones = GeocoderDirecciones(context)
    val almacenDireccion: AlmacenDireccion = AlmacenDireccionPreferencias(context)
    val ubicacionSesion = kotlinx.coroutines.flow.MutableStateFlow<pe.tecsup.chambape.device.Direccion?>(null)

    /** La dirección es de la cuenta: otra persona que entre en este celular no la hereda. */
    fun cerrarSesion() {
        almacenDireccion.guardar(null)
        ubicacionSesion.value = null
        authRepository.cerrarSesion()
    }
}

class ChambaPeApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
