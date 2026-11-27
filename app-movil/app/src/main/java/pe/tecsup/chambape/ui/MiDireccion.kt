package pe.tecsup.chambape.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import pe.tecsup.chambape.R
import pe.tecsup.chambape.data.local.AlmacenDireccion
import pe.tecsup.chambape.device.Direccion
import pe.tecsup.chambape.ui.busqueda.HojaUbicacion
import pe.tecsup.chambape.ui.busqueda.textoCortoDireccion

class MiDireccionViewModel(private val almacen: AlmacenDireccion) : ViewModel() {
    val guardada: StateFlow<Direccion?> = almacen.guardada
    fun guardar(direccion: Direccion?) = almacen.guardar(direccion)
}

/**
 * «Mi dirección» del cliente, en Perfil: la que Buscar usa por defecto. Se elige con el mismo
 * selector (escribir, GPS o mapa) y se muestra como calle y distrito.
 */
@Composable
fun TarjetaMiDireccion(modifier: Modifier = Modifier) {
    val vm = chambaViewModel { MiDireccionViewModel(it.almacenDireccion) }
    val guardada by vm.guardada.collectAsStateWithLifecycle()
    var eligiendo by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column {
            ListItem(
                overlineContent = { Text(stringResource(R.string.mi_direccion)) },
                headlineContent = {
                    Text(
                        guardada?.let { textoCortoDireccion(it) } ?: stringResource(R.string.mi_direccion_vacia),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = { Text(stringResource(R.string.mi_direccion_ayuda)) },
                leadingContent = { Icon(Icons.Outlined.Home, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                trailingContent = {
                    TextButton(onClick = { eligiendo = true }) {
                        Text(stringResource(if (guardada == null) R.string.mi_direccion_agregar else R.string.mi_direccion_cambiar))
                    }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            )
            if (guardada != null) {
                HorizontalDivider()
                TextButton(onClick = { vm.guardar(null) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.LocationOff, contentDescription = null)
                    Text(stringResource(R.string.mi_direccion_quitar), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }

    if (eligiendo) {
        HojaUbicacion(
            titulo = stringResource(R.string.mi_direccion_hoja),
            actual = guardada,
            guardada = null,
            onElegida = vm::guardar,
            onCerrar = { eligiendo = false },
        )
    }
}
