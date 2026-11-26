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

/** Lo que responde la API Django (docs/API_BUSQUEDA.md). Todo puede venir nulo: se valida en el mapper. */
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

interface ApiService {
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
    fun crear(baseUrl: String): ApiService =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
}
