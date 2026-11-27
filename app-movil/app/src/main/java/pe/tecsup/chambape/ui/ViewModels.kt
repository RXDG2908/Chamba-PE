package pe.tecsup.chambape.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.tecsup.chambape.R
import pe.tecsup.chambape.data.auth.AuthRepository
import pe.tecsup.chambape.data.repository.PrestadorRepository
import pe.tecsup.chambape.data.repository.SolicitudRepository
import pe.tecsup.chambape.device.Notificador
import pe.tecsup.chambape.domain.model.ErrorAuth
import pe.tecsup.chambape.domain.model.Prestador
import pe.tecsup.chambape.domain.model.Sesion
import pe.tecsup.chambape.domain.model.Solicitud
import pe.tecsup.chambape.domain.model.TipoErrorAuth
import pe.tecsup.chambape.domain.model.NuevaSolicitud
import pe.tecsup.chambape.domain.model.EstadoSolicitud
import pe.tecsup.chambape.data.repository.ContextoBusqueda

// ---------- Ingreso y registro ----------

data class AuthUiState(
    val nombre: String = "",
    val correo: String = "",
    val clave: String = "",
    val camposVacios: Boolean = false,
    val claveCorta: Boolean = false,
    val cargando: Boolean = false,
    val error: TipoErrorAuth? = null,
    /** Registro de cliente por pasos: 0 nombre, 1 correo, 2 contraseña. */
    val paso: Int = 0,
    /** Error del campo del paso actual (recurso de texto), debajo del campo. */
    val errorNombre: Int? = null,
    val errorCorreo: Int? = null,
    val errorClave: Int? = null,
)

class AuthViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun cambiarNombre(v: String) = _uiState.update { it.copy(nombre = v.take(NOMBRE_MAXIMO), camposVacios = false, errorNombre = null) }
    fun cambiarCorreo(v: String) = _uiState.update { it.copy(correo = v.trim(), camposVacios = false, error = null, errorCorreo = null) }
    fun cambiarClave(v: String) =
        _uiState.update { it.copy(clave = v, camposVacios = false, claveCorta = false, error = null, errorClave = null) }

    /** Registro de cliente: valida el paso actual y avanza; en el último crea la cuenta. */
    fun siguienteRegistro() {
        val s = _uiState.value
        if (s.cargando) return
        val error = when (s.paso) {
            PASO_NOMBRE -> R.string.error_nombre.takeIf { s.nombre.trim().length < 2 }
            PASO_CORREO -> errorDeCorreo(s.correo)
            else -> R.string.error_clave_minima.takeIf { s.clave.length < CLAVE_MINIMA }
        }
        when {
            error != null -> _uiState.update {
                when (s.paso) {
                    PASO_NOMBRE -> it.copy(errorNombre = error)
                    PASO_CORREO -> it.copy(errorCorreo = error)
                    else -> it.copy(errorClave = error)
                }
            }
            s.paso < PASO_CLAVE -> _uiState.update { it.copy(paso = it.paso + 1, error = null) }
            else -> registrar()
        }
    }

    /** Retrocede un paso sin perder lo escrito; false si ya está en el primero. */
    fun atrasRegistro(): Boolean {
        val s = _uiState.value
        if (s.cargando || s.paso == 0) return false
        _uiState.update { it.copy(paso = it.paso - 1, error = null) }
        return true
    }

    fun ingresar() {
        val s = _uiState.value
        if (s.correo.isBlank() || s.clave.isBlank()) {
            _uiState.update { it.copy(camposVacios = true) }
            return
        }
        ejecutar { auth.ingresar(s.correo, s.clave) }
    }

    fun registrar() {
        val s = _uiState.value
        if (s.nombre.isBlank() || s.correo.isBlank() || s.clave.isBlank()) {
            _uiState.update { it.copy(camposVacios = true) }
            return
        }
        if (s.clave.length < CLAVE_MINIMA) {
            _uiState.update { it.copy(claveCorta = true) }
            return
        }
        ejecutar { auth.registrarCliente(s.nombre.trim(), s.correo, s.clave) }
    }

    private fun ejecutar(accion: suspend () -> Unit) {
        if (_uiState.value.cargando) return
        _uiState.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            val error = try {
                accion()
                null
            } catch (e: ErrorAuth) {
                e.tipo
            }
            _uiState.update {
                // Un error de un campo lleva al paso donde se corrige y se muestra debajo del campo.
                when (error) {
                    TipoErrorAuth.CORREO_EN_USO -> it.copy(cargando = false, paso = PASO_CORREO, errorCorreo = R.string.error_correo_en_uso)
                    TipoErrorAuth.CORREO_INVALIDO -> it.copy(cargando = false, paso = PASO_CORREO, errorCorreo = R.string.error_correo_invalido)
                    TipoErrorAuth.CLAVE_DEBIL -> it.copy(cargando = false, paso = PASO_CLAVE, errorClave = R.string.error_clave_debil)
                    else -> it.copy(cargando = false, error = error)
                }
            }
        }
    }

    companion object {
        const val CLAVE_MINIMA = 6
        const val NOMBRE_MAXIMO = 60
        const val PASO_NOMBRE = 0
        const val PASO_CORREO = 1
        const val PASO_CLAVE = 2
        private val CORREO = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")

        fun errorDeCorreo(correo: String): Int? = when {
            correo.isBlank() -> R.string.error_obligatorio
            !CORREO.matches(correo.trim()) -> R.string.error_correo_formato
            else -> null
        }
    }
}

// ---------- Detalle del prestador y envío de la solicitud ----------

data class DetalleUiState(
    val cargando: Boolean = true,
    val prestador: Prestador? = null,
    val descripcion: String = "",
    val telefono: String = "",
    val descripcionCorta: Boolean = false,
    val telefonoInvalido: Boolean = false,
    /** Sin una búsqueda previa no hay rubro ni ubicación para enviar la solicitud. */
    val sinBusqueda: Boolean = false,
    /** Dónde se pide el servicio (la dirección de la última búsqueda); null si no hubo búsqueda. */
    val lugar: String? = null,
    val enviando: Boolean = false,
    val enviada: Boolean = false,
    val errorEnvio: Boolean = false,
)

class DetalleViewModel(
    private val prestadorId: Int,
    private val sesion: Sesion,
    private val prestadores: PrestadorRepository,
    private val solicitudes: SolicitudRepository,
    private val notificador: Notificador,
    private val contexto: ContextoBusqueda,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DetalleUiState(lugar = contexto.ultima?.direccion))
    val uiState: StateFlow<DetalleUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val prestador = prestadores.prestador(prestadorId)
            _uiState.update { it.copy(cargando = false, prestador = prestador) }
        }
    }

    fun cambiarDescripcion(v: String) =
        _uiState.update { it.copy(descripcion = v.take(DESCRIPCION_MAXIMA), descripcionCorta = false, errorEnvio = false) }

    fun cambiarTelefono(v: String) = _uiState.update {
        it.copy(telefono = v.filter(Char::isDigit).take(TELEFONO_DIGITOS), telefonoInvalido = false, errorEnvio = false)
    }

    fun alternarFavorito() {
        viewModelScope.launch {
            val favorito = prestadores.alternarFavorito(prestadorId)
            _uiState.update { it.copy(prestador = it.prestador?.copy(favorito = favorito)) }
        }
    }

    fun enviar() {
        val s = _uiState.value
        val prestador = s.prestador ?: return
        if (s.enviando || s.enviada) return // evita el doble envío
        val descripcionCorta = s.descripcion.trim().length < DESCRIPCION_MINIMA
        val telefonoInvalido = s.telefono.length != TELEFONO_DIGITOS
        val busqueda = contexto.ultima
        if (descripcionCorta || telefonoInvalido || busqueda == null) {
            _uiState.update {
                it.copy(descripcionCorta = descripcionCorta, telefonoInvalido = telefonoInvalido, sinBusqueda = busqueda == null)
            }
            return
        }
        _uiState.update { it.copy(enviando = true, errorEnvio = false) }
        viewModelScope.launch {
            val ok = try {
                solicitudes.crear(
                    sesion, prestador,
                    NuevaSolicitud(busqueda.rubroId, s.descripcion, s.telefono, busqueda.latitud, busqueda.longitud),
                )
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            if (ok) notificador.solicitudEnviada(prestador.nombreCompleto)
            _uiState.update { it.copy(enviando = false, enviada = ok, errorEnvio = !ok) }
        }
    }

    companion object {
        const val DESCRIPCION_MINIMA = 10
        const val DESCRIPCION_MAXIMA = 500
        const val TELEFONO_DIGITOS = 9
    }
}

// ---------- Favoritos ----------

class FavoritosViewModel(private val prestadores: PrestadorRepository) : ViewModel() {
    /** null mientras Room responde por primera vez. */
    val favoritos: StateFlow<List<Prestador>?> =
        prestadores.favoritos().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun quitar(prestadorId: Int) {
        viewModelScope.launch { prestadores.alternarFavorito(prestadorId) }
    }
}

// ---------- Solicitudes (enviadas por el cliente o recibidas por el prestador) ----------

data class SolicitudesUiState(
    val cargando: Boolean = true,
    val solicitudes: List<Solicitud> = emptyList(),
    val error: Boolean = false,
    val respondiendoId: Int? = null,
    val errorRespuesta: Boolean = false,
)

class SolicitudesViewModel(
    private val sesion: Sesion,
    private val repository: SolicitudRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SolicitudesUiState())
    val uiState: StateFlow<SolicitudesUiState> = _uiState.asStateFlow()

    init {
        observar()
    }

    fun reintentar() = observar()

    private fun observar() {
        _uiState.update { it.copy(cargando = true, error = false) }
        viewModelScope.launch {
            repository.deLaSesion(sesion)
                .catch { _uiState.update { it.copy(cargando = false, error = true) } }
                .collect { lista -> _uiState.update { it.copy(cargando = false, solicitudes = ordenarSolicitudes(lista), error = false) } }
        }
    }

    fun responder(solicitudId: Int, aceptar: Boolean) = accion(solicitudId) { repository.responder(solicitudId, aceptar) }

    /** HU-11: el prestador pasa el servicio a la etapa siguiente. */
    fun avanzar(solicitudId: Int, estado: EstadoSolicitud) = accion(solicitudId) { repository.avanzar(solicitudId, estado) }

    /** HU-12: el cliente califica el servicio finalizado (1 a 5 estrellas). */
    fun calificar(solicitudId: Int, estrellas: Int, comentario: String) =
        accion(solicitudId) { repository.calificar(solicitudId, estrellas, comentario) }

    private fun accion(solicitudId: Int, ejecutar: suspend () -> Unit) {
        if (_uiState.value.respondiendoId != null) return
        _uiState.update { it.copy(respondiendoId = solicitudId, errorRespuesta = false) }
        viewModelScope.launch {
            val ok = try {
                ejecutar()
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            _uiState.update { it.copy(respondiendoId = null, errorRespuesta = !ok) }
        }
    }
}

/**
 * Las que siguen en curso (pendiente, confirmada, en camino, en servicio) van primero; las
 * finalizadas y rechazadas, al final. Dentro de cada grupo se respeta el orden del servidor.
 */
internal fun ordenarSolicitudes(lista: List<Solicitud>): List<Solicitud> =
    lista.sortedBy { it.estado == EstadoSolicitud.FINALIZADA || it.estado == EstadoSolicitud.RECHAZADA }
