package pe.tecsup.chambape.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import pe.tecsup.chambape.AppContainer
import pe.tecsup.chambape.ChambaPeApplication
import pe.tecsup.chambape.R
import pe.tecsup.chambape.domain.model.Prestador
import pe.tecsup.chambape.domain.model.TipoError
import pe.tecsup.chambape.ui.busqueda.textoCalificacion

/** Crea un ViewModel con las dependencias del [AppContainer]. */
@Composable
inline fun <reified VM : ViewModel> chambaViewModel(key: String? = null, crossinline crear: (AppContainer) -> VM): VM {
    val container = (LocalContext.current.applicationContext as ChambaPeApplication).container
    return viewModel(key = key, factory = viewModelFactory { initializer { crear(container) } })
}

@Composable
fun textoError(tipo: TipoError): String = stringResource(
    when (tipo) {
        TipoError.SIN_CONEXION -> R.string.error_sin_conexion
        TipoError.SERVIDOR -> R.string.error_servidor
        TipoError.DATOS_INVALIDOS -> R.string.error_datos_invalidos
        TipoError.CUENTA_NO_VINCULADA -> R.string.error_cuenta_no_vinculada
    },
)

/** Icono que acompaña a cada tipo de error: dice de un vistazo si es la red, el servidor o la cuenta. */
fun iconoError(tipo: TipoError): ImageVector = when (tipo) {
    TipoError.SIN_CONEXION -> Icons.Outlined.WifiOff
    TipoError.SERVIDOR -> Icons.Outlined.CloudOff
    TipoError.DATOS_INVALIDOS -> Icons.Outlined.ErrorOutline
    TipoError.CUENTA_NO_VINCULADA -> Icons.Outlined.LinkOff
}

@Composable
fun Cargando(modifier: Modifier = Modifier) {
    val descripcion = stringResource(R.string.cargando)
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator()
        Text(descripcion, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

/** Estado vacío o de error: icono, texto y, si hay, una acción para salir de ahí (por ejemplo, Reintentar). */
@Composable
fun Mensaje(
    texto: String,
    modifier: Modifier = Modifier,
    textoBoton: String? = null,
    icono: ImageVector? = null,
    onBoton: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icono != null) {
            Box(
                modifier = Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icono, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(34.dp))
            }
        }
        Text(
            texto,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (textoBoton != null) FilledTonalButton(onClick = onBoton) { Text(textoBoton) }
    }
}

@Composable
fun AvisoSinConexion(modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Outlined.WifiOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(stringResource(R.string.aviso_cache), color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Círculo con las iniciales: identifica a la persona sin pedir una foto. */
@Composable
fun Iniciales(nombre: String, modifier: Modifier = Modifier, tamano: Dp = 44.dp) {
    val iniciales = inicialesDe(nombre)
    Box(
        modifier = modifier.size(tamano).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            iniciales,
            style = if (tamano >= 64.dp) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/** Promedio con una estrella dibujada (no un carácter): «4,8 · 23 calificaciones». */
@Composable
fun Calificacion(prestador: Prestador, modifier: Modifier = Modifier) {
    val texto = textoCalificacion(prestador)
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = texto },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (prestador.tieneCalificaciones) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
        }
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Corazón de favorito: al marcarlo late una vez (rebote corto); al quitarlo solo cambia. */
@Composable
fun BotonFavorito(favorito: Boolean, descripcion: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val escala = remember { Animatable(1f) }
    // Solo late al pasar de no marcado a marcado, no al aparecer en pantalla ya marcado.
    var previo by remember { mutableStateOf(favorito) }
    LaunchedEffect(favorito) {
        if (favorito && !previo) {
            escala.animateTo(1.3f, tween(Movimiento.CORTA, easing = Movimiento.Desacelerada))
            escala.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
        previo = favorito
    }
    IconButton(onClick = onClick, modifier = modifier) {
        AnimatedContent(
            targetState = favorito,
            transitionSpec = { (scaleIn(initialScale = 0.6f) + fadeIn()) togetherWith fadeOut(tween(Movimiento.CORTA)) },
            label = "favorito",
        ) { marcado ->
            Icon(
                imageVector = if (marcado) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = descripcion,
                tint = if (marcado) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { scaleX = escala.value; scaleY = escala.value },
            )
        }
    }
}

@Composable
fun PrestadorCard(
    prestador: Prestador,
    favorito: Boolean,
    onAbrir: () -> Unit,
    onFavorito: () -> Unit,
    modifier: Modifier = Modifier,
    /** El rubro buscado va primero (su ícono y su nombre), aunque el prestador tenga otros. */
    rubroBuscado: String? = null,
    /** HU-10: marca al prestador que la IA recomienda (el mismo de la tarjeta de arriba). */
    recomendado: Boolean = false,
) {
    val rubros = prestador.rubros.sortedByDescending { it == rubroBuscado }
    Card(
        onClick = onAbrir,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp, pressedElevation = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Iniciales(prestador.nombreCompleto)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (recomendado) {
                    Text(
                        stringResource(R.string.recomendado_insignia),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(prestador.nombreCompleto, style = MaterialTheme.typography.titleMedium)
                if (rubros.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(iconoRubro(rubros.first()), contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(rubros.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    prestador.distanciaKm?.let {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Icon(Icons.Outlined.NearMe, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.distancia_km, it), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Calificacion(prestador)
                }
            }
            BotonFavorito(
                favorito = favorito,
                descripcion = stringResource(
                    if (favorito) R.string.quitar_favorito else R.string.agregar_favorito,
                    prestador.nombreCompleto,
                ),
                onClick = onFavorito,
            )
        }
    }
}

@Composable
fun PantallaCentrada(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}
