package pe.tecsup.chambape.ui.busqueda

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.WrongLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.tecsup.chambape.R
import pe.tecsup.chambape.device.BuscadorDirecciones
import pe.tecsup.chambape.device.Coordenadas
import pe.tecsup.chambape.device.Direccion
import pe.tecsup.chambape.device.UbicacionProvider
import pe.tecsup.chambape.ui.BotonPrincipal
import pe.tecsup.chambape.ui.chambaViewModel

// ---------- Estado ----------

sealed interface EstadoSugerencias {
    data object Inicial : EstadoSugerencias
    data object Buscando : EstadoSugerencias
    data class Lista(val direcciones: List<Direccion>) : EstadoSugerencias
    data object SinResultados : EstadoSugerencias
}

data class SelectorUiState(
    val texto: String = "",
    val sugerencias: EstadoSugerencias = EstadoSugerencias.Inicial,
    val ubicando: Boolean = false,
    val sinUbicacion: Boolean = false,
    /** Dirección bajo el alfiler del mapa; null mientras se le busca nombre. */
    val puntoMapa: Direccion? = null,
    val nombrandoPunto: Boolean = false,
)

/**
 * Convierte lo que el cliente sabe (una calle, su distrito, «aquí estoy», un punto en el mapa)
 * en una [Direccion]. Nunca le pide coordenadas. Lo usan Buscar y «Mi dirección» en Perfil.
 */
class SelectorUbicacionViewModel(
    private val buscador: BuscadorDirecciones,
    private val ubicacion: UbicacionProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SelectorUiState())
    val uiState: StateFlow<SelectorUiState> = _uiState.asStateFlow()

    private val _elegida = Channel<Direccion>(Channel.BUFFERED)
    /** Cada lugar que la persona confirma; la pantalla lo usa y cierra la hoja. */
    val elegida: Flow<Direccion> = _elegida.receiveAsFlow()

    private var busqueda: Job? = null
    private var nombrePunto: Job? = null

    fun escribir(texto: String) {
        _uiState.update { it.copy(texto = texto) }
        busqueda?.cancel()
        if (texto.trim().length < MINIMO_LETRAS) {
            _uiState.update { it.copy(sugerencias = EstadoSugerencias.Inicial) }
            return
        }
        busqueda = viewModelScope.launch {
            delay(ESPERA_TECLEO_MS) // no se consulta por cada letra
            _uiState.update { it.copy(sugerencias = EstadoSugerencias.Buscando) }
            val lista = buscador.buscar(texto)
            _uiState.update {
                it.copy(sugerencias = if (lista.isEmpty()) EstadoSugerencias.SinResultados else EstadoSugerencias.Lista(lista))
            }
        }
    }

    fun elegir(direccion: Direccion) {
        _elegida.trySend(direccion)
    }

    fun usarMiUbicacion() {
        if (_uiState.value.ubicando) return
        _uiState.update { it.copy(ubicando = true, sinUbicacion = false) }
        viewModelScope.launch {
            val punto = ubicacion.actual()
            if (punto == null) {
                _uiState.update { it.copy(ubicando = false, sinUbicacion = true) }
                return@launch
            }
            val direccion = buscador.nombrar(punto) ?: Direccion("", "", punto)
            _uiState.update { it.copy(ubicando = false) }
            elegir(direccion.copy(coordenadas = punto))
        }
    }

    fun permisoDenegado() = _uiState.update { it.copy(sinUbicacion = true) }

    /** El mapa se detuvo: se busca la calle bajo el alfiler. */
    fun moverMapa(punto: Coordenadas) {
        nombrePunto?.cancel()
        _uiState.update { it.copy(nombrandoPunto = true) }
        nombrePunto = viewModelScope.launch {
            val direccion = buscador.nombrar(punto) ?: Direccion("", "", punto)
            _uiState.update { it.copy(puntoMapa = direccion.copy(coordenadas = punto), nombrandoPunto = false) }
        }
    }

    fun confirmarMapa() {
        val punto = _uiState.value.puntoMapa ?: return
        if (_uiState.value.nombrandoPunto) return
        elegir(punto)
    }

    /** Al volver a abrir la hoja se empieza limpio. */
    fun reiniciar() {
        busqueda?.cancel()
        nombrePunto?.cancel()
        _uiState.value = SelectorUiState()
    }

    private companion object {
        const val MINIMO_LETRAS = 3
        const val ESPERA_TECLEO_MS = 350L
    }
}

// ---------- Textos ----------

/** El título de una dirección; si el Geocoder no le dio nombre de calle, un texto claro. */
@Composable
fun tituloDireccion(direccion: Direccion): String =
    direccion.titulo.ifBlank { stringResource(R.string.ubicacion_sin_calle) }

@Composable
fun textoCortoDireccion(direccion: Direccion): String =
    if (direccion.titulo.isBlank()) stringResource(R.string.ubicacion_sin_calle) else direccion.corta

// ---------- Barra superior ----------

/**
 * La ubicación arriba de todo, como la dirección de entrega en una app de delivery: siempre a la
 * vista, un toque para cambiarla. Sin dirección, invita a elegir una.
 */
@Composable
fun BarraUbicacion(direccion: Direccion?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val texto = direccion?.let { textoCortoDireccion(it) } ?: stringResource(R.string.ubicacion_elegir)
    val descripcion = stringResource(R.string.ubicacion_cambiar_descripcion, texto)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = descripcion },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.ubicacion_servicio_en),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    texto,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (direccion == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
            Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------- Hoja para elegir ----------

/**
 * Hoja inferior para elegir el lugar: escribir la calle o el distrito (con sugerencias), usar la
 * ubicación del celular, la dirección guardada, o mover el mapa bajo un alfiler fijo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaUbicacion(
    titulo: String,
    actual: Direccion?,
    guardada: Direccion?,
    onElegida: (Direccion) -> Unit,
    onCerrar: () -> Unit,
) {
    val vm = chambaViewModel(key = "selector-ubicacion") { SelectorUbicacionViewModel(it.buscadorDirecciones, it.ubicacionProvider) }
    val estado by vm.uiState.collectAsStateWithLifecycle()
    val alElegir by rememberUpdatedState(onElegida)
    val alCerrar by rememberUpdatedState(onCerrar)
    var enMapa by rememberSaveable { mutableStateOf(false) }
    val hoja = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pedirPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        if (concedido) vm.usarMiUbicacion() else vm.permisoDenegado()
    }

    LaunchedEffect(vm) {
        vm.reiniciar()
        vm.elegida.collect { direccion ->
            alElegir(direccion)
            hoja.hide()
            alCerrar()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onCerrar,
        sheetState = hoja,
        // En el mapa, arrastrar mueve el mapa, no la hoja.
        sheetGesturesEnabled = !enMapa,
    ) {
        AnimatedContent(targetState = enMapa, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "modo") { mapa ->
            if (mapa) {
                ElegirEnMapa(
                    inicial = estado.puntoMapa?.coordenadas ?: actual?.coordenadas ?: guardada?.coordenadas,
                    punto = estado.puntoMapa,
                    nombrando = estado.nombrandoPunto,
                    onMover = vm::moverMapa,
                    onConfirmar = vm::confirmarMapa,
                    onVolver = { enMapa = false },
                )
            } else {
                ListaUbicaciones(
                    titulo = titulo,
                    estado = estado,
                    guardada = guardada,
                    onTexto = vm::escribir,
                    onMiUbicacion = { pedirPermiso.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
                    onElegir = vm::elegir,
                    onMapa = { enMapa = true },
                )
            }
        }
    }
}

@Composable
private fun ListaUbicaciones(
    titulo: String,
    estado: SelectorUiState,
    guardada: Direccion?,
    onTexto: (String) -> Unit,
    onMiUbicacion: () -> Unit,
    onElegir: (Direccion) -> Unit,
    onMapa: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().imePadding(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Text(
                titulo,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp).semantics { heading() },
            )
        }
        item {
            OutlinedTextField(
                value = estado.texto,
                onValueChange = onTexto,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text(stringResource(R.string.ubicacion_escribe)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (estado.texto.isNotEmpty()) {
                        IconButton(onClick = { onTexto("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.ubicacion_borrar_texto))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onTexto(estado.texto) }),
                supportingText = { Text(stringResource(R.string.ubicacion_escribe_ayuda)) },
            )
        }
        if (estado.sugerencias == EstadoSugerencias.Buscando) {
            item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) }
        }
        when (val s = estado.sugerencias) {
            is EstadoSugerencias.Lista -> items(s.direcciones, key = { "${it.coordenadas}-${it.titulo}" }) { direccion ->
                OpcionUbicacion(
                    icono = Icons.Outlined.Place,
                    titulo = tituloDireccion(direccion),
                    detalle = direccion.detalle.ifBlank { null },
                    onClick = { onElegir(direccion) },
                )
            }
            EstadoSugerencias.SinResultados -> item {
                OpcionUbicacion(
                    icono = Icons.Outlined.WrongLocation,
                    titulo = stringResource(R.string.ubicacion_sin_resultados, estado.texto.trim()),
                    detalle = stringResource(R.string.ubicacion_sin_resultados_ayuda),
                    onClick = onMapa,
                )
            }
            else -> Unit
        }
        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
        item {
            OpcionUbicacion(
                icono = Icons.Outlined.MyLocation,
                titulo = stringResource(if (estado.ubicando) R.string.ubicando else R.string.usar_mi_ubicacion),
                detalle = if (estado.sinUbicacion) stringResource(R.string.ubicacion_no_disponible) else stringResource(R.string.ubicacion_gps_ayuda),
                destacada = true,
                error = estado.sinUbicacion,
                cargando = estado.ubicando,
                onClick = onMiUbicacion,
            )
        }
        if (guardada != null) {
            item {
                OpcionUbicacion(
                    icono = Icons.Outlined.Home,
                    titulo = stringResource(R.string.mi_direccion),
                    detalle = textoCortoDireccion(guardada),
                    onClick = { onElegir(guardada) },
                )
            }
        }
        item {
            OpcionUbicacion(
                icono = Icons.Outlined.Map,
                titulo = stringResource(R.string.ubicacion_en_mapa),
                detalle = stringResource(R.string.ubicacion_en_mapa_ayuda),
                onClick = onMapa,
            )
        }
    }
}

@Composable
private fun OpcionUbicacion(
    icono: androidx.compose.ui.graphics.vector.ImageVector,
    titulo: String,
    detalle: String?,
    onClick: () -> Unit,
    destacada: Boolean = false,
    error: Boolean = false,
    cargando: Boolean = false,
) {
    val color = when {
        error -> MaterialTheme.colorScheme.error
        destacada -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    ListItem(
        headlineContent = {
            Text(
                titulo,
                fontWeight = if (destacada) FontWeight.SemiBold else null,
                color = if (destacada && !error) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = detalle?.let {
            { Text(it, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        },
        leadingContent = {
            if (cargando) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            else Icon(icono, contentDescription = null, tint = color)
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick),
    )
}

private val LIMA_CENTRO = LatLng(-12.046374, -77.042793)

/** Mapa con un alfiler fijo al centro: la persona mueve el mapa hasta su puerta y confirma. */
@Composable
private fun ElegirEnMapa(
    inicial: Coordenadas?,
    punto: Direccion?,
    nombrando: Boolean,
    onMover: (Coordenadas) -> Unit,
    onConfirmar: () -> Unit,
    onVolver: () -> Unit,
) {
    val camara = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(inicial?.let { LatLng(it.latitud, it.longitud) } ?: LIMA_CENTRO, 16f)
    }
    val alMover by rememberUpdatedState(onMover)
    // Al detenerse el mapa (y al abrirlo) se busca la calle bajo el alfiler.
    LaunchedEffect(camara) {
        snapshotFlow { camara.isMoving }.filter { !it }.collect {
            val centro = camara.position.target
            alMover(Coordenadas(centro.latitude, centro.longitude))
        }
    }
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            IconButton(onClick = onVolver) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.volver))
            }
            Text(
                stringResource(R.string.ubicacion_mapa_titulo),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
        }
        val descripcionMapa = stringResource(R.string.ubicacion_mapa_descripcion)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxWidth().height(380.dp).semantics { contentDescription = descripcionMapa },
                cameraPositionState = camara,
                uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false, myLocationButtonEnabled = false),
            )
            // La punta del alfiler marca el centro exacto del mapa.
            Icon(
                Icons.Filled.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp).offset(y = (-24).dp),
            )
        }
        Column(modifier = Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (nombrando || punto == null) {
                Text(stringResource(R.string.ubicacion_buscando_calle), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(tituloDireccion(punto), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (punto.detalle.isNotBlank()) {
                    Text(punto.detalle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        BotonPrincipal(
            texto = stringResource(R.string.ubicacion_confirmar),
            onClick = onConfirmar,
            cargando = nombrando,
            habilitado = punto != null,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            icono = Icons.Filled.LocationOn,
        )
    }
}
