package pe.tecsup.chambape.ui.busqueda

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.tecsup.chambape.R
import pe.tecsup.chambape.domain.model.Rubro
import pe.tecsup.chambape.ui.AvisoError
import pe.tecsup.chambape.ui.AvisoSinConexion
import pe.tecsup.chambape.ui.Cargando
import pe.tecsup.chambape.ui.Mensaje
import pe.tecsup.chambape.ui.PrestadorCard
import pe.tecsup.chambape.ui.chambaViewModel
import pe.tecsup.chambape.ui.iconoError
import pe.tecsup.chambape.ui.iconoRubro
import pe.tecsup.chambape.ui.textoError

/** Conecta el ViewModel con la pantalla; [BusquedaScreen] se puede probar sin él. */
@Composable
fun BusquedaRoute(onAbrirPrestador: (Int) -> Unit) {
    val vm = chambaViewModel { BusquedaViewModel(it.prestadorRepository, it.contextoBusqueda, it.almacenDireccion) }
    val estado by vm.uiState.collectAsStateWithLifecycle()
    val guardada by vm.direccionGuardada.collectAsStateWithLifecycle()
    var eligiendoLugar by rememberSaveable { mutableStateOf(false) }
    BusquedaScreen(
        estado = estado,
        onCambiarUbicacion = { eligiendoLugar = true },
        onRubro = vm::seleccionarRubro,
        onReintentar = vm::buscar,
        onReintentarRubros = vm::cargarRubros,
        onAbrirPrestador = onAbrirPrestador,
        onFavorito = vm::alternarFavorito,
    )
    if (eligiendoLugar) {
        HojaUbicacion(
            titulo = stringResource(R.string.ubicacion_hoja_titulo),
            actual = estado.direccion,
            guardada = guardada,
            onElegida = vm::elegirDireccion,
            onCerrar = { eligiendoLugar = false },
        )
    }
}

private enum class Vista { LISTA, MAPA }

@Composable
fun BusquedaScreen(
    estado: BusquedaUiState,
    onCambiarUbicacion: () -> Unit,
    onRubro: (Int) -> Unit,
    onReintentar: () -> Unit,
    onReintentarRubros: () -> Unit,
    onAbrirPrestador: (Int) -> Unit,
    onFavorito: (Int) -> Unit,
) {
    var vista by rememberSaveable { mutableStateOf(Vista.LISTA) }
    Column(modifier = Modifier.fillMaxSize()) {
        BarraUbicacion(
            direccion = estado.direccion,
            onClick = onCambiarUbicacion,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.busqueda_rubro),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
            }
            item {
                when (val rubros = estado.rubros) {
                    EstadoRubros.Cargando -> Cargando()
                    is EstadoRubros.Error -> Mensaje(
                        textoError(rubros.tipo), textoBoton = stringResource(R.string.reintentar),
                        icono = iconoError(rubros.tipo), onBoton = onReintentarRubros,
                    )
                    is EstadoRubros.Listo ->
                        if (rubros.rubros.isEmpty()) Mensaje(stringResource(R.string.busqueda_sin_rubros))
                        else GrillaRubros(rubros.rubros, estado.rubroId, onRubro)
                }
            }
            if (estado.faltaRubro) item { AvisoError(stringResource(R.string.error_falta_rubro)) }
            if (estado.faltaDireccion) item { AvisoError(stringResource(R.string.error_falta_direccion)) }

            when (val resultados = estado.resultados) {
                EstadoResultados.Inicial -> item {
                    if (estado.direccion == null) {
                        Mensaje(
                            stringResource(R.string.busqueda_sin_ubicacion),
                            textoBoton = stringResource(R.string.ubicacion_elegir_boton),
                            icono = Icons.Outlined.TravelExplore,
                            onBoton = onCambiarUbicacion,
                        )
                    } else {
                        Mensaje(stringResource(R.string.busqueda_inicial), icono = Icons.Outlined.TravelExplore)
                    }
                }
                EstadoResultados.Cargando -> item { Cargando() }
                EstadoResultados.Vacio -> item {
                    Mensaje(
                        stringResource(R.string.busqueda_vacia),
                        textoBoton = stringResource(R.string.ubicacion_cambiar),
                        icono = Icons.Outlined.SearchOff,
                        onBoton = onCambiarUbicacion,
                    )
                }
                is EstadoResultados.Error -> item {
                    Mensaje(
                        textoError(resultados.tipo), textoBoton = stringResource(R.string.reintentar),
                        icono = iconoError(resultados.tipo), onBoton = onReintentar,
                    )
                }
                is EstadoResultados.Exito -> {
                    if (resultados.desdeCache) item { AvisoSinConexion() }
                    item(key = "titulo-resultados") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).animateItem(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                pluralStringResource(R.plurals.resultados_encontrados, resultados.prestadores.size, resultados.prestadores.size),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f).semantics { heading() },
                            )
                            SelectorVista(vista, onCambiar = { vista = it })
                        }
                    }
                    if (vista == Vista.MAPA) {
                        item(key = "mapa-resultados") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                MapaBusqueda(
                                    ubicacion = estado.ubicacion,
                                    prestadores = resultados.prestadores,
                                    encuadre = resultados.prestadores.hashCode(),
                                    onElegirUbicacion = null,
                                    alto = 420.dp,
                                )
                                Text(
                                    stringResource(R.string.mapa_leyenda),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        items(resultados.prestadores, key = { it.id }) { prestador ->
                            PrestadorCard(
                                prestador = prestador,
                                favorito = prestador.id in estado.favoritos,
                                onAbrir = { onAbrirPrestador(prestador.id) },
                                onFavorito = { onFavorito(prestador.id) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectorVista(vista: Vista, onCambiar: (Vista) -> Unit) {
    val opciones = listOf(
        Triple(Vista.LISTA, R.string.vista_lista, Icons.AutoMirrored.Outlined.List),
        Triple(Vista.MAPA, R.string.vista_mapa, Icons.Outlined.Map),
    )
    SingleChoiceSegmentedButtonRow {
        opciones.forEachIndexed { indice, (opcion, texto, icono) ->
            SegmentedButton(
                selected = vista == opcion,
                onClick = { onCambiar(opcion) },
                shape = SegmentedButtonDefaults.itemShape(indice, opciones.size),
                icon = { Icon(icono, contentDescription = null, modifier = Modifier.size(SegmentedButtonDefaults.IconSize)) },
            ) { Text(stringResource(texto)) }
        }
    }
}

/**
 * Los rubros como un catálogo de íconos (4 por fila): se reconocen de un vistazo y un toque
 * basta, como las categorías de una app de delivery. El elegido se llena de color.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GrillaRubros(rubros: List<Rubro>, elegido: Int?, onRubro: (Int) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        maxItemsInEachRow = 4,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        rubros.forEach { rubro ->
            OpcionRubro(
                rubro = rubro,
                elegido = rubro.id == elegido,
                onClick = { onRubro(rubro.id) },
                modifier = Modifier.fillMaxWidth(0.25f),
            )
        }
    }
}

@Composable
private fun OpcionRubro(rubro: Rubro, elegido: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val esquema = MaterialTheme.colorScheme
    val fondo by animateColorAsState(if (elegido) esquema.primary else esquema.secondaryContainer, label = "fondo-rubro")
    val icono by animateColorAsState(if (elegido) esquema.onPrimary else esquema.onSecondaryContainer, label = "icono-rubro")
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .selectable(selected = elegido, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(fondo),
            contentAlignment = Alignment.Center,
        ) {
            Icon(iconoRubro(rubro.nombre), contentDescription = null, tint = icono, modifier = Modifier.size(28.dp))
        }
        Text(
            rubro.nombre,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (elegido) FontWeight.Bold else FontWeight.Medium,
            color = if (elegido) esquema.primary else esquema.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
