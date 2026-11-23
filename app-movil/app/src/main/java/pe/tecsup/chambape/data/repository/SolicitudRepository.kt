package pe.tecsup.chambape.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import pe.tecsup.chambape.domain.model.EstadoSolicitud
import pe.tecsup.chambape.domain.model.Prestador
import pe.tecsup.chambape.domain.model.Rol
import pe.tecsup.chambape.domain.model.Sesion
import pe.tecsup.chambape.domain.model.Solicitud

interface SolicitudRepository {
    /** Solicitudes de la sesión, en vivo: las que envió el cliente o las que recibió el prestador. */
    fun deLaSesion(sesion: Sesion): Flow<List<Solicitud>>
    suspend fun crear(sesion: Sesion, prestador: Prestador, descripcion: String, direccion: String)
    suspend fun responder(solicitudId: String, aceptar: Boolean)
}

/** Solicitudes en Cloud Firestore (`solicitudes/{id}`); las reglas filtran por usuario. */
class FirestoreSolicitudRepository(private val firestore: FirebaseFirestore) : SolicitudRepository {

    override fun deLaSesion(sesion: Sesion): Flow<List<Solicitud>> {
        val coleccion = firestore.collection(SOLICITUDES)
        val consulta = when (sesion.rol) {
            Rol.CLIENTE -> coleccion.whereEqualTo("clienteUid", sesion.uid)
            // Prestador aún sin vincular por un administrador: no tiene bandeja.
            Rol.PRESTADOR -> coleccion.whereEqualTo("prestadorId", sesion.prestadorId ?: return flowOf(emptyList()))
        }
        return callbackFlow {
            val registro = consulta.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                } else if (snapshot != null) {
                    trySend(
                        snapshot.documents.mapNotNull { it.aSolicitud() }
                            .sortedByDescending { it.creadaEnMillis ?: Long.MAX_VALUE },
                    )
                }
            }
            awaitClose { registro.remove() }
        }
    }

    override suspend fun crear(sesion: Sesion, prestador: Prestador, descripcion: String, direccion: String) {
        firestore.collection(SOLICITUDES).add(
            mapOf(
                "prestadorId" to prestador.id,
                "prestadorNombre" to prestador.nombreCompleto,
                "clienteUid" to sesion.uid,
                "clienteNombre" to sesion.nombre,
                "descripcion" to descripcion.trim(),
                "direccion" to direccion.trim(),
                "estado" to "pendiente",
                "creadaEn" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    override suspend fun responder(solicitudId: String, aceptar: Boolean) {
        firestore.collection(SOLICITUDES).document(solicitudId).update(
            mapOf(
                "estado" to if (aceptar) "aceptada" else "rechazada",
                "respondidaEn" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    private fun DocumentSnapshot.aSolicitud(): Solicitud? {
        return Solicitud(
            id = id,
            prestadorId = getLong("prestadorId")?.toInt() ?: return null,
            prestadorNombre = getString("prestadorNombre").orEmpty(),
            clienteUid = getString("clienteUid") ?: return null,
            clienteNombre = getString("clienteNombre").orEmpty(),
            descripcion = getString("descripcion").orEmpty(),
            direccion = getString("direccion").orEmpty(),
            estado = when (getString("estado")) {
                "aceptada" -> EstadoSolicitud.ACEPTADA
                "rechazada" -> EstadoSolicitud.RECHAZADA
                else -> EstadoSolicitud.PENDIENTE
            },
            creadaEnMillis = getTimestamp("creadaEn")?.toDate()?.time,
        )
    }

    private companion object {
        const val SOLICITUDES = "solicitudes"
    }
}
