package pe.tecsup.chambape.device

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import pe.tecsup.chambape.MainActivity
import pe.tecsup.chambape.R

data class Coordenadas(val latitud: Double, val longitud: Double)

/** Ubicación aproximada del celular. Devuelve null si no hay permiso o no se pudo obtener. */
interface UbicacionProvider {
    suspend fun actual(): Coordenadas?
}

class UbicacionFused(private val context: Context) : UbicacionProvider {
    private val cliente = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission") // se comprueba justo antes de pedir la ubicación
    override suspend fun actual(): Coordenadas? {
        val permiso = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (permiso != PackageManager.PERMISSION_GRANTED) return null
        return try {
            // Con límite de tiempo: sin señal, getCurrentLocation puede no responder nunca.
            // Si vence, se usa la última ubicación conocida (o null y la pantalla avisa).
            val ubicacion = withTimeoutOrNull(ESPERA_MAXIMA_MS) {
                cliente.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token).await()
            } ?: withTimeoutOrNull(ESPERA_ULTIMA_MS) { cliente.lastLocation.await() }
            ubicacion?.let { Coordenadas(it.latitude, it.longitude) }
        } catch (e: SecurityException) {
            null
        }
    }

    private companion object {
        const val ESPERA_MAXIMA_MS = 10_000L
        const val ESPERA_ULTIMA_MS = 2_000L
    }
}

/** Notificaciones locales de la app. */
interface Notificador {
    fun solicitudEnviada(nombrePrestador: String)
}

class NotificadorAndroid(private val context: Context) : Notificador {

    init {
        val canal = NotificationChannel(
            CANAL_SOLICITUDES,
            context.getString(R.string.canal_solicitudes_nombre),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.canal_solicitudes_descripcion) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }

    @SuppressLint("MissingPermission") // areNotificationsEnabled cubre el permiso de Android 13+
    override fun solicitudEnviada(nombrePrestador: String) {
        val gestor = NotificationManagerCompat.from(context)
        if (!gestor.areNotificationsEnabled()) return
        // Al tocarla se abre la app.
        val abrirApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notificacion = NotificationCompat.Builder(context, CANAL_SOLICITUDES)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.notificacion_solicitud_titulo))
            .setContentText(context.getString(R.string.notificacion_solicitud_texto, nombrePrestador))
            .setContentIntent(abrirApp)
            .setAutoCancel(true)
            .build()
        gestor.notify(nombrePrestador.hashCode(), notificacion)
    }

    private companion object {
        const val CANAL_SOLICITUDES = "solicitudes"
    }
}
