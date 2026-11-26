package pe.tecsup.chambape.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.tecsup.chambape.R
import pe.tecsup.chambape.domain.model.EstadoSolicitud
import pe.tecsup.chambape.domain.model.Rol
import pe.tecsup.chambape.domain.model.Sesion
import pe.tecsup.chambape.domain.model.Solicitud
import pe.tecsup.chambape.ui.busqueda.textoCalificacion
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.annotation.StringRes

// ---------- Detalle del prestador + solicitud ----------

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
        prestador == null -> PantallaCentrada { Mensaje(stringResource(R.string.detalle_no_encontrado)) }
        else -> Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(prestador.nombreCompleto, style = MaterialTheme.typography.headlineSmall)
            if (prestador.rubros.isNotEmpty()) Text(prestador.rubros.joinToString(" · "))
            prestador.distanciaKm?.let { Text(stringResource(R.string.distancia_km, it)) }
            Text(
                stringResource(R.string.detalle_zona, prestador.latitud, prestador.longitud),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(textoCalificacion(prestador), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = vm::alternarFavorito) {
                Text(stringResource(if (prestador.favorito) R.string.quitar_de_favoritos else R.string.guardar_en_favoritos))
            }

            Text(stringResource(R.string.solicitud_titulo), style = MaterialTheme.typography.titleMedium)
            if (estado.enviada) {
                Mensaje(stringResource(R.string.solicitud_enviada, prestador.nombreCompleto))
            } else {
                OutlinedTextField(
                    value = estado.descripcion,
                    onValueChange = vm::cambiarDescripcion,
                    label = { Text(stringResource(R.string.solicitud_descripcion)) },
                    isError = estado.descripcionCorta,
                    supportingText = {
                        Text(
                            if (estado.descripcionCorta) {
                                stringResource(R.string.error_descripcion_corta, DetalleViewModel.DESCRIPCION_MINIMA)
                            } else {
                                "${estado.descripcion.length}/${DetalleViewModel.DESCRIPCION_MAXIMA}"
                            },
                        )
                    },
                    minLines = 3,
                    enabled = !estado.enviando,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = estado.telefono,
                    onValueChange = vm::cambiarTelefono,
                    label = { Text(stringResource(R.string.solicitud_telefono)) },
                    isError = estado.telefonoInvalido,
                    supportingText = { if (estado.telefonoInvalido) Text(stringResource(R.string.error_telefono)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    enabled = !estado.enviando,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (estado.sinBusqueda) {
                    Text(stringResource(R.string.error_sin_busqueda), color = MaterialTheme.colorScheme.error)
                }
                if (estado.errorEnvio) {
                    Text(stringResource(R.string.error_envio_solicitud), color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            pedirPermiso.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            vm.enviar()
                        }
                    },
                    enabled = !estado.enviando,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(if (estado.enviando) R.string.enviando else R.string.enviar_solicitud)) }
            }
        }
    }
}

// ---------- Favoritos ----------

@Composable
fun FavoritosScreen(onAbrirPrestador: (Int) -> Unit) {
    val vm = chambaViewModel { FavoritosViewModel(it.prestadorRepository) }
    val favoritos by vm.favoritos.collectAsStateWithLifecycle()
    val lista = favoritos
    when {
        lista == null -> PantallaCentrada { Cargando() }
        lista.isEmpty() -> PantallaCentrada { Mensaje(stringResource(R.string.favoritos_vacio)) }
        else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(lista, key = { it.id }) { prestador ->
                PrestadorCard(
                    prestador = prestador,
                    favorito = true,
                    onAbrir = { onAbrirPrestador(prestador.id) },
                    onFavorito = { vm.quitar(prestador.id) },
                )
            }
        }
    }
}

// ---------- Solicitudes ----------

@Composable
fun SolicitudesScreen(sesion: Sesion) {
    val vm = chambaViewModel { SolicitudesViewModel(sesion, it.solicitudRepository) }
    val estado by vm.uiState.collectAsStateWithLifecycle()
    val esPrestador = sesion.rol == Rol.PRESTADOR
    when {
        esPrestador && sesion.prestadorId == null ->
            PantallaCentrada { Mensaje(stringResource(R.string.prestador_sin_vincular)) }
        estado.cargando -> PantallaCentrada { Cargando() }
        estado.error -> PantallaCentrada {
            Mensaje(stringResource(R.string.error_solicitudes), textoBoton = stringResource(R.string.reintentar), onBoton = vm::reintentar)
        }
        estado.solicitudes.isEmpty() -> PantallaCentrada {
            Mensaje(stringResource(if (esPrestador) R.string.solicitudes_vacio_prestador else R.string.solicitudes_vacio_cliente))
        }
        else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (estado.errorRespuesta) {
                item { Text(stringResource(R.string.error_respuesta), color = MaterialTheme.colorScheme.error) }
            }
            items(estado.solicitudes, key = { it.id }) { solicitud ->
                SolicitudCard(
                    solicitud = solicitud,
                    esPrestador = esPrestador,
                    ocupado = estado.respondiendoId != null,
                    onResponder = { aceptar -> vm.responder(solicitud.id, aceptar) },
                    onAvanzar = { siguiente -> vm.avanzar(solicitud.id, siguiente) },
                    onCalificar = { estrellas, comentario -> vm.calificar(solicitud.id, estrellas, comentario) },
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

@StringRes
private fun accionSiguiente(estado: EstadoSolicitud): Int? = when (estado) {
    EstadoSolicitud.EN_CAMINO -> R.string.accion_en_camino
    EstadoSolicitud.EN_SERVICIO -> R.string.accion_en_servicio
    EstadoSolicitud.FINALIZADA -> R.string.accion_finalizar
    else -> null
}

/** HU-11: las cuatro etapas, marcando las hechas y la actual (igual que la web). */
@Composable
private fun Seguimiento(solicitud: Solicitud) {
    val actual = ETAPAS.indexOf(solicitud.estado)
    if (actual < 0) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        ETAPAS.forEachIndexed { i, etapa ->
            val hecha = i <= actual
            Text(
                (if (hecha) "✓ " else "") + stringResource(nombreEtapa(etapa)),
                style = MaterialTheme.typography.labelSmall,
                color = if (hecha) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SolicitudCard(
    solicitud: Solicitud,
    esPrestador: Boolean,
    ocupado: Boolean,
    onResponder: (Boolean) -> Unit,
    onAvanzar: (EstadoSolicitud) -> Unit,
    onCalificar: (Int, String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(solicitud.contraparte, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    stringResource(nombreEtapa(solicitud.estado)),
                    style = MaterialTheme.typography.labelLarge,
                    color = when (solicitud.estado) {
                        EstadoSolicitud.PENDIENTE -> MaterialTheme.colorScheme.secondary
                        EstadoSolicitud.RECHAZADA -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    },
                )
            }
            if (solicitud.rubro.isNotBlank()) Text(solicitud.rubro, style = MaterialTheme.typography.bodySmall)
            Text(solicitud.descripcion)
            if (esPrestador && !solicitud.telefono.isNullOrBlank()) {
                Text(stringResource(R.string.solicitud_contacto, solicitud.telefono), style = MaterialTheme.typography.bodyMedium)
            }
            Seguimiento(solicitud)
            when {
                esPrestador && solicitud.estado == EstadoSolicitud.PENDIENTE ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { onResponder(true) }, enabled = !ocupado) { Text(stringResource(R.string.aceptar)) }
                        OutlinedButton(onClick = { onResponder(false) }, enabled = !ocupado) { Text(stringResource(R.string.rechazar)) }
                    }
                esPrestador -> solicitud.siguienteEstado?.let { siguiente ->
                    accionSiguiente(siguiente)?.let { texto ->
                        Button(onClick = { onAvanzar(siguiente) }, enabled = !ocupado, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(texto))
                        }
                    }
                }
                solicitud.puedeCalificar -> Calificar(ocupado, onCalificar)
                solicitud.estrellas != null ->
                    Text(stringResource(R.string.tu_calificacion, "★".repeat(solicitud.estrellas)), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** HU-12: de 1 a 5 estrellas y un comentario opcional; una sola vez. */
@Composable
private fun Calificar(ocupado: Boolean, onCalificar: (Int, String) -> Unit) {
    var estrellas by rememberSaveable { mutableIntStateOf(0) }
    var comentario by rememberSaveable { mutableStateOf("") }
    Text(stringResource(R.string.como_fue_el_servicio), style = MaterialTheme.typography.titleSmall)
    Row {
        (1..5).forEach { n ->
            TextButton(onClick = { estrellas = n }, enabled = !ocupado) {
                Text(if (n <= estrellas) "★" else "☆", style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
    OutlinedTextField(
        value = comentario,
        onValueChange = { comentario = it.take(500) },
        label = { Text(stringResource(R.string.comentario_opcional)) },
        enabled = !ocupado,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = { onCalificar(estrellas, comentario) }, enabled = !ocupado && estrellas > 0, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.enviar_calificacion))
    }
}

// ---------- Perfil ----------

@Composable
fun PerfilScreen(sesion: Sesion, onCerrarSesion: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(sesion.nombre, style = MaterialTheme.typography.headlineSmall)
        Text(sesion.correo)
        Text(stringResource(if (sesion.rol == Rol.PRESTADOR) R.string.rol_prestador else R.string.rol_cliente))
        OutlinedButton(onClick = onCerrarSesion) { Text(stringResource(R.string.cerrar_sesion)) }
    }
}
