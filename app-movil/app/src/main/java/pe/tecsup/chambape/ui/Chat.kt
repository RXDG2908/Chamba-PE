package pe.tecsup.chambape.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.tecsup.chambape.R
import pe.tecsup.chambape.data.repository.ChatRepository
import pe.tecsup.chambape.domain.model.Chat
import pe.tecsup.chambape.domain.model.ErrorDatos
import pe.tecsup.chambape.domain.model.MensajeChat
import pe.tecsup.chambape.domain.model.TipoError
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ---------- Estado ----------

data class ChatUiState(
    val chat: Chat? = null,
    /** Error al consultar; si ya hay mensajes se siguen mostrando y se reintenta solo. */
    val error: TipoError? = null,
    val texto: String = "",
    val enviando: Boolean = false,
    val errorEnvio: Boolean = false,
)

/**
 * Chat de una solicitud aceptada. Consulta los mensajes cada [intervaloMs] (como el seguimiento,
 * sin WebSocket) mientras la pantalla existe; un fallo no corta la consulta, se reintenta en la
 * siguiente vuelta.
 */
class ChatViewModel(
    private val solicitudId: Int,
    private val repository: ChatRepository,
    private val intervaloMs: Long = 5_000,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                consultar()
                delay(intervaloMs)
            }
        }
    }

    private suspend fun consultar() {
        try {
            val chat = repository.consultar(solicitudId)
            _uiState.update { it.copy(chat = chat, error = null) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ErrorDatos) {
            _uiState.update { it.copy(error = e.tipo) }
        }
    }

    fun cambiarTexto(v: String) = _uiState.update { it.copy(texto = v.take(MAXIMO), errorEnvio = false) }

    fun enviar() {
        val estado = _uiState.value
        val texto = estado.texto
        if (texto.isBlank() || estado.enviando || estado.chat?.activo != true) return
        _uiState.update { it.copy(enviando = true, errorEnvio = false) }
        viewModelScope.launch {
            try {
                val mensaje = repository.enviar(solicitudId, texto)
                _uiState.update { actual ->
                    val chat = actual.chat?.let { c ->
                        if (c.mensajes.any { it.id == mensaje.id }) c else c.copy(mensajes = c.mensajes + mensaje)
                    }
                    actual.copy(chat = chat, texto = "", enviando = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ErrorDatos) {
                _uiState.update { it.copy(enviando = false, errorEnvio = true) }
                // Puede que el servicio haya finalizado: se trae el estado al momento.
                consultar()
            }
        }
    }

    companion object {
        const val MAXIMO = 500
    }
}

/** Hora local del mensaje («14:05»); vacía si la fecha no se entiende. */
internal fun horaMensaje(iso: String, zona: ZoneId = ZoneId.systemDefault()): String =
    try {
        Instant.parse(iso).atZone(zona).format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (e: java.time.format.DateTimeParseException) {
        ""
    }

// ---------- Pantalla ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(solicitudId: Int, onVolver: () -> Unit) {
    val vm = chambaViewModel(key = "chat-$solicitudId") { ChatViewModel(solicitudId, it.chatRepository) }
    val estado by vm.uiState.collectAsStateWithLifecycle()
    val chat = estado.chat
    val contexto = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        TopAppBar(
            title = {
                Column {
                    Text(chat?.contraparte ?: stringResource(R.string.chat_titulo), style = MaterialTheme.typography.titleMedium)
                    if (chat != null) {
                        Text(
                            stringResource(R.string.chat_subtitulo, chat.solicitudId, chat.estadoTexto),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onVolver) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.volver))
                }
            },
            actions = {
                val telefono = chat?.telefono
                if (telefono != null) {
                    // Abre el marcador con el número (no llama solo).
                    IconButton(onClick = {
                        try {
                            contexto.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$telefono")))
                        } catch (_: ActivityNotFoundException) {
                            // Sin app de teléfono (tablet): no hay a dónde llamar.
                        }
                    }) {
                        Icon(Icons.Outlined.Call, contentDescription = stringResource(R.string.chat_llamar, chat?.contraparte.orEmpty()))
                    }
                }
            },
        )
        when {
            chat == null && estado.error != null -> PantallaCentrada {
                Mensaje(textoError(estado.error!!), icono = iconoError(estado.error!!))
            }
            chat == null -> PantallaCentrada { Cargando() }
            else -> {
                AvisoError(if (estado.error != null) textoError(estado.error!!) else null)
                Mensajes(chat.mensajes, modifier = Modifier.weight(1f))
                if (chat.activo) {
                    AvisoError(if (estado.errorEnvio) stringResource(R.string.chat_error_envio) else null)
                    Escribir(estado.texto, estado.enviando, vm::cambiarTexto, vm::enviar)
                } else {
                    Text(
                        stringResource(R.string.chat_solo_lectura),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Mensajes(mensajes: List<MensajeChat>, modifier: Modifier = Modifier) {
    if (mensajes.isEmpty()) {
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center) {
            Mensaje(stringResource(R.string.chat_vacio), icono = Icons.Outlined.ChatBubbleOutline)
        }
        return
    }
    val lista = rememberLazyListState()
    // Baja al último mensaje cuando llega uno nuevo.
    LaunchedEffect(mensajes.size) { lista.animateScrollToItem(mensajes.lastIndex) }
    LazyColumn(
        state = lista,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(mensajes, key = { it.id }) { m -> Burbuja(m) }
    }
}

@Composable
private fun Burbuja(mensaje: MensajeChat) {
    val colores = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (mensaje.mio) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (mensaje.mio) colores.primary else colores.surfaceContainerHigh,
            contentColor = if (mensaje.mio) colores.onPrimary else colores.onSurface,
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (mensaje.mio) 16.dp else 4.dp, bottomEnd = if (mensaje.mio) 4.dp else 16.dp,
            ),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(mensaje.texto, style = MaterialTheme.typography.bodyMedium)
                Text(
                    horaMensaje(mensaje.enviado),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

@Composable
private fun Escribir(texto: String, enviando: Boolean, onCambiar: (String) -> Unit, onEnviar: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = texto,
            onValueChange = onCambiar,
            placeholder = { Text(stringResource(R.string.chat_escribe)) },
            enabled = !enviando,
            maxLines = 4,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onEnviar, enabled = !enviando && texto.isNotBlank()) {
            Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = stringResource(R.string.chat_enviar), tint = MaterialTheme.colorScheme.primary)
        }
    }
}
