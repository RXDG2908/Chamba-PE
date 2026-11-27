package pe.tecsup.chambape.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material.icons.outlined.CarRepair
import androidx.compose.material.icons.outlined.Carpenter
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ElectricalServices
import androidx.compose.material.icons.outlined.FormatPaint
import androidx.compose.material.icons.outlined.Foundation
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PestControl
import androidx.compose.material.icons.outlined.Plumbing
import androidx.compose.material.icons.outlined.Roofing
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Window
import androidx.compose.material.icons.outlined.Yard
import androidx.compose.ui.graphics.vector.ImageVector
import pe.tecsup.chambape.R
import pe.tecsup.chambape.domain.model.Rubro
import java.text.Normalizer

/**
 * Un ícono por rubro (sistema de íconos: Material Symbols outlined, 24 dp, un solo trazo).
 * Se busca por el nombre sin tildes ni mayúsculas, así «Gasfitería» y «gasfiteria» coinciden;
 * un rubro nuevo que la app aún no conoce recibe la herramienta genérica.
 */
fun iconoRubro(nombre: String): ImageVector = when (clave(nombre)) {
    "gasfiteria", "plomeria" -> Icons.Outlined.Plumbing
    "electricidad" -> Icons.Outlined.ElectricalServices
    "carpinteria" -> Icons.Outlined.Carpenter
    "pintura" -> Icons.Outlined.FormatPaint
    "albanileria", "construccion" -> Icons.Outlined.Foundation
    "cerrajeria" -> Icons.Outlined.Key
    "limpieza" -> Icons.Outlined.CleaningServices
    "jardineria" -> Icons.Outlined.Yard
    "soldadura" -> Icons.Outlined.LocalFireDepartment
    "mecanica" -> Icons.Outlined.CarRepair
    "camaras de seguridad" -> Icons.Outlined.Videocam
    "aire acondicionado" -> Icons.Outlined.AcUnit
    "fumigacion" -> Icons.Outlined.PestControl
    "redes e internet" -> Icons.Outlined.Router
    "mudanzas" -> Icons.Outlined.LocalShipping
    "vidrieria" -> Icons.Outlined.Window
    "electrodomesticos" -> Icons.Outlined.Kitchen
    "techos y drywall" -> Icons.Outlined.Roofing
    else -> Icons.Outlined.Handyman
}

internal fun clave(nombre: String): String =
    Normalizer.normalize(nombre.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

/**
 * Secciones de la pantalla principal, pensadas desde lo que la persona necesita hacer
 * (information-architecture): una urgencia, cuidar la casa, mantener un edificio o atender un
 * negocio. Un rubro puede estar en varias (Electricidad es urgencia, hogar y empresa).
 * Cada sección tiene su color (law-of-similarity): el mismo rubro se reconoce por su ícono y la
 * sección por su color.
 */
enum class Seccion(
    @StringRes val titulo: Int,
    @StringRes val descripcion: Int,
    val icono: ImageVector,
    val claves: List<String>,
) {
    URGENTE(
        R.string.seccion_urgente, R.string.seccion_urgente_desc, Icons.Outlined.NotificationsActive,
        listOf("gasfiteria", "cerrajeria", "electricidad", "vidrieria"),
    ),
    HOGAR(
        R.string.seccion_hogar, R.string.seccion_hogar_desc, Icons.Outlined.Home,
        listOf("limpieza", "pintura", "carpinteria", "jardineria", "electrodomesticos", "fumigacion", "mudanzas", "gasfiteria", "electricidad"),
    ),
    RESIDENCIAL(
        R.string.seccion_residencial, R.string.seccion_residencial_desc, Icons.Outlined.Apartment,
        listOf("albanileria", "pintura", "techos y drywall", "jardineria", "fumigacion", "camaras de seguridad", "limpieza"),
    ),
    EMPRESAS(
        R.string.seccion_empresas, R.string.seccion_empresas_desc, Icons.Outlined.Work,
        listOf("electricidad", "aire acondicionado", "camaras de seguridad", "redes e internet", "limpieza", "soldadura", "mecanica"),
    ),
    OTROS(R.string.seccion_otros, R.string.seccion_otros_desc, Icons.Outlined.Apps, emptyList());

    /** Los rubros de la base que van en esta sección, en el orden de la sección. */
    fun rubrosDe(todos: List<Rubro>): List<Rubro> {
        if (this == OTROS) {
            val conocidos = entries.flatMap { it.claves }.toSet()
            return todos.filter { clave(it.nombre) !in conocidos }
        }
        val porClave = todos.associateBy { clave(it.nombre) }
        return claves.mapNotNull { porClave[it] }
    }
}

/** La primera sección que contiene al rubro (para abrirlo desde la búsqueda de Inicio). */
fun seccionDe(rubro: Rubro): Seccion =
    Seccion.entries.firstOrNull { it != Seccion.OTROS && clave(rubro.nombre) in it.claves } ?: Seccion.OTROS

/** «Lo más pedido» en Inicio: atajos a los rubros que más se buscan, en este orden. */
val RUBROS_POPULARES = listOf("gasfiteria", "electricidad", "limpieza", "cerrajeria", "pintura", "carpinteria", "camaras de seguridad")

fun populares(todos: List<Rubro>): List<Rubro> {
    val porClave = todos.associateBy { clave(it.nombre) }
    return RUBROS_POPULARES.mapNotNull { porClave[it] }
}

/**
 * Colores de cada sección (law-of-similarity): el mismo par en su tarjeta de Inicio, su cabecera
 * y sus rubros, así la sección se reconoce de un vistazo. Son roles del tema: el modo oscuro los
 * resuelve solo.
 */
@androidx.compose.runtime.Composable
fun coloresSeccion(seccion: Seccion): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> {
    val c = androidx.compose.material3.MaterialTheme.colorScheme
    return when (seccion) {
        Seccion.URGENTE -> c.errorContainer to c.onErrorContainer
        Seccion.HOGAR -> c.secondaryContainer to c.onSecondaryContainer
        Seccion.RESIDENCIAL -> c.tertiaryContainer to c.onTertiaryContainer
        Seccion.EMPRESAS -> c.primaryContainer to c.onPrimaryContainer
        Seccion.OTROS -> c.surfaceContainerHighest to c.onSurfaceVariant
    }
}

/**
 * Palabras con que la gente pide cada rubro (search-ux: se busca con el vocabulario de la persona,
 * no con el nombre del catálogo). «gasfitero», «plomero» o «agua» llevan a Gasfitería.
 */
private val SINONIMOS = mapOf(
    "gasfiteria" to listOf("gasfitero", "plomero", "plomeria", "agua", "cano", "fuga", "desague", "inodoro", "terma"),
    "electricidad" to listOf("electricista", "luz", "corto", "enchufe", "tomacorriente", "cable"),
    "carpinteria" to listOf("carpintero", "madera", "mueble", "puerta", "closet"),
    "pintura" to listOf("pintor", "pintar", "pared"),
    "albanileria" to listOf("albanil", "construccion", "obra", "pared", "piso", "mayolica"),
    "cerrajeria" to listOf("cerrajero", "llave", "chapa", "cerradura"),
    "limpieza" to listOf("limpiar", "aseo", "limpiador"),
    "jardineria" to listOf("jardinero", "jardin", "pasto", "plantas", "poda"),
    "soldadura" to listOf("soldador", "soldar", "reja", "metal"),
    "mecanica" to listOf("mecanico", "auto", "carro", "moto"),
    "camaras de seguridad" to listOf("camara", "camaras", "seguridad", "cctv", "vigilancia"),
    "aire acondicionado" to listOf("aire", "aire acondicionado", "clima", "frio"),
    "fumigacion" to listOf("fumigar", "plaga", "cucaracha", "ratas", "termitas"),
    "redes e internet" to listOf("internet", "wifi", "red", "router", "redes"),
    "mudanzas" to listOf("mudanza", "flete", "mudar"),
    "vidrieria" to listOf("vidrio", "vidriero", "ventana", "espejo"),
    "electrodomesticos" to listOf("refrigeradora", "lavadora", "cocina", "microondas", "electrodomestico"),
    "techos y drywall" to listOf("techo", "drywall", "tabique", "goteras"),
)

/** ¿El rubro coincide con lo que la persona escribió? Sin tildes, por nombre o por sinónimo. */
fun coincide(rubro: Rubro, texto: String): Boolean {
    val buscado = clave(texto)
    if (buscado.length < 2) return false
    val nombre = clave(rubro.nombre)
    return nombre.contains(buscado) || SINONIMOS[nombre].orEmpty().any { it.startsWith(buscado) || buscado.startsWith(it) }
}
