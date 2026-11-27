package pe.tecsup.chambape.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Carpenter
import androidx.compose.material.icons.outlined.CarRepair
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ElectricalServices
import androidx.compose.material.icons.outlined.FormatPaint
import androidx.compose.material.icons.outlined.Foundation
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Plumbing
import androidx.compose.material.icons.outlined.Yard
import androidx.compose.ui.graphics.vector.ImageVector
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
    else -> Icons.Outlined.Handyman
}

internal fun clave(nombre: String): String =
    Normalizer.normalize(nombre.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
