package pe.tecsup.chambape.ui.busqueda

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.tecsup.chambape.R
import pe.tecsup.chambape.domain.model.Rubro
import pe.tecsup.chambape.ui.Cargando
import pe.tecsup.chambape.ui.Mensaje
import pe.tecsup.chambape.ui.Seccion
import pe.tecsup.chambape.ui.chambaViewModel
import pe.tecsup.chambape.ui.coincide
import pe.tecsup.chambape.ui.coloresSeccion
import pe.tecsup.chambape.ui.iconoError
import pe.tecsup.chambape.ui.iconoRubro
import pe.tecsup.chambape.ui.populares
import pe.tecsup.chambape.ui.seccionDe
import pe.tecsup.chambape.ui.textoError

/**
 * Inicio del cliente (patrón hub, como Rappi): la ubicación arriba, un buscador que entiende
 * «gasfitero» o «wifi», una tarjeta grande por sección y «Lo más pedido». Cada tarjeta abre su
 * sección en otra pantalla.
 */
@Composable
fun InicioRoute(nombre: String, onAbrirSeccion: (Seccion, Int?) -> Unit) {
    val vm = chambaViewModel(key = "inicio") {
        BusquedaViewModel(it.prestadorRepository, null, it.almacenDireccion, it.ubicacionSesion)
    }
    val estado by vm.uiState.collectAsStateWithLifecycle()
    val guardada by vm.direccionGuardada.collectAsStateWithLifecycle()
    var eligiendoLugar by rememberSaveable { mutableStateOf(false) }
    InicioScreen(
        nombre = nombre,
        estado = estado,
        onCambiarUbicacion = { eligiendoLugar = true },
        onAbrirSeccion = onAbrirSeccion,
        onReintentarRubros = vm::cargarRubros,
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

@Composable
fun InicioScreen(
    nombre: String,
    estado: BusquedaUiState,
    onCambiarUbicacion: () -> Unit,
    onAbrirSeccion: (Seccion, Int?) -> Unit,
    onReintentarRubros: () -> Unit,
) {
    var texto by rememberSaveable { mutableStateOf("") }
    val rubros = (estado.rubros as? EstadoRubros.Listo)?.rubros.orEmpty()
    val coincidencias = if (texto.isBlank()) emptyList() else rubros.filter { coincide(it, texto) }
    val abrirRubro = { rubro: Rubro -> onAbrirSeccion(seccionDe(rubro), rubro.id) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "ubicacion") { BarraUbicacion(direccion = estado.direccion, onClick = onCambiarUbicacion) }
        item(key = "saludo") {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (nombre.isNotBlank()) {
                    Text(
                        stringResource(R.string.inicio_hola, nombre.substringBefore(' ')),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    stringResource(R.string.inicio_titulo),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
            }
        }
        item(key = "buscador") {
            OutlinedTextField(
                value = texto,
                onValueChange = { texto = it.take(40) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.inicio_buscar)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (texto.isNotEmpty()) {
                        IconButton(onClick = { texto = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.ubicacion_borrar_texto))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            )
        }

        when {
            estado.rubros is EstadoRubros.Cargando -> item { Cargando() }
            estado.rubros is EstadoRubros.Error -> item {
                val tipo = (estado.rubros as EstadoRubros.Error).tipo
                Mensaje(textoError(tipo), textoBoton = stringResource(R.string.reintentar), icono = iconoError(tipo), onBoton = onReintentarRubros)
            }
            texto.isNotBlank() -> {
                // Con texto, los resultados reemplazan a las tarjetas: una sola cosa a la vez.
                if (coincidencias.isEmpty()) {
                    item { Mensaje(stringResource(R.string.inicio_sin_coincidencias, texto.trim()), icono = Icons.Outlined.SearchOff) }
                } else {
                    items(coincidencias, key = { "busca-${it.id}" }) { rubro -> ResultadoRubro(rubro, onClick = { abrirRubro(rubro) }) }
                }
            }
            else -> {
                val conRubros = Seccion.entries.filter { it.rubrosDe(rubros).isNotEmpty() }
                if (Seccion.URGENTE in conRubros) {
                    item(key = "tarjeta-urgente") {
                        TarjetaSeccion(Seccion.URGENTE, Seccion.URGENTE.rubrosDe(rubros), ancha = true, onClick = { onAbrirSeccion(Seccion.URGENTE, null) })
                    }
                }
                // El resto, de dos en dos: tarjetas cuadradas y grandes, fáciles de tocar (fitts-law).
                conRubros.filter { it != Seccion.URGENTE }.chunked(2).forEach { par ->
                    item(key = "par-${par.first().name}") {
                        if (par.size == 1) {
                            // Si una queda sola, ocupa todo el ancho (sin medio espacio vacío).
                            val seccion = par.single()
                            TarjetaSeccion(seccion, seccion.rubrosDe(rubros), ancha = true, onClick = { onAbrirSeccion(seccion, null) })
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                par.forEach { seccion ->
                                    TarjetaSeccion(
                                        seccion, seccion.rubrosDe(rubros), ancha = false,
                                        onClick = { onAbrirSeccion(seccion, null) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
                val masPedidos = populares(rubros)
                if (masPedidos.isNotEmpty()) {
                    item(key = "populares") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(R.string.inicio_populares),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.semantics { heading() },
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(masPedidos, key = { it.id }) { rubro ->
                                    val (fondo, tinta) = coloresSeccion(seccionDe(rubro))
                                    OpcionRubro(rubro, false, fondo, tinta, { abrirRubro(rubro) }, Modifier.width(88.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tarjeta grande de una sección: nombre, para qué sirve, cuántos servicios trae y sus íconos.
 * El ícono de la sección, grande y recortado, hace de ilustración (sin imágenes que cargar).
 */
@Composable
private fun TarjetaSeccion(
    seccion: Seccion,
    rubros: List<Rubro>,
    ancha: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (fondo, tinta) = coloresSeccion(seccion)
    val titulo = stringResource(seccion.titulo)
    val cuantos = pluralStringResource(R.plurals.inicio_servicios, rubros.size, rubros.size)
    val descripcion = stringResource(R.string.inicio_abrir_seccion, titulo, cuantos)
    Surface(
        onClick = onClick,
        color = fondo,
        contentColor = tinta,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
            .fillMaxWidth()
            .then(if (ancha) Modifier.height(168.dp) else Modifier.aspectRatio(1f))
            .semantics(mergeDescendants = true) { contentDescription = descripcion },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Icon(
                seccion.icono,
                contentDescription = null,
                tint = tinta.copy(alpha = 0.16f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 18.dp, y = 18.dp)
                    .size(if (ancha) 150.dp else 116.dp),
            )
            Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(seccion.icono, contentDescription = null, modifier = Modifier.size(22.dp))
                        Text(
                            titulo,
                            style = if (ancha) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        stringResource(seccion.descripcion),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (ancha) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Los primeros rubros, como fichas: se ve qué hay dentro antes de entrar.
                    rubros.take(if (ancha) 4 else 3).forEach { rubro ->
                        Box(
                            modifier = Modifier.size(if (ancha) 40.dp else 34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(iconoRubro(rubro.nombre), contentDescription = null, modifier = Modifier.size(if (ancha) 22.dp else 18.dp))
                        }
                    }
                    if (ancha) {
                        Box(Modifier.weight(1f))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(cuantos, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Un rubro encontrado por el buscador, con la sección donde se abrirá. */
@Composable
private fun ResultadoRubro(rubro: Rubro, onClick: () -> Unit) {
    val seccion = seccionDe(rubro)
    val (fondo, tinta) = coloresSeccion(seccion)
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        ListItem(
            headlineContent = { Text(rubro.nombre, fontWeight = FontWeight.SemiBold) },
            supportingContent = { Text(stringResource(R.string.inicio_en_seccion, stringResource(seccion.titulo))) },
            leadingContent = {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(fondo), contentAlignment = Alignment.Center) {
                    Icon(iconoRubro(rubro.nombre), contentDescription = null, tint = tinta)
                }
            },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        )
    }
}
