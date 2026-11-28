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
    /** HU-12: promedio de 1 a 5 estrellas; null sin calificaciones. */
    val calificacionPromedio: Double? = null,
    val calificacionesTotal: Int = 0,
    /** Nombre que ven los clientes (el de su empresa o el suyo); null con un servidor antiguo. */
    val nombrePublico: String? = null,
    val empresa: Boolean = false,
    /** Registrado hace menos de 30 días: se muestra «Nuevo». */
    val nuevo: Boolean = false,
) {
    val nombreCompleto: String get() = nombrePublico ?: "$nombres $apellidos".trim()
    val tieneCalificaciones: Boolean get() = calificacionesTotal > 0 && calificacionPromedio?.isFinite() == true
}

/** Dato junto con su origen: [desdeCache] es true cuando no hubo red y salió de Room. */
data class Datos<T>(val valor: T, val desdeCache: Boolean)

enum class TipoError { SIN_CONEXION, SERVIDOR, DATOS_INVALIDOS, CUENTA_NO_VINCULADA }

enum class EstadoVerificacion { VERIFICADO, PENDIENTE, RECHAZADO }

/** Disponibilidad del prestador y estado de su verificación, tal como los guarda la API. */
data class Disponibilidad(val disponible: Boolean, val estado: EstadoVerificacion)

data class DatosRegistroPrestador(
    val nombres: String,
    val apellidos: String,
    val dni: String,
    val telefono: String,
    val rubros: Set<Int>,
    val latitud: Double,
    val longitud: Double,
    val radioKm: Int,
)

/** El servidor rechazó el registro: mensajes por campo (o `detail` para uno general). */
class ErrorRegistro(val campos: Map<String, String>) : Exception("registro rechazado")

class ErrorDatos(val tipo: TipoError, causa: Throwable? = null) : Exception(tipo.name, causa)

enum class Rol { CLIENTE, PRESTADOR }

data class Sesion(
    val uid: String,
    val correo: String,
    val nombre: String,
    val rol: Rol,
    /** ID del prestador en la API Django (GET /api/usuarios/yo/); null para clientes. */
    val prestadorId: Int?,
)

sealed interface EstadoSesion {
    data object Cargando : EstadoSesion
    data object SinSesion : EstadoSesion
    data class Activa(val sesion: Sesion) : EstadoSesion
}

enum class TipoErrorAuth { CREDENCIALES, CORREO_EN_USO, CLAVE_DEBIL, CORREO_INVALIDO, SIN_CONEXION, CUENTA_SANCIONADA, DEMASIADOS_INTENTOS, DESCONOCIDO }

class ErrorAuth(val tipo: TipoErrorAuth, causa: Throwable? = null) : Exception(tipo.name, causa)

/** Estados de la solicitud en Django (HU-4 y HU-11), con el valor que usa la API. */
enum class EstadoSolicitud(val api: String) {
    PENDIENTE("pendiente"), ACEPTADA("aceptada"), RECHAZADA("rechazada"),
    EN_CAMINO("en_camino"), EN_SERVICIO("en_servicio"), FINALIZADA("finalizada");

    companion object {
        fun de(valor: String?): EstadoSolicitud? = entries.firstOrNull { it.api == valor }
    }
}

/** Una etapa del seguimiento (HU-11) con su fecha y hora ISO 8601. */
data class Etapa(val estado: EstadoSolicitud, val fecha: String)

/**
 * Solicitud tal como la guarda Django, la misma que ve la web. [contraparte] es el prestador
 * para el cliente y el cliente para el prestador; [telefono] solo lo ve el prestador.
 */
data class Solicitud(
    val id: Int,
    val contraparte: String,
    val telefono: String?,
    val rubro: String,
    val descripcion: String,
    val estado: EstadoSolicitud,
    val estadoTexto: String,
    /** Estado al que el prestador puede pasarla ahora (null si no puede avanzarla). */
    val siguienteEstado: EstadoSolicitud? = null,
    val historial: List<Etapa> = emptyList(),
    /** Cliente: puede calificar porque está Finalizada y aún no la calificó (HU-12). */
    val puedeCalificar: Boolean = false,
    val estrellas: Int? = null,
)

/** Lo que se envía al pedir un servicio: rubro y ubicación de la búsqueda, más el contacto. */
data class NuevaSolicitud(
    val rubroId: Int,
    val descripcion: String,
    val telefono: String,
    val latitud: Double,
    val longitud: Double,
)

/** HU-10: por qué la IA recomienda a este prestador y en cuánto llegaría. */
data class Recomendacion(val prestadorId: Int, val motivo: String, val puntaje: Double?, val minutosEstimados: Int)

/** Un mensaje del chat; [mio] si lo escribió quien tiene la sesión. [enviado] en ISO 8601. */
data class MensajeChat(val id: Int, val mio: Boolean, val texto: String, val enviado: String)

/**
 * Chat de una solicitud aceptada. [activo] mientras el servicio no termina; al finalizar queda
 * solo para leer. [telefono] es el de la otra parte (null si no hay o si ya no se muestra).
 */
data class Chat(
    val solicitudId: Int,
    val estadoTexto: String,
    val activo: Boolean,
    val contraparte: String,
    val telefono: String?,
    val mensajes: List<MensajeChat>,
)
