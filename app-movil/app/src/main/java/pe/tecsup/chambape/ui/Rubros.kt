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
