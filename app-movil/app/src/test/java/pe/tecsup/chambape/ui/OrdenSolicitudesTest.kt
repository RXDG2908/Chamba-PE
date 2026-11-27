package pe.tecsup.chambape.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import pe.tecsup.chambape.domain.model.EstadoSolicitud
import pe.tecsup.chambape.domain.model.Solicitud

class OrdenSolicitudesTest {
    private fun solicitud(id: Int, estado: EstadoSolicitud) =
        Solicitud(id, "Rosa", null, "Gasfitería", "Fuga", estado, estado.name)

    @Test
    fun `las que siguen en curso van antes que las terminadas, sin mezclar su orden`() {
        val lista = listOf(
            solicitud(1, EstadoSolicitud.FINALIZADA),
            solicitud(2, EstadoSolicitud.EN_CAMINO),
            solicitud(3, EstadoSolicitud.RECHAZADA),
            solicitud(4, EstadoSolicitud.PENDIENTE),
            solicitud(5, EstadoSolicitud.EN_SERVICIO),
        )
        assertEquals(listOf(2, 4, 5, 1, 3), ordenarSolicitudes(lista).map { it.id })
    }
}
