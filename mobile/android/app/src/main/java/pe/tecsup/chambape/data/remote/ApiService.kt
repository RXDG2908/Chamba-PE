package pe.tecsup.chambape.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
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
)

data class DisponibilidadDto(
    @SerializedName("disponible") val disponible: Boolean?,
    @SerializedName("estado") val estado: String?,
)

data class CambioDisponibilidadDto(@SerializedName("disponible") val disponible: Boolean)

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
    @GET("api/usuarios/disponibilidad/")
    suspend fun disponibilidad(@Header("Authorization") autorizacion: String): DisponibilidadDto

    @PATCH("api/usuarios/disponibilidad/")
    suspend fun cambiarDisponibilidad(
        @Header("Authorization") autorizacion: String,
        @Body cambio: CambioDisponibilidadDto,
    ): DisponibilidadDto
}

object RetrofitClient {
    fun crear(baseUrl: String): ApiService =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
}
