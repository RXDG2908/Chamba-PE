package pe.tecsup.chambape.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.tecsup.chambape.R
import pe.tecsup.chambape.data.repository.DisponibilidadRepository
import pe.tecsup.chambape.domain.model.Disponibilidad
import pe.tecsup.chambape.domain.model.ErrorDatos
import pe.tecsup.chambape.domain.model.EstadoVerificacion
import pe.tecsup.chambape.domain.model.TipoError

data class DisponibilidadUiState(
    val cargando: Boolean = true,
    val datos: Disponibilidad? = null,
    val errorCarga: TipoError? = null,
    val guardando: Boolean = false,
    val errorGuardado: Boolean = false,
)

class DisponibilidadViewModel(private val repository: DisponibilidadRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(DisponibilidadUiState())
    val uiState: StateFlow<DisponibilidadUiState> = _uiState.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _uiState.update { it.copy(cargando = true, errorCarga = null) }
        viewModelScope.launch {
            _uiState.update {
                try {
                    it.copy(cargando = false, datos = repository.consultar())
                } catch (e: ErrorDatos) {
                    it.copy(cargando = false, errorCarga = e.tipo)
                }
            }
        }
    }

    fun alternar() {
        val actual = _uiState.value
        val datos = actual.datos ?: return
        if (actual.guardando) return
        _uiState.update { it.copy(guardando = true, errorGuardado = false) }
        viewModelScope.launch {
            // El interruptor solo cambia cuando el servidor confirma: un fallo no deja un estado ficticio.
            val nuevo = try {
                repository.cambiar(!datos.disponible)
            } catch (e: ErrorDatos) {
                null
            }
            _uiState.update { it.copy(guardando = false, datos = nuevo ?: it.datos, errorGuardado = nuevo == null) }
        }
    }
}

@Composable
fun DisponibilidadScreen() {
    val vm = chambaViewModel { DisponibilidadViewModel(it.disponibilidadRepository) }
    val estado by vm.uiState.collectAsStateWithLifecycle()
    val datos = estado.datos
    when {
        estado.cargando -> PantallaCentrada { Cargando() }
        datos == null -> PantallaCentrada {
            Mensaje(
                textoError(estado.errorCarga ?: TipoError.SERVIDOR),
                textoBoton = stringResource(R.string.reintentar),
                onBoton = vm::cargar,
            )
        }
        else -> Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.disponibilidad_ayuda), style = MaterialTheme.typography.bodyLarge)
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = datos.disponible,
                            enabled = !estado.guardando,
                            role = Role.Switch,
                            onValueChange = { vm.alternar() },
                        )
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(if (datos.disponible) R.string.disponibilidad_activo else R.string.disponibilidad_inactivo),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = datos.disponible, onCheckedChange = null, enabled = !estado.guardando)
                }
            }
            Text(
                stringResource(
                    when {
                        !datos.disponible -> R.string.disponibilidad_resumen_inactivo
                        datos.estado == EstadoVerificacion.VERIFICADO -> R.string.disponibilidad_resumen_activo
                        else -> R.string.disponibilidad_resumen_sin_verificar
                    },
                ),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            when (datos.estado) {
                EstadoVerificacion.PENDIENTE -> Text(stringResource(R.string.disponibilidad_aviso_pendiente), color = MaterialTheme.colorScheme.secondary)
                EstadoVerificacion.RECHAZADO -> Text(stringResource(R.string.disponibilidad_aviso_rechazado), color = MaterialTheme.colorScheme.error)
                EstadoVerificacion.VERIFICADO -> Unit
            }
            if (estado.errorGuardado) {
                Text(
                    stringResource(R.string.disponibilidad_error_guardar),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
            }
        }
    }
}
