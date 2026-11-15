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

// ---------- Detalle del prestador + solicitud ----------

@Composable
fun DetalleScreen(prestadorId: Int, sesion: Sesion) {
    val vm = chambaViewModel {
        DetalleViewModel(prestadorId, sesion, it.prestadorRepository, it.solicitudRepository, it.notificador)
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
            Text(stringResource(R.string.sin_calificaciones), style = MaterialTheme.typography.bodySmall)
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
                    value = estado.direccion,
                    onValueChange = vm::cambiarDireccion,
                    label = { Text(stringResource(R.string.solicitud_direccion)) },
                    singleLine = true,
                    enabled = !estado.enviando,
                    modifier = Modifier.fillMaxWidth(),
                )
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
                    respondiendo = estado.respondiendoId != null,
                    onResponder = { aceptar -> vm.responder(solicitud.id, aceptar) },
                )
            }
        }
    }
}

@Composable
private fun SolicitudCard(solicitud: Solicitud, esPrestador: Boolean, respondiendo: Boolean, onResponder: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (esPrestador) solicitud.clienteNombre else solicitud.prestadorNombre,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(solicitud.descripcion)
            if (solicitud.direccion.isNotBlank()) {
                Text(stringResource(R.string.solicitud_en, solicitud.direccion), style = MaterialTheme.typography.bodySmall)
            }
            Text(
                stringResource(
                    when (solicitud.estado) {
                        EstadoSolicitud.PENDIENTE -> R.string.estado_pendiente
                        EstadoSolicitud.ACEPTADA -> R.string.estado_aceptada
                        EstadoSolicitud.RECHAZADA -> R.string.estado_rechazada
                    },
                ),
                style = MaterialTheme.typography.labelLarge,
                color = when (solicitud.estado) {
                    EstadoSolicitud.PENDIENTE -> MaterialTheme.colorScheme.secondary
                    EstadoSolicitud.ACEPTADA -> MaterialTheme.colorScheme.primary
                    EstadoSolicitud.RECHAZADA -> MaterialTheme.colorScheme.error
                },
            )
            if (esPrestador && solicitud.estado == EstadoSolicitud.PENDIENTE) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onResponder(true) }, enabled = !respondiendo) { Text(stringResource(R.string.aceptar)) }
                    OutlinedButton(onClick = { onResponder(false) }, enabled = !respondiendo) { Text(stringResource(R.string.rechazar)) }
                }
            }
        }
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
