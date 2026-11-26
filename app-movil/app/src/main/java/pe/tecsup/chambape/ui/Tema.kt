package pe.tecsup.chambape.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Sistema visual de ChambaPE en la app (AND-02): los mismos colores de marca que la web
 * (verde #16734F y acento naranja #F29F3D, ver web-portal/src/estilos/Tema.css), expresados
 * con los roles de color de Material 3 para que claro, oscuro y contraste funcionen solos.
 */
private val Claro = lightColorScheme(
    primary = Color(0xFF16734F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB7F0D3),
    onPrimaryContainer = Color(0xFF002114),
    secondary = Color(0xFF8A5100),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDDB8),
    onSecondaryContainer = Color(0xFF2C1600),
    tertiary = Color(0xFF2463A6),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD4E3FF),
    onTertiaryContainer = Color(0xFF001C3A),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF5F8F6),
    onBackground = Color(0xFF1C2B23),
    surface = Color(0xFFF5F8F6),
    onSurface = Color(0xFF1C2B23),
    surfaceVariant = Color(0xFFDCE5DE),
    onSurfaceVariant = Color(0xFF475A50),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEFF4F1),
    surfaceContainer = Color(0xFFEAF0EC),
    surfaceContainerHigh = Color(0xFFE4EBE7),
    surfaceContainerHighest = Color(0xFFDEE6E1),
    outline = Color(0xFF718077),
    outlineVariant = Color(0xFFC0CCC4),
)

private val Oscuro = darkColorScheme(
    primary = Color(0xFF7FD8B5),
    onPrimary = Color(0xFF003825),
    primaryContainer = Color(0xFF005237),
    onPrimaryContainer = Color(0xFFB7F0D3),
    secondary = Color(0xFFFFB866),
    onSecondary = Color(0xFF492900),
    secondaryContainer = Color(0xFF693C00),
    onSecondaryContainer = Color(0xFFFFDDB8),
    tertiary = Color(0xFFA5C8FF),
    onTertiary = Color(0xFF00315E),
    tertiaryContainer = Color(0xFF004884),
    onTertiaryContainer = Color(0xFFD4E3FF),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1512),
    onBackground = Color(0xFFDDE5E0),
    surface = Color(0xFF0F1512),
    onSurface = Color(0xFFDDE5E0),
    surfaceVariant = Color(0xFF3F4943),
    onSurfaceVariant = Color(0xFFBFC9C2),
    surfaceContainerLowest = Color(0xFF0A0F0D),
    surfaceContainerLow = Color(0xFF171D1A),
    surfaceContainer = Color(0xFF1B211E),
    surfaceContainerHigh = Color(0xFF252B28),
    surfaceContainerHighest = Color(0xFF303633),
    outline = Color(0xFF89938C),
    outlineVariant = Color(0xFF3F4943),
)

private val Formas = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val Tipografia = Typography().let { base ->
    base.copy(
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Curvas de Material 3: llegada con desaceleración, salida rápida. */
object Movimiento {
    val Enfatizada = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Desacelerada = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val Acelerada = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    const val CORTA = 150
    const val MEDIA = 300
    const val LARGA = 450
}

@Composable
fun ChambaPeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Oscuro else Claro,
        shapes = Formas,
        typography = Tipografia,
        content = content,
    )
}
