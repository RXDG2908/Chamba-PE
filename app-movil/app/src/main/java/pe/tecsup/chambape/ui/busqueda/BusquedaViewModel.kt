package pe.tecsup.chambape.ui.busqueda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.tecsup.chambape.data.local.AlmacenDireccion
import pe.tecsup.chambape.data.repository.ContextoBusqueda
import pe.tecsup.chambape.data.repository.PrestadorRepository
import pe.tecsup.chambape.device.Coordenadas
import pe.tecsup.chambape.device.Direccion
import pe.tecsup.chambape.domain.model.ErrorDatos
import pe.tecsup.chambape.domain.model.Prestador
import pe.tecsup.chambape.domain.model.Rubro
import pe.tecsup.chambape.domain.model.TipoError

sealed interface EstadoRubros {
    data object Cargando : EstadoRubros
    data class Listo(val rubros: List<Rubro>, val desdeCache: Boolean) : EstadoRubros
    data class Error(val tipo: TipoError) : EstadoRubros
}

sealed interface EstadoResultados {
    data object Inicial : EstadoResultados
    data object Cargando : EstadoResultados
    data class Exito(val prestadores: List<Prestador>, val desdeCache: Boolean) : EstadoResultados
    data object Vacio : EstadoResultados
    data class Error(val tipo: TipoError) : EstadoResultados
}

data class BusquedaUiState(
    val rubros: EstadoRubros = EstadoRubros.Cargando,
    val rubroId: Int? = null,
    /** Dónde se necesita el servicio, ya con nombre de calle (lo resuelve [SelectorUbicacionViewModel]). */
    val direccion: Direccion? = null,
    /** La dirección vino de «Mi dirección» (Perfil): si cambia allá, cambia aquí. */
    val usaDireccionGuardada: Boolean = false,
    val faltaRubro: Boolean = false,
    val faltaDireccion: Boolean = false,
    val favoritos: Set<Int> = emptySet(),
    val resultados: EstadoResultados = EstadoResultados.Inicial,
) {
    /** Punto que pinta el mapa y que va a la API; la persona nunca lo ve como números. */
    val ubicacion: Coordenadas? get() = direccion?.coordenadas
}

private fun Coordenadas.valida() = latitud in -90.0..90.0 && longitud in -180.0..180.0 &&
    latitud.isFinite() && longitud.isFinite()

class BusquedaViewModel(
    private val repository: PrestadorRepository,
    /** Guarda rubro y ubicación de la búsqueda: la solicitud se envía con ellos. */
    private val contexto: ContextoBusqueda? = null,
    private val almacen: AlmacenDireccion? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BusquedaUiState())
    val uiState: StateFlow<BusquedaUiState> = _uiState.asStateFlow()

    /** «Mi dirección» de Perfil, para ofrecerla en el selector de ubicación. */
    val direccionGuardada: StateFlow<Direccion?> = almacen?.guardada ?: MutableStateFlow(null)

    private var busquedaEnCurso: Job? = null

    init {
        cargarRubros()
        viewModelScope.launch {
            repository.favoritos().collect { lista ->
                _uiState.update { it.copy(favoritos = lista.map { p -> p.id }.toSet()) }
            }
        }
        // Empieza con «Mi dirección» y la sigue mientras la persona no elija otra aquí.
        almacen?.let { guardadas ->
            viewModelScope.launch {
                guardadas.guardada.collect { guardada ->
                    val estado = _uiState.value
                    if (guardada != null && (estado.direccion == null || estado.usaDireccionGuardada)) {
                        _uiState.update { it.copy(direccion = guardada, usaDireccionGuardada = true, faltaDireccion = false) }
                        buscarSiEstaListo()
                    }
                }
            }
        }
    }

    fun cargarRubros() {
        _uiState.update { it.copy(rubros = EstadoRubros.Cargando) }
        viewModelScope.launch {
            val estado = try {
                val datos = repository.rubros()
                EstadoRubros.Listo(datos.valor, datos.desdeCache)
            } catch (e: ErrorDatos) {
                EstadoRubros.Error(e.tipo)
            }
            _uiState.update { it.copy(rubros = estado) }
        }
    }

    /** Elegir el rubro ya busca si hay dirección: un toque, como en una app de delivery. */
    fun seleccionarRubro(id: Int) {
        _uiState.update { it.copy(rubroId = id, faltaRubro = false) }
        buscarSiEstaListo()
    }

    /** Lugar elegido en el selector de ubicación (escrito, en el mapa o el del celular). */
    fun elegirDireccion(direccion: Direccion) {
        if (!direccion.coordenadas.valida()) return
        _uiState.update { it.copy(direccion = direccion, usaDireccionGuardada = false, faltaDireccion = false) }
        buscarSiEstaListo()
    }

    private fun buscarSiEstaListo() {
        val estado = _uiState.value
        if (estado.rubroId != null && estado.direccion != null) buscar()
    }

    fun buscar() {
        val estado = _uiState.value
        val rubroId = estado.rubroId
        val punto = estado.direccion?.coordenadas
        if (rubroId == null || punto == null) {
            // Sin rubro o sin lugar no se llama a la API: la pantalla dice qué falta.
            _uiState.update { it.copy(faltaRubro = rubroId == null, faltaDireccion = punto == null) }
            return
        }
        contexto?.ultima = ContextoBusqueda.Datos(rubroId, punto.latitud, punto.longitud)
        // Una búsqueda nueva cancela la anterior: una respuesta antigua no pisa la reciente.
        busquedaEnCurso?.cancel()
        _uiState.update { it.copy(resultados = EstadoResultados.Cargando) }
        busquedaEnCurso = viewModelScope.launch {
            val resultado = try {
                val datos = repository.buscar(rubroId, punto.latitud, punto.longitud)
                if (datos.valor.isEmpty()) EstadoResultados.Vacio else EstadoResultados.Exito(datos.valor, datos.desdeCache)
            } catch (e: ErrorDatos) {
                EstadoResultados.Error(e.tipo)
            }
            _uiState.update { it.copy(resultados = resultado) }
        }
    }

    fun alternarFavorito(prestadorId: Int) {
        viewModelScope.launch { repository.alternarFavorito(prestadorId) }
    }
}
