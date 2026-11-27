package pe.tecsup.chambape.data.remote

import com.google.gson.annotations.SerializedName
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Lo que responde la API de usuario (Spring Boot; las mismas respuestas que Django, docs/API_BUSQUEDA.md). Todo puede venir nulo: se valida en el mapper. */
data class RubroDto(
    @SerializedName("id") val id: Int?,
    @SerializedName("nombre") val nombre: String?,
)

data class PrestadorDto(
    @SerializedName("id") val id: Int?,
    @SerializedName("nombres") val nombres: String?,
    @SerializedName("apellidos") val apellidos: String?,
    @SerializedName("rubros") val rubros: List<RubroDto>?,
    @SerializedName("latitud") val latitud: Double?,
    @SerializedName("longitud") val longitud: Double?,
    @SerializedName("distancia_km") val distanciaKm: Double?,
    // HU-12: null mientras no tenga calificaciones.
    @SerializedName("calificacion_promedio") val calificacionPromedio: Double? = null,
    @SerializedName("calificaciones_total") val calificacionesTotal: Int? = null,
    // Perfil: empresa o independiente, el nombre que ven los clientes y si se registró hace poco («Nuevo»).
    @SerializedName("tipo") val tipo: String? = null,
    @SerializedName("nombre_publico") val nombrePublico: String? = null,
    @SerializedName("nuevo") val nuevo: Boolean? = null,
)

data class DisponibilidadDto(
    @SerializedName("disponible") val disponible: Boolean?,
    @SerializedName("estado") val estado: String?,
)

data class CambioDisponibilidadDto(@SerializedName("disponible") val disponible: Boolean)

data class RegistroPrestadorDto(
    @SerializedName("nombres") val nombres: String,
    @SerializedName("apellidos") val apellidos: String,
    @SerializedName("dni") val dni: String,
    @SerializedName("telefono") val telefono: String,
    @SerializedName("rubros") val rubros: List<Int>,
    @SerializedName("latitud") val latitud: String,
    @SerializedName("longitud") val longitud: String,
    @SerializedName("radio_cobertura_km") val radioCoberturaKm: Int,
)

// ---------- Solicitudes, seguimiento y calificación (las mismas rutas que usa la web) ----------

data class RegistroClienteDto(@SerializedName("nombre") val nombre: String)

/** GET /api/usuarios/yo/: la cuenta de Django vinculada al token (rol y prestador). */
data class UsuarioDto(
    @SerializedName("email") val email: String?,
    @SerializedName("nombres") val nombres: String?,
    @SerializedName("apellidos") val apellidos: String?,
    @SerializedName("rol") val rol: String?,
    @SerializedName("prestador_id") val prestadorId: Int?,
)

data class CrearSolicitudDto(
    @SerializedName("prestador") val prestador: Int,
    @SerializedName("rubro") val rubro: Int,
    @SerializedName("cliente_nombre") val clienteNombre: String,
    @SerializedName("cliente_telefono") val clienteTelefono: String,
    @SerializedName("descripcion") val descripcion: String,
    // Seis decimales con punto, como los guarda Django.
    @SerializedName("latitud") val latitud: String,
    @SerializedName("longitud") val longitud: String,
)

data class SolicitudCreadaDto(@SerializedName("id") val id: Int?)

data class EtapaDto(@SerializedName("estado") val estado: String?, @SerializedName("fecha") val fecha: String?)

data class PrestadorResumenDto(@SerializedName("id") val id: Int?, @SerializedName("nombre") val nombre: String?)

data class CalificacionDto(@SerializedName("estrellas") val estrellas: Int?)

/** `GET api/calificaciones/mis-servicios/`: lo que ve el cliente. */
data class MiServicioDto(
    @SerializedName("id") val id: Int?,
    @SerializedName("prestador") val prestador: PrestadorResumenDto?,
    @SerializedName("rubro") val rubro: String?,
    @SerializedName("descripcion") val descripcion: String?,
    @SerializedName("estado") val estado: String?,
    @SerializedName("estado_display") val estadoDisplay: String?,
    @SerializedName("historial") val historial: List<EtapaDto>?,
    @SerializedName("calificacion") val calificacion: CalificacionDto?,
    @SerializedName("puede_calificar") val puedeCalificar: Boolean?,
)

/** `GET api/servicios/solicitudes/recibidas/`: lo que ve el prestador. */
data class SolicitudRecibidaDto(
    @SerializedName("id") val id: Int?,
    @SerializedName("rubro") val rubro: String?,
    @SerializedName("cliente_nombre") val clienteNombre: String?,
    @SerializedName("cliente_telefono") val clienteTelefono: String?,
    @SerializedName("descripcion") val descripcion: String?,
    @SerializedName("estado") val estado: String?,
    @SerializedName("estado_display") val estadoDisplay: String?,
    @SerializedName("siguiente_estado") val siguienteEstado: String?,
    @SerializedName("historial") val historial: List<EtapaDto>?,
)

data class AvanzarDto(@SerializedName("estado") val estado: String)

data class CalificarDto(
    @SerializedName("solicitud") val solicitud: Int,
    @SerializedName("estrellas") val estrellas: Int,
    @SerializedName("comentario") val comentario: String,
)

/** HU-10: la API devuelve más campos; la app usa los que muestra. */
data class RecomendadoDto(
    @SerializedName("id") val id: Int,
    @SerializedName("motivo") val motivo: String?,
    @SerializedName("puntaje") val puntaje: Double?,
    @SerializedName("tiempo_estimado_min") val tiempoEstimadoMin: Int?,
)

data class RecomendacionDto(
    @SerializedName("recomendado") val recomendado: RecomendadoDto?,
)

/** La contraseña de la cuenta de la web, para vincularla con la cuenta de la app. */
data class VincularCuentaDto(@SerializedName("password") val password: String)

interface ApiService {
    // HU-10: pública como la búsqueda (sin token), en Spring igual que en Django.
    @GET("api/ia/recomendacion/")
    suspend fun recomendacion(
        @Query("rubro") rubroId: Int,
        @Query("latitud") latitud: Double,
        @Query("longitud") longitud: Double,
    ): RecomendacionDto

    @GET("api/prestadores/rubros/")
    suspend fun rubros(): List<RubroDto>

    @GET("api/prestadores/buscar/")
    suspend fun buscar(
        @Query("rubro") rubroId: Int,
        @Query("latitud") latitud: Double,
        @Query("longitud") longitud: Double,
    ): List<PrestadorDto>

    // Rutas privadas: la cabecera lleva "Bearer <token de Firebase>".
    @POST("api/prestadores/registro-movil/")
    suspend fun registrarPrestador(
        @Header("Authorization") autorizacion: String,
        @Body datos: RegistroPrestadorDto,
    ): ResponseBody

    @GET("api/usuarios/disponibilidad/")
    suspend fun disponibilidad(@Header("Authorization") autorizacion: String): DisponibilidadDto

    @PATCH("api/usuarios/disponibilidad/")
    suspend fun cambiarDisponibilidad(
        @Header("Authorization") autorizacion: String,
        @Body cambio: CambioDisponibilidadDto,
    ): DisponibilidadDto

    // ---------- Solicitudes (HU-4, HU-11 y HU-12) con el token de Firebase ----------

    /** Crea (o devuelve) la cuenta de cliente de Django vinculada al usuario de Firebase. */
    @POST("api/usuarios/registro-movil/")
    suspend fun registrarClienteMovil(@Header("Authorization") autorizacion: String, @Body datos: RegistroClienteDto): UsuarioDto

    /** Rol y prestador de la cuenta (antes en Firestore `usuarios/{uid}`). 403 con `codigo` si no está vinculada o sancionada. */
    // Misma cuenta en la web y la app: vincula esta cuenta de Firebase con la de la web del mismo correo.
    @POST("api/usuarios/vincular/")
    suspend fun vincularCuenta(@Header("Authorization") autorizacion: String, @Body datos: VincularCuentaDto): UsuarioDto

    @GET("api/usuarios/yo/")
    suspend fun yo(@Header("Authorization") autorizacion: String): UsuarioDto

    @POST("api/servicios/solicitudes/")
    suspend fun crearSolicitud(@Header("Authorization") autorizacion: String, @Body datos: CrearSolicitudDto): SolicitudCreadaDto

    @GET("api/calificaciones/mis-servicios/")
    suspend fun misServicios(@Header("Authorization") autorizacion: String): List<MiServicioDto>

    @GET("api/servicios/solicitudes/recibidas/")
    suspend fun solicitudesRecibidas(@Header("Authorization") autorizacion: String): List<SolicitudRecibidaDto>

    @POST("api/servicios/solicitudes/{id}/aceptar/")
    suspend fun aceptarSolicitud(@Header("Authorization") autorizacion: String, @Path("id") id: Int): SolicitudRecibidaDto

    @POST("api/servicios/solicitudes/{id}/rechazar/")
    suspend fun rechazarSolicitud(@Header("Authorization") autorizacion: String, @Path("id") id: Int): SolicitudRecibidaDto

    @POST("api/servicios/solicitudes/{id}/avanzar/")
    suspend fun avanzarSolicitud(
        @Header("Authorization") autorizacion: String,
        @Path("id") id: Int,
        @Body datos: AvanzarDto,
    ): SolicitudRecibidaDto

    @POST("api/calificaciones/")
    suspend fun calificar(@Header("Authorization") autorizacion: String, @Body datos: CalificarDto)
}

object RetrofitClient {
    /**
     * En el emulador, 127.0.0.1 es el propio emulador: la API de la PC está en 10.0.2.2. Así la app
     * no depende de `adb reverse`, que se pierde cada vez que se reinicia adb o el emulador (y la
     * app solo decía «No hay conexión»). En un teléfono por USB se sigue usando 127.0.0.1 con
     * `adb reverse`, y una URL pública no se toca.
     */
    fun urlParaEsteEquipo(baseUrl: String, enEmulador: Boolean): String {
        if (!enEmulador) return baseUrl
        return Regex("""^(https?://)(127\.0\.0\.1|localhost)(?=[:/]|$)""").replace(baseUrl) { it.groupValues[1] + "10.0.2.2" }
    }

    /** Emulador del SDK de Android (imágenes «sdk_gphone», hardware ranchu o goldfish). */
    fun esEmulador(): Boolean = android.os.Build.HARDWARE in setOf("ranchu", "goldfish") ||
        android.os.Build.FINGERPRINT.startsWith("generic") || android.os.Build.PRODUCT.startsWith("sdk_gphone")

    fun crear(baseUrl: String): ApiService =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
}
