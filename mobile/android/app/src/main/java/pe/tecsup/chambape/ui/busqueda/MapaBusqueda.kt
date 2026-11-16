package pe.tecsup.chambape.ui.busqueda

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import pe.tecsup.chambape.R
import pe.tecsup.chambape.device.Coordenadas
import pe.tecsup.chambape.domain.model.Prestador
import java.io.File

private val LIMA = GeoPoint(-12.046374, -77.042793)
private const val ZOOM_INICIAL = 13.0
private const val ZOOM_MAXIMO_ENCUADRE = 15.0
private const val COLOR_UBICACION = 0xFF2463A6.toInt()
private const val COLOR_PRESTADOR = 0xFF0B6E4F.toInt()

/**
 * Mapa interactivo de OpenStreetMap (osmdroid), equivalente al de Leaflet en la web:
 * un toque elige dónde se necesita el servicio (punto azul) y los prestadores encontrados
 * aparecen como marcadores verdes numerados; al tocarlos se abre su ficha.
 *
 * [encuadre] cambia cada vez que hay que volver a encuadrar ubicación y resultados.
 */
@Composable
fun MapaBusqueda(
    ubicacion: Coordenadas?,
    prestadores: List<Prestador>,
    encuadre: Int,
    onElegirUbicacion: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
    /** Si se indica, dibuja el círculo de cobertura alrededor de la ubicación (registro de prestador). */
    radioKm: Int? = null,
) {
    val context = LocalContext.current
    val alElegir by rememberUpdatedState(onElegirUbicacion)
    val descripcion = stringResource(R.string.mapa_descripcion)
    val textoUbicacion = stringResource(R.string.mapa_tu_ubicacion)
    val sinCalificaciones = stringResource(R.string.sin_calificaciones)

    val mapa = remember { crearMapa(context) { lat, lon -> alElegir(lat, lon) } }

    // osmdroid necesita onResume/onPause para arrancar y detener la descarga de teselas.
    val ciclo = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(ciclo, mapa) {
        val observador = LifecycleEventObserver { _, evento ->
            when (evento) {
                Lifecycle.Event.ON_RESUME -> mapa.onResume()
                Lifecycle.Event.ON_PAUSE -> mapa.onPause()
                else -> Unit
            }
        }
        ciclo.addObserver(observador)
        onDispose {
            ciclo.removeObserver(observador)
            mapa.onDetach()
        }
    }

    AndroidView(
        factory = { mapa },
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(12.dp))
            .semantics { contentDescription = descripcion },
        update = { vista ->
            val estado = EstadoMapa(ubicacion, prestadores, encuadre, radioKm)
            if (vista.tag == estado) return@AndroidView
            val encuadrar = (vista.tag as? EstadoMapa)?.encuadre != encuadre
            vista.tag = estado

            // Se conservan el receptor de toques y el aviso de derechos; se rehacen los marcadores.
            vista.overlays.removeAll { it is Marker || it is Polygon }
            if (ubicacion != null && radioKm != null) {
                val circulo = Polygon(vista).apply {
                    points = Polygon.pointsAsCircle(GeoPoint(ubicacion.latitud, ubicacion.longitud), radioKm * 1000.0)
                    fillPaint.color = 0x332463A6
                    outlinePaint.color = COLOR_UBICACION
                    outlinePaint.strokeWidth = 2 * vista.resources.displayMetrics.density
                    // No consume el toque: dentro del círculo se puede seguir eligiendo otro centro.
                    setOnClickListener { _, _, _ -> false }
                }
                vista.overlays += circulo
                // Al mover el centro o cambiar el radio, el mapa se ajusta para mostrar toda la zona.
                val zona = circulo.bounds
                val margen = (16 * vista.resources.displayMetrics.density).toInt()
                vista.post { vista.zoomToBoundingBox(zona, false, margen) }
            }
            ubicacion?.let {
                vista.overlays += Marker(vista).apply {
                    position = GeoPoint(it.latitud, it.longitud)
                    icon = icono(vista.context, COLOR_UBICACION, texto = null)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = textoUbicacion
                }
            }
            prestadores.forEachIndexed { indice, prestador ->
                vista.overlays += Marker(vista).apply {
                    position = GeoPoint(prestador.latitud, prestador.longitud)
                    icon = icono(vista.context, COLOR_PRESTADOR, texto = "${indice + 1}")
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = prestador.nombreCompleto
                    snippet = fichaPrestador(vista.context, prestador, sinCalificaciones)
                }
            }
            if (encuadrar) encuadrar(vista, ubicacion, prestadores)
            vista.invalidate()
        },
    )
}

private data class EstadoMapa(
    val ubicacion: Coordenadas?,
    val prestadores: List<Prestador>,
    val encuadre: Int,
    val radioKm: Int?,
)

@SuppressLint("ClickableViewAccessibility") // el toque sigue llegando al mapa; solo se evita que la lista lo robe
private fun crearMapa(context: Context, onToque: (Double, Double) -> Unit): MapView {
    // Política de uso de OpenStreetMap: identificar la app y guardar las teselas en caché.
    Configuration.getInstance().apply {
        userAgentValue = context.packageName
        osmdroidBasePath = File(context.cacheDir, "osmdroid")
        osmdroidTileCache = File(osmdroidBasePath, "teselas")
    }
    return MapView(context).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
        isHorizontalMapRepetitionEnabled = false
        isVerticalMapRepetitionEnabled = false
        minZoomLevel = 3.0
        controller.setZoom(ZOOM_INICIAL)
        controller.setCenter(LIMA)
        overlays += CopyrightOverlay(context)
        overlays += MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(punto: GeoPoint): Boolean {
                onToque(punto.latitude, punto.longitude)
                return true
            }

            override fun longPressHelper(punto: GeoPoint) = false
        })
        // El mapa va dentro de una lista que se desplaza: mientras se toca, el gesto es del mapa.
        setOnTouchListener { vista, _ ->
            vista.parent?.requestDisallowInterceptTouchEvent(true)
            false
        }
    }
}

/** Centra el mapa para que se vean la ubicación elegida y todos los resultados. */
private fun encuadrar(mapa: MapView, ubicacion: Coordenadas?, prestadores: List<Prestador>) {
    val puntos = buildList {
        ubicacion?.let { add(GeoPoint(it.latitud, it.longitud)) }
        prestadores.forEach { add(GeoPoint(it.latitud, it.longitud)) }
    }
    if (puntos.isEmpty()) return
    // post: el mapa necesita tener tamaño antes de calcular el encuadre.
    mapa.post {
        val todosIguales = puntos.all { it.latitude == puntos[0].latitude && it.longitude == puntos[0].longitude }
        if (todosIguales) {
            mapa.controller.setZoom(ZOOM_MAXIMO_ENCUADRE)
            mapa.controller.setCenter(puntos[0])
        } else {
            val margen = (32 * mapa.resources.displayMetrics.density).toInt()
            mapa.zoomToBoundingBox(BoundingBox.fromGeoPointsSafe(puntos), false, margen, ZOOM_MAXIMO_ENCUADRE, null)
        }
    }
}

private fun fichaPrestador(context: Context, prestador: Prestador, sinCalificaciones: String): String =
    buildList {
        if (prestador.rubros.isNotEmpty()) add(prestador.rubros.joinToString(" · "))
        prestador.distanciaKm?.let { add(context.getString(R.string.distancia_km, it)) }
        add(sinCalificaciones)
    }.joinToString("<br>")

/** Círculo de color con borde blanco y, opcionalmente, un número dentro. */
private fun icono(context: Context, color: Int, texto: String?): Drawable {
    val densidad = context.resources.displayMetrics.density
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
    return BitmapDrawable(context.resources, bitmap)
}
