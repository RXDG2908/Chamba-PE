package pe.tecsup.chambape.device

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Un lugar como lo entiende el cliente: calle y distrito. Las coordenadas viajan por dentro
 * (la búsqueda y la solicitud las necesitan), pero nunca se muestran.
 */
data class Direccion(
    val titulo: String,
    val detalle: String,
    val coordenadas: Coordenadas,
    /** Distrito («Miraflores»): lo que reconoce la persona en la barra, más que la urbanización. */
    val distrito: String = "",
) {
    /** Una línea para la barra de ubicación: «Av. Arequipa 1234, Miraflores». */
    val corta: String
        get() {
            val zona = distrito.ifBlank { detalle.substringBefore(',').trim() }
            return if (zona.isBlank() || zona == titulo) titulo else "$titulo, $zona"
        }
}

/** Convierte lo que escribe la persona en lugares, y un punto del mapa en una dirección legible. */
interface BuscadorDirecciones {
    suspend fun buscar(texto: String): List<Direccion>
    suspend fun nombrar(coordenadas: Coordenadas): Direccion?
}

/**
 * Geocoder del sistema (el de Google en los celulares con Play Services), sin clave ni costo.
 * Las búsquedas se limitan al Perú para que «Larco» no devuelva una calle de otro país.
 */
class GeocoderDirecciones(context: Context) : BuscadorDirecciones {
    private val geocoder = Geocoder(context, Locale.forLanguageTag("es-PE"))

    override suspend fun buscar(texto: String): List<Direccion> {
        val consulta = texto.trim()
        if (consulta.length < MINIMO_LETRAS || !Geocoder.isPresent()) return emptyList()
        val lugares = conLimite {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocationName(
                        consulta, MAXIMO_RESULTADOS, SUR, OESTE, NORTE, ESTE,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(direcciones: MutableList<Address>) = cont.resume(direcciones)
                            override fun onError(mensaje: String?) = cont.resume(emptyList())
                        },
                    )
                }
            } else {
                @Suppress("DEPRECATION")
                withContext(Dispatchers.IO) {
                    geocoder.getFromLocationName(consulta, MAXIMO_RESULTADOS, SUR, OESTE, NORTE, ESTE).orEmpty()
                }
            }
        }
        return lugares.mapNotNull(::aDireccion).distinctBy { it.titulo + it.detalle }
    }

    override suspend fun nombrar(coordenadas: Coordenadas): Direccion? {
        if (!Geocoder.isPresent()) return null
        val lugares = conLimite {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(
                        coordenadas.latitud, coordenadas.longitud, 1,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(direcciones: MutableList<Address>) = cont.resume(direcciones)
                            override fun onError(mensaje: String?) = cont.resume(emptyList())
                        },
                    )
                }
            } else {
                @Suppress("DEPRECATION")
                withContext(Dispatchers.IO) { geocoder.getFromLocation(coordenadas.latitud, coordenadas.longitud, 1).orEmpty() }
            }
        }
        // El punto exacto es el que tocó la persona, no el centro de la calle que devuelve el Geocoder.
        return lugares.firstOrNull()?.let(::aDireccion)?.copy(coordenadas = coordenadas)
    }

    /** Sin red el Geocoder puede tardar mucho o lanzar IOException: se trata como «sin resultados». */
    private suspend fun conLimite(bloque: suspend () -> List<Address>): List<Address> =
        try {
            withTimeoutOrNull(ESPERA_MS) { bloque() }.orEmpty()
        } catch (e: java.io.IOException) {
            emptyList()
        } catch (e: IllegalArgumentException) {
            emptyList()
        }

    private companion object {
        const val MINIMO_LETRAS = 3
        const val MAXIMO_RESULTADOS = 6
        const val ESPERA_MS = 8_000L
        // Rectángulo que contiene al Perú.
        const val SUR = -18.4
        const val OESTE = -81.4
        const val NORTE = -0.03
        const val ESTE = -68.6
    }
}

/** Calle y número arriba; distrito y ciudad debajo. Sin coordenadas en ningún texto. */
internal fun aDireccion(lugar: Address): Direccion? {
    if (!lugar.hasLatitude() || !lugar.hasLongitude()) return null
    val calle = listOfNotNull(lugar.thoroughfare, lugar.subThoroughfare).joinToString(" ").trim()
    val titulo = calle.ifBlank { lugar.featureName?.takeUnless { it.any(Char::isDigit) && it.length < 6 }.orEmpty() }
        .ifBlank { lugar.subLocality ?: lugar.locality ?: return null }
    // «Urb Cercado de Miraflores, Miraflores» repite el distrito: queda solo «Miraflores».
    val urbanizacion = lugar.subLocality?.takeUnless { sub -> lugar.locality?.let { sub.contains(it, ignoreCase = true) } == true }
    val detalle = listOfNotNull(urbanizacion, lugar.locality, lugar.adminArea)
        .map(String::trim).filter { it.isNotBlank() && it != titulo }.distinct().joinToString(", ")
    val distrito = (lugar.locality ?: lugar.subLocality).orEmpty().trim()
    return Direccion(titulo, detalle, Coordenadas(lugar.latitude, lugar.longitude), distrito)
}
