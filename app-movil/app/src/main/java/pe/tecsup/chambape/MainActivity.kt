package pe.tecsup.chambape

import android.animation.ValueAnimator
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.view.animation.PathInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import pe.tecsup.chambape.ui.ChambaPeApp
import pe.tecsup.chambape.ui.ChambaPeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Antes de super.onCreate: el tema de arranque (pin animado) cede su lugar al tema de la app.
        val arranque = installSplashScreen()
        super.onCreate(savedInstanceState)
        // El sistema quita el arranque con el primer cuadro de la app y podría cortar el pin a medio
        // caer. Se sostiene solo hasta que termina la animación (740 ms desde que nace el proceso):
        // en un arranque en frío normal eso no añade espera.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ValueAnimator.areAnimatorsEnabled()) {
            val fin = Process.getStartUptimeMillis() + (ANIMACION_MS * escalaDeAnimacion()).toLong()
            arranque.setKeepOnScreenCondition { SystemClock.uptimeMillis() < fin }
        }
        arranque.setOnExitAnimationListener { proveedor ->
            val vista = proveedor.view
            // Sin animaciones en el sistema (o en reducir movimiento) se quita al instante.
            if (!ValueAnimator.areAnimatorsEnabled()) {
                proveedor.remove()
                return@setOnExitAnimationListener
            }
            // Salida más corta que la entrada (200 ms): el pin sube un poco y la pantalla se funde con la app.
            proveedor.iconView.animate()
                .translationY(-vista.height * 0.03f)
                .setDuration(SALIDA_MS)
                .setInterpolator(SALIDA)
                .start()
            vista.animate()
                .alpha(0f)
                .setDuration(SALIDA_MS)
                .setInterpolator(SALIDA)
                .withEndAction { proveedor.remove() }
                .start()
        }
        enableEdgeToEdge()
        setContent {
            ChambaPeTheme {
                ChambaPeApp()
            }
        }
    }

    /** Respeta la escala de animaciones del sistema (opciones de desarrollador y accesibilidad). */
    private fun escalaDeAnimacion(): Float =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ValueAnimator.getDurationScale() else 1f

    private companion object {
        /** Duración del icono animado: res/drawable/splash_animado.xml y values-v31/themes.xml. */
        const val ANIMACION_MS = 740L
        const val SALIDA_MS = 200L
        /** La misma curva de salida que la web y el arranque. */
        val SALIDA = PathInterpolator(0.23f, 1f, 0.32f, 1f)
    }
}
