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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import pe.tecsup.chambape.ui.Seccion
import pe.tecsup.chambape.ui.chambaViewModel
import pe.tecsup.chambape.ui.coloresSeccion
import pe.tecsup.chambape.ui.iconoError
import pe.tecsup.chambape.ui.iconoRubro
import pe.tecsup.chambape.ui.textoError

/**
 * Pantalla de una sección («Urgente», «Hogar»…): se entra desde su tarjeta en Inicio (patrón
 * hub) y se vuelve con la flecha o el gesto Atrás. [rubroInicial] llega elegido si se tocó un
 * rubro en Inicio (búsqueda o «Lo más pedido»).
 */
@Composable
fun SeccionRoute(seccion: Seccion, rubroInicial: Int?, onVolver: () -> Unit, onAbrirPrestador: (Int) -> Unit) {
    val vm = chambaViewModel {
        BusquedaViewModel(it.prestadorRepository, it.contextoBusqueda, it.almacenDireccion, it.ubicacionSesion)
    }
    val estado by vm.uiState.collectAsStateWithLifecycle()
    val guardada by vm.direccionGuardada.collectAsStateWithLifecycle()
    var eligiendoLugar by rememberSaveable { mutableStateOf(false) }
    // El rubro de Inicio se elige una sola vez (al volver de un detalle no se repite la búsqueda).
    var inicialAplicado by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(rubroInicial, inicialAplicado) {
        if (rubroInicial != null && !inicialAplicado) {
            vm.seleccionarRubro(rubroInicial)
            inicialAplicado = true
        }
    }
    BusquedaScreen(
        seccion = seccion,
        estado = estado,
        onVolver = onVolver,
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
    seccion: Seccion,
    estado: BusquedaUiState,
    onVolver: () -> Unit,
    onCambiarUbicacion: () -> Unit,
    onRubro: (Int) -> Unit,
    onReintentar: () -> Unit,
    onReintentarRubros: () -> Unit,
    onAbrirPrestador: (Int) -> Unit,
    onFavorito: (Int) -> Unit,
) {
    var vista by rememberSaveable { mutableStateOf(Vista.LISTA) }
    val lista = rememberLazyListState()
    val rubros = (estado.rubros as? EstadoRubros.Listo)?.rubros.orEmpty()
    val rubrosSeccion = seccion.rubrosDe(rubros)
    // Al llegar resultados se baja hasta ellos.
    LaunchedEffect(estado.resultados) {
        if (estado.resultados is EstadoResultados.Exito || estado.resultados == EstadoResultados.Vacio) {
            val indice = lista.layoutInfo.totalItemsCount - 1
            lista.animateScrollToItem(indiceResultados(lista).coerceAtMost(indice.coerceAtLeast(0)))
        }
    }
    LazyColumn(
        state = lista,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "encabezado") { EncabezadoSeccion(seccion, onVolver) }
        item(key = "ubicacion") {
            BarraUbicacion(
                direccion = estado.direccion,
                onClick = onCambiarUbicacion,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        item(key = "rubros") {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                when (val estadoRubros = estado.rubros) {
                    EstadoRubros.Cargando -> Cargando()
                    is EstadoRubros.Error -> Mensaje(
                        textoError(estadoRubros.tipo), textoBoton = stringResource(R.string.reintentar),
                        icono = iconoError(estadoRubros.tipo), onBoton = onReintentarRubros,
                    )
                    is EstadoRubros.Listo ->
                        if (rubrosSeccion.isEmpty()) Mensaje(stringResource(R.string.busqueda_sin_rubros))
                        else GrillaSeccion(seccion, rubrosSeccion, estado.rubroId, onRubro)
                }
            }
        }
        if (estado.faltaRubro) item { Box(Modifier.padding(horizontal = 16.dp)) { AvisoError(stringResource(R.string.error_falta_rubro)) } }
        if (estado.faltaDireccion) item { Box(Modifier.padding(horizontal = 16.dp)) { AvisoError(stringResource(R.string.error_falta_direccion)) } }

        when (val resultados = estado.resultados) {
            EstadoResultados.Inicial -> item {
                Box(Modifier.padding(horizontal = 16.dp)) {
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
            }
            EstadoResultados.Cargando -> item { Cargando() }
            EstadoResultados.Vacio -> item(key = CLAVE_RESULTADOS) {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    Mensaje(
                        stringResource(R.string.busqueda_vacia),
                        textoBoton = stringResource(R.string.ubicacion_cambiar),
                        icono = Icons.Outlined.SearchOff,
                        onBoton = onCambiarUbicacion,
                    )
                }
            }
            is EstadoResultados.Error -> item {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    Mensaje(
                        textoError(resultados.tipo), textoBoton = stringResource(R.string.reintentar),
                        icono = iconoError(resultados.tipo), onBoton = onReintentar,
                    )
                }
            }
            is EstadoResultados.Exito -> {
                if (resultados.desdeCache) item { Box(Modifier.padding(horizontal = 16.dp)) { AvisoSinConexion() } }
                item(key = CLAVE_RESULTADOS) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp).animateItem(),
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
                        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            modifier = Modifier.padding(horizontal = 16.dp).animateItem(),
                            rubroBuscado = rubros.firstOrNull { it.id == estado.rubroId }?.nombre,
                        )
                    }
                }
            }
        }
    }
}

/** Cabecera con el color de la sección: dice dónde estás y cómo volver (wayfinding). */
@Composable
private fun EncabezadoSeccion(seccion: Seccion, onVolver: () -> Unit) {
    val (fondo, tinta) = coloresSeccion(seccion)
    Surface(color = fondo, contentColor = tinta, shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // El ícono grande de la sección, recortado en la esquina: da carácter sin tapar texto.
            Icon(
                seccion.icono,
                contentDescription = null,
                tint = tinta.copy(alpha = 0.14f),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp).size(132.dp),
            )
            Column(modifier = Modifier.padding(start = 8.dp, end = 24.dp, top = 8.dp, bottom = 24.dp)) {
                IconButton(onClick = onVolver) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.volver))
                }
                Text(
                    stringResource(seccion.titulo),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp).semantics { heading() },
                )
                Text(
                    stringResource(seccion.descripcion),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp, top = 2.dp),
                )
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

private const val CLAVE_RESULTADOS = "titulo-resultados"

/** Posición del encabezado de resultados en la lista (o la última, si aún no se compuso). */
private fun indiceResultados(lista: LazyListState): Int =
    lista.layoutInfo.visibleItemsInfo.firstOrNull { it.key == CLAVE_RESULTADOS }?.index
        ?: (lista.layoutInfo.totalItemsCount - 1)

/** Los rubros de la sección en grilla de 4, con el color de la sección. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GrillaSeccion(seccion: Seccion, rubros: List<Rubro>, elegido: Int?, onRubro: (Int) -> Unit) {
    val (fondo, tinta) = coloresSeccion(seccion)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.busqueda_rubro),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            maxItemsInEachRow = 4,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            rubros.forEach { rubro ->
                OpcionRubro(rubro, rubro.id == elegido, fondo, tinta, { onRubro(rubro.id) }, Modifier.fillMaxWidth(0.25f))
            }
        }
    }
}

@Composable
internal fun OpcionRubro(
    rubro: Rubro,
    elegido: Boolean,
    fondoSeccion: Color,
    tintaSeccion: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val esquema = MaterialTheme.colorScheme
    val fondo by animateColorAsState(if (elegido) esquema.primary else fondoSeccion, label = "fondo-rubro")
    val icono by animateColorAsState(if (elegido) esquema.onPrimary else tintaSeccion, label = "icono-rubro")
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
