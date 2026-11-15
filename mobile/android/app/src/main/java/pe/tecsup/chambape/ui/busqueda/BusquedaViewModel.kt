package pe.tecsup.chambape.ui.busqueda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.tecsup.chambape.data.repository.PrestadorRepository
import pe.tecsup.chambape.device.UbicacionProvider
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
    val latitud: String = "",
    val longitud: String = "",
    val faltaRubro: Boolean = false,
    val latitudInvalida: Boolean = false,
    val longitudInvalida: Boolean = false,
    val ubicando: Boolean = false,
    val ubicacionNoDisponible: Boolean = false,
    val favoritos: Set<Int> = emptySet(),
    val resultados: EstadoResultados = EstadoResultados.Inicial,
)

class BusquedaViewModel(
    private val repository: PrestadorRepository,
    private val ubicacion: UbicacionProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BusquedaUiState())
    val uiState: StateFlow<BusquedaUiState> = _uiState.asStateFlow()

    private var busquedaEnCurso: Job? = null

    init {
        cargarRubros()
        viewModelScope.launch {
            repository.favoritos().collect { lista ->
                _uiState.update { it.copy(favoritos = lista.map { p -> p.id }.toSet()) }
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

    fun seleccionarRubro(id: Int) = _uiState.update { it.copy(rubroId = id, faltaRubro = false) }

    fun cambiarLatitud(texto: String) = _uiState.update { it.copy(latitud = texto, latitudInvalida = false) }

    fun cambiarLongitud(texto: String) = _uiState.update { it.copy(longitud = texto, longitudInvalida = false) }

    /** Rellena las coordenadas con la ubicación del celular; el permiso lo pide la pantalla. */
    fun usarMiUbicacion() {
        _uiState.update { it.copy(ubicando = true, ubicacionNoDisponible = false) }
        viewModelScope.launch {
            val actual = ubicacion.actual()
            _uiState.update {
                if (actual == null) {
                    it.copy(ubicando = false, ubicacionNoDisponible = true)
                } else {
                    it.copy(
                        ubicando = false,
                        latitud = actual.latitud.toString(),
                        longitud = actual.longitud.toString(),
                        latitudInvalida = false,
                        longitudInvalida = false,
                    )
                }
            }
        }
    }

    fun permisoUbicacionDenegado() = _uiState.update { it.copy(ubicacionNoDisponible = true) }

    fun buscar() {
        val estado = _uiState.value
        val latitud = coordenada(estado.latitud, 90.0)
        val longitud = coordenada(estado.longitud, 180.0)
        val rubroId = estado.rubroId
        if (rubroId == null || latitud == null || longitud == null) {
            // Una entrada inválida no llega al repositorio.
            _uiState.update {
                it.copy(faltaRubro = rubroId == null, latitudInvalida = latitud == null, longitudInvalida = longitud == null)
            }
            return
        }
        // Una búsqueda nueva cancela la anterior: una respuesta antigua no pisa la reciente.
        busquedaEnCurso?.cancel()
        _uiState.update { it.copy(resultados = EstadoResultados.Cargando) }
        busquedaEnCurso = viewModelScope.launch {
            val resultado = try {
                val datos = repository.buscar(rubroId, latitud, longitud)
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

    private fun coordenada(texto: String, limite: Double): Double? =
        texto.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= -limite && it <= limite }
}
