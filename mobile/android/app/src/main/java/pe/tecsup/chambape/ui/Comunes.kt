package pe.tecsup.chambape.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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

// Tema provisional; el sistema visual definitivo corresponde a AND-02.
private val ColoresClaros = lightColorScheme(
    primary = Color(0xFF0B6E4F),
    secondary = Color(0xFF8A5A00),
    tertiary = Color(0xFF1F5F8B),
)
private val ColoresOscuros = darkColorScheme(
    primary = Color(0xFF7FD8B5),
    secondary = Color(0xFFF2BE6B),
    tertiary = Color(0xFF9CCBF0),
)

@Composable
fun ChambaPeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) ColoresOscuros else ColoresClaros, content = content)
}

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
    },
)

@Composable
fun Cargando(modifier: Modifier = Modifier) {
    val descripcion = stringResource(R.string.cargando)
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator()
        Text(descripcion, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

/** Estado vacío o de error, con un botón opcional (por ejemplo, Reintentar). */
@Composable
fun Mensaje(
    texto: String,
    modifier: Modifier = Modifier,
    textoBoton: String? = null,
    onBoton: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            texto,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (textoBoton != null) Button(onClick = onBoton) { Text(textoBoton) }
    }
}

@Composable
fun AvisoSinConexion(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.aviso_cache),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.secondary,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
fun PrestadorCard(
    prestador: Prestador,
    favorito: Boolean,
    onAbrir: () -> Unit,
    onFavorito: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(onClick = onAbrir, modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(prestador.nombreCompleto, style = MaterialTheme.typography.titleMedium)
                if (prestador.rubros.isNotEmpty()) {
                    Text(prestador.rubros.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                }
                prestador.distanciaKm?.let {
                    Text(stringResource(R.string.distancia_km, it), style = MaterialTheme.typography.bodySmall)
                }
            }
            IconButton(onClick = onFavorito) {
                Icon(
                    imageVector = if (favorito) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = stringResource(
                        if (favorito) R.string.quitar_favorito else R.string.agregar_favorito,
                        prestador.nombreCompleto,
                    ),
                    tint = if (favorito) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
