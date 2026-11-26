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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.GppBad
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent

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
private fun Aviso(icono: ImageVector, texto: String, fondo: Color, contenido: Color) {
    Surface(color = fondo, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icono, contentDescription = null, tint = contenido)
            Text(texto, color = contenido, style = MaterialTheme.typography.bodyMedium)
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
            val tipo = estado.errorCarga ?: TipoError.SERVIDOR
            Mensaje(
                textoError(tipo),
                textoBoton = stringResource(R.string.reintentar),
                icono = iconoError(tipo),
                onBoton = vm::cargar,
            )
        }
        else -> Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.disponibilidad_ayuda),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // La tarjeta toma el color del estado: verde cuando recibe solicitudes, neutra cuando no.
            val fondo by animateColorAsState(
                if (datos.disponible) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                tween(Movimiento.MEDIA),
                label = "fondo",
            )
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = fondo)) {
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
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    AnimatedContent(datos.disponible, label = "icono") { activo ->
                        Icon(
                            if (activo) Icons.Outlined.ToggleOn else Icons.Outlined.DoNotDisturbOn,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    Text(
                        stringResource(if (datos.disponible) R.string.disponibilidad_activo else R.string.disponibilidad_inactivo),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (estado.guardando) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                    Switch(
                        checked = datos.disponible,
                        onCheckedChange = null,
                        enabled = !estado.guardando,
                        thumbContent = {
                            Icon(
                                if (datos.disponible) Icons.Filled.Check else Icons.Filled.Close,
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize),
                            )
                        },
                    )
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
                EstadoVerificacion.PENDIENTE -> Aviso(
                    Icons.Outlined.HourglassTop, stringResource(R.string.disponibilidad_aviso_pendiente),
                    MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer,
                )
                EstadoVerificacion.RECHAZADO -> Aviso(
                    Icons.Outlined.GppBad, stringResource(R.string.disponibilidad_aviso_rechazado),
                    MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer,
                )
                EstadoVerificacion.VERIFICADO -> Aviso(
                    Icons.Outlined.VerifiedUser, stringResource(R.string.disponibilidad_verificado),
                    MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            AvisoError(if (estado.errorGuardado) stringResource(R.string.disponibilidad_error_guardar) else null)
        }
    }
}
