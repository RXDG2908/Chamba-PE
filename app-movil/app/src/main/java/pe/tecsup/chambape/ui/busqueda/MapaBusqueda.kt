package pe.tecsup.chambape.ui.busqueda

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import pe.tecsup.chambape.R
import pe.tecsup.chambape.device.Coordenadas
import pe.tecsup.chambape.domain.model.Prestador
import kotlin.math.cos

private val LIMA = LatLng(-12.046374, -77.042793)
private const val ZOOM_INICIAL = 13f
private const val ZOOM_MAXIMO_ENCUADRE = 15f
private const val KM_POR_GRADO = 111.195
private const val COLOR_UBICACION = 0xFF2463A6.toInt()
private const val COLOR_PRESTADOR = 0xFF0B6E4F.toInt()

/**
 * Mapa de Google Maps (maps-compose), equivalente al de la web: el punto azul es dónde se
 * necesita el servicio (con [onElegirUbicacion], un toque lo mueve) y los prestadores encontrados
 * aparecen como marcadores verdes numerados; al tocarlos se ve su ficha con la calificación.
 *
 * [encuadre] cambia cada vez que hay que volver a encuadrar ubicación y resultados. Con
 * [radioKm] dibuja el círculo de cobertura alrededor de la ubicación (registro de prestador).
 */
@Composable
fun MapaBusqueda(
    ubicacion: Coordenadas?,
    prestadores: List<Prestador>,
    encuadre: Int,
    /** null: el mapa solo muestra (la ubicación del cliente se elige con la barra de arriba). */
    onElegirUbicacion: ((Double, Double) -> Unit)?,
    modifier: Modifier = Modifier,
    radioKm: Int? = null,
    alto: androidx.compose.ui.unit.Dp = 280.dp,
) {
    val alElegir by rememberUpdatedState(onElegirUbicacion)
    val descripcion = stringResource(R.string.mapa_descripcion)
    val textoUbicacion = stringResource(R.string.mapa_tu_ubicacion)
    val camara = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(LIMA, ZOOM_INICIAL) }
    // Encuadrar antes de que el mapa tenga tamaño falla: se espera a que termine de cargar.
    var cargado by remember { mutableStateOf(false) }

    LaunchedEffect(cargado, encuadre) {
        if (cargado && radioKm == null) encuadrar(camara, ubicacion, prestadores)
    }
    // Registro: al mover el centro o cambiar el radio, se ve toda la zona de cobertura.
    LaunchedEffect(cargado, ubicacion, radioKm) {
        if (cargado && ubicacion != null && radioKm != null) {
            camara.animate(CameraUpdateFactory.newLatLngBounds(zona(ubicacion, radioKm), 48))
        }
    }

    GoogleMap(
        modifier = modifier
            .fillMaxWidth()
            .height(alto)
            .clip(RoundedCornerShape(12.dp))
            .semantics { contentDescription = descripcion },
        cameraPositionState = camara,
        properties = MapProperties(isBuildingEnabled = true),
        uiSettings = MapUiSettings(zoomControlsEnabled = true, mapToolbarEnabled = false, myLocationButtonEnabled = false),
        onMapLoaded = { cargado = true },
        onMapClick = { punto -> alElegir?.invoke(punto.latitude, punto.longitude) },
    ) {
        if (ubicacion != null && radioKm != null) {
            Circle(
                center = LatLng(ubicacion.latitud, ubicacion.longitud),
                radius = radioKm * 1000.0,
                fillColor = Color(0x332463A6),
                strokeColor = Color(COLOR_UBICACION),
                strokeWidth = 4f,
            )
        }
        ubicacion?.let {
            Marker(
                state = rememberMarkerState(key = "ubicacion-${it.latitud},${it.longitud}", position = LatLng(it.latitud, it.longitud)),
                title = textoUbicacion,
                icon = icono(COLOR_UBICACION, texto = null),
                anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
            )
        }
        prestadores.forEachIndexed { indice, prestador ->
            Marker(
                state = rememberMarkerState(key = "prestador-${prestador.id}", position = LatLng(prestador.latitud, prestador.longitud)),
                title = "${indice + 1}. ${prestador.nombreCompleto}",
                snippet = fichaPrestador(prestador),
                icon = icono(COLOR_PRESTADOR, texto = "${indice + 1}"),
                anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
            )
        }
    }
}

/** Rubros, distancia y calificación del prestador, en una línea (la ficha del marcador). */
@Composable
private fun fichaPrestador(prestador: Prestador): String = buildList {
    if (prestador.rubros.isNotEmpty()) add(prestador.rubros.joinToString(" · "))
    prestador.distanciaKm?.let { add(stringResource(R.string.distancia_km, it)) }
    add(textoCalificacion(prestador))
}.joinToString(" · ")

/** «★ 4,8 · 23 calificaciones» o «Sin calificaciones» (HU-12), igual que en la web. */
@Composable
fun textoCalificacion(prestador: Prestador): String =
    if (prestador.tieneCalificaciones) {
        pluralStringResource(
            R.plurals.calificacion_resumen, prestador.calificacionesTotal,
            prestador.calificacionPromedio ?: 0.0, prestador.calificacionesTotal,
        )
    } else {
        stringResource(R.string.sin_calificaciones)
    }

/** Mueve la cámara para que se vean la ubicación elegida y todos los resultados. */
private suspend fun encuadrar(
    camara: com.google.maps.android.compose.CameraPositionState,
    ubicacion: Coordenadas?,
    prestadores: List<Prestador>,
) {
    val puntos = buildList {
        ubicacion?.let { add(LatLng(it.latitud, it.longitud)) }
        prestadores.forEach { add(LatLng(it.latitud, it.longitud)) }
    }
    if (puntos.isEmpty()) return
    if (puntos.all { it == puntos[0] }) {
        camara.animate(CameraUpdateFactory.newLatLngZoom(puntos[0], ZOOM_MAXIMO_ENCUADRE))
        return
    }
    val limites = LatLngBounds.builder().apply { puntos.forEach(::include) }.build()
    camara.animate(CameraUpdateFactory.newLatLngBounds(limites, 64))
    if (camara.position.zoom > ZOOM_MAXIMO_ENCUADRE) camara.animate(CameraUpdateFactory.zoomTo(ZOOM_MAXIMO_ENCUADRE))
}

/** Rectángulo que contiene el círculo de cobertura. */
private fun zona(centro: Coordenadas, radioKm: Int): LatLngBounds {
    val dLat = radioKm / KM_POR_GRADO
    val dLon = radioKm / (KM_POR_GRADO * cos(Math.toRadians(centro.latitud)).coerceAtLeast(0.01))
    return LatLngBounds(
        LatLng(centro.latitud - dLat, centro.longitud - dLon),
        LatLng(centro.latitud + dLat, centro.longitud + dLon),
    )
}

/** Círculo de color con borde blanco y, opcionalmente, un número dentro. */
@Composable
private fun icono(color: Int, texto: String?): BitmapDescriptor {
    val densidad = LocalContext.current.resources.displayMetrics.density
    return remember(color, texto, densidad) {
        val lado = ((if (texto == null) 22 else 34) * densidad).toInt()
        val bitmap = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888)
        val lienzo = Canvas(bitmap)
        val pincel = Paint(Paint.ANTI_ALIAS_FLAG)
        val centro = lado / 2f
        pincel.color = 0xFFFFFFFF.toInt()
        lienzo.drawCircle(centro, centro, centro, pincel)
        pincel.color = color
        lienzo.drawCircle(centro, centro, centro - 3 * densidad, pincel)
        if (texto != null) {
            pincel.color = 0xFFFFFFFF.toInt()
            pincel.textAlign = Paint.Align.CENTER
            pincel.textSize = 15 * densidad
            pincel.isFakeBoldText = true
            lienzo.drawText(texto, centro, centro - (pincel.descent() + pincel.ascent()) / 2, pincel)
        }
        BitmapDescriptorFactory.fromBitmap(bitmap)
    }
}
