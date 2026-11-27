package pe.tecsup.chambape.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.tecsup.chambape.R
import pe.tecsup.chambape.domain.model.EstadoSolicitud
import pe.tecsup.chambape.domain.model.Rol
import pe.tecsup.chambape.domain.model.Sesion
import pe.tecsup.chambape.domain.model.Solicitud

// ---------- Detalle del prestador + solicitud ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetalleScreen(prestadorId: Int, sesion: Sesion) {
    val vm = chambaViewModel {
        DetalleViewModel(prestadorId, sesion, it.prestadorRepository, it.solicitudRepository, it.notificador, it.contextoBusqueda)
    }
    val estado by vm.uiState.collectAsStateWithLifecycle()
    // El permiso de notificaciones se pide al enviar; si se niega, la solicitud se envía igual.
    val pedirPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.enviar() }
    val prestador = estado.prestador

    when {
        estado.cargando -> PantallaCentrada { Cargando() }
        prestador == null -> PantallaCentrada {
            Mensaje(stringResource(R.string.detalle_no_encontrado), icono = Icons.Outlined.PersonSearch)
        }
        else -> Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Ficha: quién es, qué hace, a qué distancia y cómo lo califican.
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Iniciales(prestador.nombreCompleto, tamano = 64.dp)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(prestador.nombreCompleto, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                            Calificacion(prestador)
                        }
                        BotonFavorito(
                            favorito = prestador.favorito,
                            descripcion = stringResource(
                                if (prestador.favorito) R.string.quitar_favorito else R.string.agregar_favorito,
                                prestador.nombreCompleto,
                            ),
                            onClick = vm::alternarFavorito,
                        )
                    }
                    if (prestador.rubros.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            prestador.rubros.forEach { rubro ->
                                AssistChip(
                                    onClick = {},
                                    label = { Text(rubro) },
                                    leadingIcon = { Icon(iconoRubro(rubro), contentDescription = null, modifier = Modifier.size(18.dp)) },
                                )
                            }
                        }
                    }
                    prestador.distanciaKm?.let {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Outlined.NearMe, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.distancia_de_ti, it), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            Text(stringResource(R.string.solicitud_titulo), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            // El cliente ve a dónde irá el prestador antes de enviar (la dirección de su búsqueda).
            estado.lugar?.takeUnless { estado.enviada }?.let { lugar ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(R.string.solicitud_lugar, lugar.ifBlank { stringResource(R.string.ubicacion_sin_calle) }),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Al enviar, el formulario se transforma en la confirmación (mismo lugar, sin salto de pantalla).
            AnimatedContent(
                targetState = estado.enviada,
                transitionSpec = { (fadeIn(tween(Movimiento.MEDIA)) + scaleIn(initialScale = 0.92f)) togetherWith fadeOut(tween(Movimiento.CORTA)) },
                label = "envio",
            ) { enviada ->
                if (enviada) {
                    Mensaje(stringResource(R.string.solicitud_enviada, prestador.nombreCompleto), icono = Icons.Outlined.TaskAlt)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = estado.descripcion,
                            onValueChange = vm::cambiarDescripcion,
                            label = { Text(stringResource(R.string.solicitud_descripcion)) },
                            placeholder = { Text(stringResource(R.string.solicitud_descripcion_ejemplo)) },
                            isError = estado.descripcionCorta,
                            supportingText = {
                                Row {
                                    Text(
                                        if (estado.descripcionCorta) {
                                            stringResource(R.string.error_descripcion_corta, DetalleViewModel.DESCRIPCION_MINIMA)
                                        } else {
                                            stringResource(R.string.solicitud_descripcion_ayuda)
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text("${estado.descripcion.length}/${DetalleViewModel.DESCRIPCION_MAXIMA}")
                                }
                            },
                            minLines = 3,
                            enabled = !estado.enviando,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        CampoFormulario(
                            estado.telefono, vm::cambiarTelefono, R.string.solicitud_telefono, Icons.Outlined.Phone,
                            error = if (estado.telefonoInvalido) stringResource(R.string.error_telefono) else null,
                            ayuda = R.string.solicitud_telefono_ayuda, habilitado = !estado.enviando,
                            teclado = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        )
                        AvisoError(
                            when {
                                estado.sinBusqueda -> stringResource(R.string.error_sin_busqueda)
                                estado.errorEnvio -> stringResource(R.string.error_envio_solicitud)
                                else -> null
                            },
                        )
                        BotonPrincipal(
                            texto = stringResource(R.string.enviar_solicitud),
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    pedirPermiso.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    vm.enviar()
                                }
                            },
                            cargando = estado.enviando,
                            icono = Icons.AutoMirrored.Outlined.Send,
                        )
                    }
                }
            }
        }
    }
}

// ---------- Favoritos ----------

@Composable
fun FavoritosScreen(onAbrirPrestador: (Int) -> Unit, onIrAInicio: () -> Unit) {
    val vm = chambaViewModel { FavoritosViewModel(it.prestadorRepository) }
    val favoritos by vm.favoritos.collectAsStateWithLifecycle()
    val lista = favoritos
    when {
        lista == null -> PantallaCentrada { Cargando() }
        lista.isEmpty() -> PantallaCentrada {
            Mensaje(
                stringResource(R.string.favoritos_vacio),
                textoBoton = stringResource(R.string.ir_a_inicio),
                icono = Icons.Outlined.FavoriteBorder,
                onBoton = onIrAInicio,
            )
        }
        else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(lista, key = { it.id }) { prestador ->
                // Al quitar un favorito la tarjeta se va y las demás suben en lugar de saltar.
                PrestadorCard(
                    prestador = prestador,
                    favorito = true,
                    onAbrir = { onAbrirPrestador(prestador.id) },
                    onFavorito = { vm.quitar(prestador.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

// ---------- Solicitudes ----------

@Composable
fun SolicitudesScreen(sesion: Sesion, onIrAInicio: (() -> Unit)? = null) {
    val vm = chambaViewModel { SolicitudesViewModel(sesion, it.solicitudRepository) }
    val estado by vm.uiState.collectAsStateWithLifecycle()
    val esPrestador = sesion.rol == Rol.PRESTADOR
    when {
        esPrestador && sesion.prestadorId == null ->
            PantallaCentrada { Mensaje(stringResource(R.string.prestador_sin_vincular), icono = Icons.Outlined.VerifiedUser) }
        estado.cargando -> PantallaCentrada { Cargando() }
        estado.error -> PantallaCentrada {
            Mensaje(
                stringResource(R.string.error_solicitudes),
                textoBoton = stringResource(R.string.reintentar),
                icono = iconoError(pe.tecsup.chambape.domain.model.TipoError.SIN_CONEXION),
                onBoton = vm::reintentar,
            )
        }
        estado.solicitudes.isEmpty() -> PantallaCentrada {
            Mensaje(
                stringResource(if (esPrestador) R.string.solicitudes_vacio_prestador else R.string.solicitudes_vacio_cliente),
                // El cliente vacío tiene a dónde ir; el prestador espera solicitudes.
                textoBoton = if (esPrestador || onIrAInicio == null) null else stringResource(R.string.ir_a_inicio),
                icono = Icons.Outlined.Inbox,
                onBoton = onIrAInicio ?: {},
            )
        }
        else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "aviso") {
                AvisoError(if (estado.errorRespuesta) stringResource(R.string.error_respuesta) else null, modifier = Modifier.animateItem())
            }
            items(estado.solicitudes, key = { it.id }) { solicitud ->
                SolicitudCard(
                    solicitud = solicitud,
                    esPrestador = esPrestador,
                    ocupado = estado.respondiendoId != null,
                    trabajando = estado.respondiendoId == solicitud.id,
                    onResponder = { aceptar -> vm.responder(solicitud.id, aceptar) },
                    onAvanzar = { siguiente -> vm.avanzar(solicitud.id, siguiente) },
                    onCalificar = { estrellas, comentario -> vm.calificar(solicitud.id, estrellas, comentario) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

private val ETAPAS = listOf(
    EstadoSolicitud.ACEPTADA, EstadoSolicitud.EN_CAMINO, EstadoSolicitud.EN_SERVICIO, EstadoSolicitud.FINALIZADA,
)

@StringRes
private fun nombreEtapa(estado: EstadoSolicitud): Int = when (estado) {
    EstadoSolicitud.PENDIENTE -> R.string.estado_pendiente
    EstadoSolicitud.ACEPTADA -> R.string.estado_confirmado
    EstadoSolicitud.RECHAZADA -> R.string.estado_rechazada
    EstadoSolicitud.EN_CAMINO -> R.string.estado_en_camino
    EstadoSolicitud.EN_SERVICIO -> R.string.estado_en_servicio
    EstadoSolicitud.FINALIZADA -> R.string.estado_finalizada
}

private fun iconoEtapa(estado: EstadoSolicitud): ImageVector = when (estado) {
    EstadoSolicitud.PENDIENTE -> Icons.Outlined.HourglassEmpty
    EstadoSolicitud.ACEPTADA -> Icons.Filled.Check
    EstadoSolicitud.RECHAZADA -> Icons.Filled.Close
    EstadoSolicitud.EN_CAMINO -> Icons.Outlined.DirectionsCar
    EstadoSolicitud.EN_SERVICIO -> Icons.Outlined.Handyman
    EstadoSolicitud.FINALIZADA -> Icons.Outlined.TaskAlt
}

@StringRes
private fun accionSiguiente(estado: EstadoSolicitud): Int? = when (estado) {
    EstadoSolicitud.EN_CAMINO -> R.string.accion_en_camino
    EstadoSolicitud.EN_SERVICIO -> R.string.accion_en_servicio
    EstadoSolicitud.FINALIZADA -> R.string.accion_finalizar
    else -> null
}

/** Etiqueta de estado con icono y color propios; cambia con un fundido cuando avanza. */
@Composable
private fun EstadoPill(estado: EstadoSolicitud) {
    val (fondo, texto) = when (estado) {
        EstadoSolicitud.PENDIENTE -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        EstadoSolicitud.RECHAZADA -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        EstadoSolicitud.FINALIZADA -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }
    val colorFondo by animateColorAsState(fondo, tween(Movimiento.MEDIA), label = "fondo")
    Surface(color = colorFondo, shape = CircleShape) {
        AnimatedContent(targetState = estado, label = "estado") { e ->
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(iconoEtapa(e), contentDescription = null, tint = texto, modifier = Modifier.size(16.dp))
                Text(stringResource(nombreEtapa(e)), style = MaterialTheme.typography.labelMedium, color = texto)
            }
        }
    }
}

/** HU-11: las cuatro etapas como línea de tiempo; el tramo recorrido se llena al avanzar. */
@Composable
private fun Seguimiento(solicitud: Solicitud) {
    val actual = ETAPAS.indexOf(solicitud.estado)
    if (actual < 0) return
    val resumen = stringResource(R.string.seguimiento_resumen, stringResource(nombreEtapa(solicitud.estado)), actual + 1, ETAPAS.size)
    Row(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = resumen },
        verticalAlignment = Alignment.Top,
    ) {
        ETAPAS.forEachIndexed { i, etapa ->
            val hecha = i <= actual
            val color by animateColorAsState(
                if (hecha) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                tween(Movimiento.LARGA, delayMillis = i * 60),
                label = "etapa",
            )
            val escala by animateFloatAsState(
                if (i == actual) 1.15f else 1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "actual",
            )
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(3.dp).background(if (i == 0) Color.Transparent else color))
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .graphicsLayer { scaleX = escala; scaleY = escala }
                            .clip(CircleShape)
                            .background(color),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            iconoEtapa(etapa), contentDescription = null, modifier = Modifier.size(15.dp),
                            tint = if (hecha) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val siguienteHecha = i < actual
                    val colorTramo by animateColorAsState(
                        if (siguienteHecha) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        tween(Movimiento.LARGA), label = "tramo",
                    )
                    Box(Modifier.weight(1f).height(3.dp).background(if (i == ETAPAS.lastIndex) Color.Transparent else colorTramo))
                }
                Text(
                    stringResource(nombreEtapa(etapa)),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hecha) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun SolicitudCard(
    solicitud: Solicitud,
    esPrestador: Boolean,
    ocupado: Boolean,
    trabajando: Boolean,
    onResponder: (Boolean) -> Unit,
    onAvanzar: (EstadoSolicitud) -> Unit,
    onCalificar: (Int, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val contexto = LocalContext.current
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Iniciales(solicitud.contraparte, tamano = 40.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(solicitud.contraparte, style = MaterialTheme.typography.titleMedium)
                    if (solicitud.rubro.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(iconoRubro(solicitud.rubro), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                            Text(solicitud.rubro, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                EstadoPill(solicitud.estado)
            }
            Text(solicitud.descripcion, style = MaterialTheme.typography.bodyMedium)
            val telefono = solicitud.telefono
            if (esPrestador && !telefono.isNullOrBlank()) {
                // El prestador llama al cliente con un toque (abre el marcador, no llama solo).
                TextButton(
                    onClick = {
                        try {
                            contexto.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$telefono")))
                        } catch (_: ActivityNotFoundException) {
                            // Sin app de teléfono (tablet): el número sigue visible en el botón.
                        }
                    },
                ) {
                    Icon(Icons.Outlined.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.llamar_a, telefono), modifier = Modifier.padding(start = 8.dp))
                }
            }
            Seguimiento(solicitud)
            when {
                esPrestador && solicitud.estado == EstadoSolicitud.PENDIENTE ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { onResponder(false) }, enabled = !ocupado, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.rechazar), modifier = Modifier.padding(start = 6.dp))
                        }
                        BotonPrincipal(
                            stringResource(R.string.aceptar), { onResponder(true) },
                            modifier = Modifier.weight(1f), cargando = trabajando, habilitado = !ocupado, icono = Icons.Filled.Check,
                        )
                    }
                esPrestador -> solicitud.siguienteEstado?.let { siguiente ->
                    accionSiguiente(siguiente)?.let { texto ->
                        BotonPrincipal(
                            stringResource(texto), { onAvanzar(siguiente) },
                            cargando = trabajando, habilitado = !ocupado, icono = iconoEtapa(siguiente),
                        )
                    }
                }
                solicitud.puedeCalificar -> {
                    HorizontalDivider()
                    Calificar(ocupado, trabajando, onCalificar)
                }
                solicitud.estrellas != null -> EstrellasDadas(solicitud.estrellas)
            }
        }
    }
}

@Composable
private fun EstrellasDadas(estrellas: Int) {
    val descripcion = stringResource(R.string.tu_calificacion, estrellas)
    Row(
        modifier = Modifier.clearAndSetSemantics { contentDescription = descripcion },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(stringResource(R.string.tu_calificacion_titulo), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 6.dp))
        (1..5).forEach { n ->
            Icon(
                if (n <= estrellas) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** HU-12: de 1 a 5 estrellas (cada una rebota al elegirla) y un comentario opcional; una sola vez. */
@Composable
private fun Calificar(ocupado: Boolean, trabajando: Boolean, onCalificar: (Int, String) -> Unit) {
    var estrellas by rememberSaveable { mutableIntStateOf(0) }
    var comentario by rememberSaveable { mutableStateOf("") }
    Text(stringResource(R.string.como_fue_el_servicio), style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        (1..5).forEach { n ->
            val elegida = n <= estrellas
            val escala by animateFloatAsState(
                if (elegida) 1.15f else 1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                label = "estrella",
            )
            IconToggleButton(checked = elegida, onCheckedChange = { estrellas = n }, enabled = !ocupado) {
                Icon(
                    if (elegida) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = stringResource(R.string.estrellas_n, n),
                    tint = if (elegida) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp).graphicsLayer { scaleX = escala; scaleY = escala },
                )
            }
        }
    }
    if (estrellas > 0) {
        Text(
            stringResource(TEXTO_ESTRELLAS[estrellas - 1]),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
    OutlinedTextField(
        value = comentario,
        onValueChange = { comentario = it.take(500) },
        label = { Text(stringResource(R.string.comentario_opcional)) },
        enabled = !ocupado,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    )
    BotonPrincipal(
        stringResource(R.string.enviar_calificacion), { onCalificar(estrellas, comentario) },
        cargando = trabajando, habilitado = !ocupado && estrellas > 0, icono = Icons.Filled.Star,
    )
}

private val TEXTO_ESTRELLAS = listOf(
    R.string.estrellas_1, R.string.estrellas_2, R.string.estrellas_3, R.string.estrellas_4, R.string.estrellas_5,
)

// ---------- Perfil ----------

@Composable
fun PerfilScreen(sesion: Sesion, onCerrarSesion: () -> Unit) {
    val esPrestador = sesion.rol == Rol.PRESTADOR
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Iniciales(sesion.nombre.ifBlank { sesion.correo }, tamano = 88.dp, modifier = Modifier.padding(top = 16.dp))
        Text(sesion.nombre.ifBlank { sesion.correo }, style = MaterialTheme.typography.headlineSmall)
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = CircleShape) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    if (esPrestador) Icons.Outlined.Handyman else Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    stringResource(if (esPrestador) R.string.rol_prestador else R.string.rol_cliente),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        ) {
            ListItem(
                headlineContent = { Text(sesion.correo) },
                overlineContent = { Text(stringResource(R.string.correo)) },
                leadingContent = { Icon(Icons.Outlined.Email, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            )
        }
        if (!esPrestador) TarjetaMiDireccion()
        OutlinedButton(onClick = onCerrarSesion, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.cerrar_sesion), modifier = Modifier.padding(start = 8.dp))
        }
    }
}
