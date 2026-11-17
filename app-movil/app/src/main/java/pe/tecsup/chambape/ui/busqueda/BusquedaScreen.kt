package pe.tecsup.chambape.ui.busqueda

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.tecsup.chambape.R
import pe.tecsup.chambape.ui.AvisoSinConexion
import pe.tecsup.chambape.ui.Cargando
import pe.tecsup.chambape.ui.Mensaje
import pe.tecsup.chambape.ui.PrestadorCard
import pe.tecsup.chambape.ui.chambaViewModel
import pe.tecsup.chambape.ui.textoError

/** Conecta el ViewModel con la pantalla; [BusquedaScreen] se puede probar sin él. */
@Composable
fun BusquedaRoute(onAbrirPrestador: (Int) -> Unit) {
    val vm = chambaViewModel { BusquedaViewModel(it.prestadorRepository, it.ubicacionProvider) }
    val estado by vm.uiState.collectAsStateWithLifecycle()
    val pedirPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        if (concedido) vm.usarMiUbicacion() else vm.permisoUbicacionDenegado()
    }
    BusquedaScreen(
        estado = estado,
        onRubro = vm::seleccionarRubro,
        onLatitud = vm::cambiarLatitud,
        onLongitud = vm::cambiarLongitud,
        onElegirEnMapa = vm::elegirUbicacion,
        onMiUbicacion = { pedirPermiso.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
        onBuscar = vm::buscar,
        onReintentarRubros = vm::cargarRubros,
        onAbrirPrestador = onAbrirPrestador,
        onFavorito = vm::alternarFavorito,
    )
}

@Composable
fun BusquedaScreen(
    estado: BusquedaUiState,
    onRubro: (Int) -> Unit,
    onLatitud: (String) -> Unit,
    onLongitud: (String) -> Unit,
    onElegirEnMapa: (Double, Double) -> Unit,
    onMiUbicacion: () -> Unit,
    onBuscar: () -> Unit,
    onReintentarRubros: () -> Unit,
    onAbrirPrestador: (Int) -> Unit,
    onFavorito: (Int) -> Unit,
) {
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Text)
    val prestadoresEnMapa = (estado.resultados as? EstadoResultados.Exito)?.prestadores.orEmpty()
    // El mapa se vuelve a encuadrar al llegar resultados nuevos, al fijar la ubicación
    // con el botón «Usar mi ubicación» y cuando la persona lo pide.
    var reencuadres by rememberSaveable { mutableIntStateOf(0) }
    val encuadre = remember(prestadoresEnMapa, estado.ubicando, reencuadres) { Any() }.hashCode()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text(stringResource(R.string.busqueda_rubro), style = MaterialTheme.typography.titleMedium) }
        item {
            when (val rubros = estado.rubros) {
                EstadoRubros.Cargando -> Cargando()
                is EstadoRubros.Error -> Mensaje(textoError(rubros.tipo), textoBoton = stringResource(R.string.reintentar), onBoton = onReintentarRubros)
                is EstadoRubros.Listo ->
                    if (rubros.rubros.isEmpty()) {
                        Mensaje(stringResource(R.string.busqueda_sin_rubros))
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(rubros.rubros, key = { it.id }) { rubro ->
                                FilterChip(
                                    selected = rubro.id == estado.rubroId,
                                    onClick = { onRubro(rubro.id) },
                                    label = { Text(rubro.nombre) },
                                )
                            }
                        }
                    }
            }
        }
        if (estado.faltaRubro) {
            item { Text(stringResource(R.string.error_falta_rubro), color = MaterialTheme.colorScheme.error) }
        }
        item { Text(stringResource(R.string.busqueda_ubicacion), style = MaterialTheme.typography.titleMedium) }
        item { Text(stringResource(R.string.mapa_ayuda), style = MaterialTheme.typography.bodyMedium) }
        item {
            MapaBusqueda(
                ubicacion = estado.ubicacion,
                prestadores = prestadoresEnMapa,
                encuadre = encuadre,
                onElegirUbicacion = onElegirEnMapa,
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.mapa_leyenda), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { reencuadres++ }, enabled = estado.ubicacion != null) {
                    Text(stringResource(R.string.mapa_encuadrar))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = estado.latitud,
                    onValueChange = onLatitud,
                    label = { Text(stringResource(R.string.latitud)) },
                    isError = estado.latitudInvalida,
                    supportingText = if (estado.latitudInvalida) ({ Text(stringResource(R.string.error_latitud)) }) else null,
                    singleLine = true,
                    keyboardOptions = decimal,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = estado.longitud,
                    onValueChange = onLongitud,
                    label = { Text(stringResource(R.string.longitud)) },
                    isError = estado.longitudInvalida,
                    supportingText = if (estado.longitudInvalida) ({ Text(stringResource(R.string.error_longitud)) }) else null,
                    singleLine = true,
                    keyboardOptions = decimal,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (estado.ubicacionNoDisponible) {
            item { Text(stringResource(R.string.ubicacion_no_disponible), color = MaterialTheme.colorScheme.secondary) }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onMiUbicacion, enabled = !estado.ubicando, modifier = Modifier.weight(1f)) {
                    Text(stringResource(if (estado.ubicando) R.string.ubicando else R.string.usar_mi_ubicacion))
                }
                Button(
                    onClick = onBuscar,
                    enabled = estado.resultados != EstadoResultados.Cargando,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.buscar)) }
            }
        }
        when (val resultados = estado.resultados) {
            EstadoResultados.Inicial -> item { Mensaje(stringResource(R.string.busqueda_inicial)) }
            EstadoResultados.Cargando -> item { Cargando() }
            EstadoResultados.Vacio -> item { Mensaje(stringResource(R.string.busqueda_vacia)) }
            is EstadoResultados.Error -> item {
                Mensaje(textoError(resultados.tipo), textoBoton = stringResource(R.string.reintentar), onBoton = onBuscar)
            }
            is EstadoResultados.Exito -> {
                if (resultados.desdeCache) item { AvisoSinConexion(Modifier.padding(0.dp)) }
                items(resultados.prestadores, key = { it.id }) { prestador ->
                    PrestadorCard(
                        prestador = prestador,
                        favorito = prestador.id in estado.favoritos,
                        onAbrir = { onAbrirPrestador(prestador.id) },
                        onFavorito = { onFavorito(prestador.id) },
                    )
                }
            }
        }
    }
}
