package pe.tecsup.chambape.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pe.tecsup.chambape.device.Coordenadas
import pe.tecsup.chambape.device.Direccion

/**
 * La dirección del cliente («Mi dirección», en Perfil). La búsqueda empieza con ella, como la
 * dirección de entrega de una app de delivery. Vive en el celular; se borra al cerrar sesión.
 */
interface AlmacenDireccion {
    val guardada: StateFlow<Direccion?>
    fun guardar(direccion: Direccion?)
}

class AlmacenDireccionPreferencias(context: Context) : AlmacenDireccion {
    private val prefs = context.getSharedPreferences("direccion_cliente", Context.MODE_PRIVATE)
    private val estado = MutableStateFlow(leer())
    override val guardada: StateFlow<Direccion?> = estado.asStateFlow()

    private fun leer(): Direccion? {
        val titulo = prefs.getString("titulo", null) ?: return null
        if (!prefs.contains("latitud") || !prefs.contains("longitud")) return null
        return Direccion(
            titulo = titulo,
            detalle = prefs.getString("detalle", "").orEmpty(),
            coordenadas = Coordenadas(
                Double.fromBits(prefs.getLong("latitud", 0)),
                Double.fromBits(prefs.getLong("longitud", 0)),
            ),
        )
    }

    override fun guardar(direccion: Direccion?) {
        prefs.edit().apply {
            if (direccion == null) {
                clear()
            } else {
                putString("titulo", direccion.titulo)
                putString("detalle", direccion.detalle)
                putLong("latitud", direccion.coordenadas.latitud.toRawBits())
                putLong("longitud", direccion.coordenadas.longitud.toRawBits())
            }
        }.apply()
        estado.value = direccion
    }
}

/** Para pruebas y vistas previas. */
class AlmacenDireccionEnMemoria(inicial: Direccion? = null) : AlmacenDireccion {
    private val estado = MutableStateFlow(inicial)
    override val guardada: StateFlow<Direccion?> = estado.asStateFlow()
    override fun guardar(direccion: Direccion?) {
        estado.value = direccion
    }
}
