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
import pe.tecsup.chambape.data.auth.AuthRepository
import pe.tecsup.chambape.data.repository.PrestadorRepository
import pe.tecsup.chambape.data.repository.SolicitudRepository
import pe.tecsup.chambape.device.Notificador
import pe.tecsup.chambape.domain.model.ErrorAuth
import pe.tecsup.chambape.domain.model.Prestador
import pe.tecsup.chambape.domain.model.Sesion
import pe.tecsup.chambape.domain.model.Solicitud
import pe.tecsup.chambape.domain.model.TipoErrorAuth

// ---------- Ingreso y registro ----------

data class AuthUiState(
    val nombre: String = "",
    val correo: String = "",
    val clave: String = "",
    val camposVacios: Boolean = false,
    val claveCorta: Boolean = false,
    val cargando: Boolean = false,
    val error: TipoErrorAuth? = null,
)

class AuthViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun cambiarNombre(v: String) = _uiState.update { it.copy(nombre = v, camposVacios = false) }
    fun cambiarCorreo(v: String) = _uiState.update { it.copy(correo = v, camposVacios = false, error = null) }
    fun cambiarClave(v: String) = _uiState.update { it.copy(clave = v, camposVacios = false, claveCorta = false, error = null) }

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
        ejecutar { auth.registrarCliente(s.nombre, s.correo, s.clave) }
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
            _uiState.update { it.copy(cargando = false, error = error) }
        }
    }

    companion object {
        const val CLAVE_MINIMA = 6
    }
}

// ---------- Detalle del prestador y envío de la solicitud ----------

data class DetalleUiState(
    val cargando: Boolean = true,
    val prestador: Prestador? = null,
    val descripcion: String = "",
    val direccion: String = "",
    val descripcionCorta: Boolean = false,
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
) : ViewModel() {
    private val _uiState = MutableStateFlow(DetalleUiState())
    val uiState: StateFlow<DetalleUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val prestador = prestadores.prestador(prestadorId)
            _uiState.update { it.copy(cargando = false, prestador = prestador) }
        }
    }

    fun cambiarDescripcion(v: String) =
        _uiState.update { it.copy(descripcion = v.take(DESCRIPCION_MAXIMA), descripcionCorta = false, errorEnvio = false) }

    fun cambiarDireccion(v: String) = _uiState.update { it.copy(direccion = v.take(DIRECCION_MAXIMA)) }

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
        if (s.descripcion.trim().length < DESCRIPCION_MINIMA) {
            _uiState.update { it.copy(descripcionCorta = true) }
            return
        }
        _uiState.update { it.copy(enviando = true, errorEnvio = false) }
        viewModelScope.launch {
            val ok = try {
                solicitudes.crear(sesion, prestador, s.descripcion, s.direccion)
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
        const val DIRECCION_MAXIMA = 120
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
    val respondiendoId: String? = null,
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
                .collect { lista -> _uiState.update { it.copy(cargando = false, solicitudes = lista, error = false) } }
        }
    }

    fun responder(solicitudId: String, aceptar: Boolean) {
        if (_uiState.value.respondiendoId != null) return
        _uiState.update { it.copy(respondiendoId = solicitudId, errorRespuesta = false) }
        viewModelScope.launch {
            val ok = try {
                repository.responder(solicitudId, aceptar)
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
