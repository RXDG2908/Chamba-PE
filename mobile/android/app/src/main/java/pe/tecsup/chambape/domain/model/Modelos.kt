package pe.tecsup.chambape.domain.model

/** Modelos que usa la interfaz. No dependen de Retrofit, Room ni Firebase. */

data class Rubro(val id: Int, val nombre: String)

data class Prestador(
    val id: Int,
    val nombres: String,
    val apellidos: String,
    val rubros: List<String>,
    val latitud: Double,
    val longitud: Double,
    val distanciaKm: Double?,
    val favorito: Boolean,
) {
    val nombreCompleto: String get() = "$nombres $apellidos".trim()
}

/** Dato junto con su origen: [desdeCache] es true cuando no hubo red y salió de Room. */
data class Datos<T>(val valor: T, val desdeCache: Boolean)

enum class TipoError { SIN_CONEXION, SERVIDOR, DATOS_INVALIDOS }

class ErrorDatos(val tipo: TipoError, causa: Throwable? = null) : Exception(tipo.name, causa)

enum class Rol { CLIENTE, PRESTADOR }

data class Sesion(
    val uid: String,
    val correo: String,
    val nombre: String,
    val rol: Rol,
    /** ID del prestador en la API Django; lo vincula un administrador en Firestore. */
    val prestadorId: Int?,
)

sealed interface EstadoSesion {
    data object Cargando : EstadoSesion
    data object SinSesion : EstadoSesion
    data class Activa(val sesion: Sesion) : EstadoSesion
}

enum class TipoErrorAuth { CREDENCIALES, CORREO_EN_USO, CLAVE_DEBIL, CORREO_INVALIDO, SIN_CONEXION, DESCONOCIDO }

class ErrorAuth(val tipo: TipoErrorAuth, causa: Throwable? = null) : Exception(tipo.name, causa)

enum class EstadoSolicitud { PENDIENTE, ACEPTADA, RECHAZADA }

data class Solicitud(
    val id: String,
    val prestadorId: Int,
    val prestadorNombre: String,
    val clienteUid: String,
    val clienteNombre: String,
    val descripcion: String,
    val direccion: String,
    val estado: EstadoSolicitud,
    val creadaEnMillis: Long?,
)
