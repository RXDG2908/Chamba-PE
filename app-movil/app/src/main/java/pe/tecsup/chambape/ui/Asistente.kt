package pe.tecsup.chambape.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import pe.tecsup.chambape.R

/** Un paso del asistente: título corto (para la barra de progreso), pregunta e icono. */
data class Paso(@StringRes val titulo: Int, @StringRes val ayuda: Int, val icono: ImageVector)

/**
 * Formulario por pasos (un tema por pantalla), según form-design: progreso visible, cada paso
 * completable por sí solo, volver atrás sin perder datos. El Back del sistema retrocede un paso;
 * en el primero sale del asistente. Entre pasos, transición de eje compartido X (Material).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Asistente(
    @StringRes titulo: Int,
    pasos: List<Paso>,
    actual: Int,
    onAtras: () -> Unit,
    onSiguiente: () -> Unit,
    @StringRes textoSiguiente: Int,
    ocupado: Boolean,
    error: String?,
    modifier: Modifier = Modifier,
    pie: @Composable ColumnScope.() -> Unit = {},
    contenido: @Composable ColumnScope.(paso: Int) -> Unit,
) {
    BackHandler(enabled = !ocupado, onBack = onAtras)
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titulo)) },
                navigationIcon = {
                    IconButton(onClick = onAtras, enabled = !ocupado) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.volver))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            Surface(tonalElevation = 2.dp, shadowElevation = 8.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AvisoError(error)
                    BotonPrincipal(
                        texto = stringResource(textoSiguiente),
                        onClick = onSiguiente,
                        cargando = ocupado,
                        icono = if (actual < pasos.lastIndex) Icons.AutoMirrored.Filled.ArrowForward else null,
                    )
                    pie()
                }
            }
        },
    ) { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno),
        ) {
            Progreso(pasos, actual, modifier = Modifier.padding(horizontal = 20.dp))
            // Avanzar entra desde la derecha; volver, desde la izquierda.
            AnimatedContent(
                targetState = actual,
                transitionSpec = {
                    val adelante = targetState > initialState
                    val dentro = tween<androidx.compose.ui.unit.IntOffset>(Movimiento.MEDIA, easing = Movimiento.Enfatizada)
                    (slideInHorizontally(dentro) { if (adelante) it / 5 else -it / 5 } +
                        fadeIn(tween(Movimiento.MEDIA, delayMillis = 60))) togetherWith
                        (slideOutHorizontally(dentro) { if (adelante) -it / 5 else it / 5 } +
                            fadeOut(tween(Movimiento.CORTA)))
                },
                label = "paso",
                modifier = Modifier.weight(1f),
            ) { paso ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(modifier = Modifier.widthIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        EncabezadoPaso(pasos[paso])
                        contenido(paso)
                    }
                }
            }
        }
    }
}

/** Barra segmentada: los pasos hechos y el actual se llenan con el color de marca. */
@Composable
private fun Progreso(pasos: List<Paso>, actual: Int, modifier: Modifier = Modifier) {
    val texto = stringResource(R.string.paso_de, actual + 1, pasos.size, stringResource(pasos[actual].titulo))
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = texto },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            pasos.indices.forEach { i ->
                val lleno by animateFloatAsState(
                    targetValue = if (i <= actual) 1f else 0f,
                    animationSpec = tween(Movimiento.LARGA, easing = Movimiento.Enfatizada),
                    label = "segmento",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = lleno
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                            }
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
        Text(
            stringResource(R.string.paso_numero, actual + 1, pasos.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun EncabezadoPaso(paso: Paso) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(paso.icono, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
        }
        Text(
            stringResource(paso.titulo),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(paso.ayuda), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Botón principal: se hunde al presionar y muestra un indicador mientras trabaja (sin saltos de tamaño). */
@Composable
fun BotonPrincipal(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cargando: Boolean = false,
    habilitado: Boolean = true,
    icono: ImageVector? = null,
) {
    val interaccion = remember { MutableInteractionSource() }
    val presionado by interaccion.collectIsPressedAsState()
    val escala by animateFloatAsState(
        if (presionado) 0.97f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "presion",
    )
    Button(
        onClick = onClick,
        enabled = habilitado && !cargando,
        interactionSource = interaccion,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .graphicsLayer { scaleX = escala; scaleY = escala },
    ) {
        Crossfade(targetState = cargando, animationSpec = tween(Movimiento.CORTA), label = "carga") { trabajando ->
            if (trabajando) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.procesando))
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(texto)
                    if (icono != null) Icon(icono, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** Error general: aparece deslizando y se anuncia a TalkBack; nombra el problema y cómo seguir. */
@Composable
fun AvisoError(texto: String?, modifier: Modifier = Modifier) {
    // Se conserva el último texto mientras sale, para que no desaparezca a mitad de la animación.
    var ultimo by remember { mutableStateOf(texto) }
    if (texto != null) ultimo = texto
    AnimatedVisibility(
        visible = texto != null,
        enter = expandVertically(tween(Movimiento.MEDIA, easing = Movimiento.Enfatizada)) + fadeIn(),
        exit = shrinkVertically(tween(Movimiento.CORTA)) + fadeOut(tween(Movimiento.CORTA)),
        modifier = modifier,
    ) {
        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .semantics { liveRegion = LiveRegionMode.Assertive },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Text(ultimo.orEmpty(), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * Campo con etiqueta persistente, icono, mensaje de error debajo (con animación) y, para la
 * clave, botón para mostrarla u ocultarla.
 */
@Composable
fun CampoFormulario(
    valor: String,
    onCambio: (String) -> Unit,
    @StringRes etiqueta: Int,
    icono: ImageVector,
    modifier: Modifier = Modifier,
    error: String? = null,
    @StringRes ayuda: Int? = null,
    habilitado: Boolean = true,
    teclado: KeyboardOptions = KeyboardOptions.Default,
    alConfirmar: (() -> Unit)? = null,
    clave: Boolean = false,
    /** Pone el cursor aquí al aparecer: en cada paso se escribe sin tener que tocar el campo. */
    enfocar: Boolean = false,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    val foco = remember { FocusRequester() }
    if (enfocar) {
        LaunchedEffect(Unit) {
            delay(Movimiento.MEDIA.toLong()) // tras la transición del paso
            runCatching { foco.requestFocus() }
        }
    }
    val colorIcono by animateColorAsState(
        if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        tween(Movimiento.CORTA),
        label = "icono",
    )
    OutlinedTextField(
        value = valor,
        onValueChange = onCambio,
        label = { Text(stringResource(etiqueta)) },
        leadingIcon = { Icon(icono, contentDescription = null, tint = colorIcono) },
        trailingIcon = if (clave) {
            {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = stringResource(if (visible) R.string.ocultar_clave else R.string.mostrar_clave),
                    )
                }
            }
        } else null,
        isError = error != null,
        supportingText = when {
            error != null -> ({ Text(error) })
            ayuda != null -> ({ Text(stringResource(ayuda)) })
            else -> null
        },
        singleLine = true,
        enabled = habilitado,
        keyboardOptions = teclado.copy(imeAction = if (alConfirmar != null) ImeAction.Done else ImeAction.Next),
        keyboardActions = KeyboardActions(onDone = { alConfirmar?.invoke() }),
        visualTransformation = if (clave && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth().focusRequester(foco),
    )
}
