package pe.tecsup.chambape.data.mapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.tecsup.chambape.data.remote.PrestadorDto
import pe.tecsup.chambape.data.remote.RubroDto

/** La búsqueda trae el perfil del prestador: nombre público, empresa e insignia «Nuevo». */
class PerfilPrestadorTest {
    private fun dto(tipo: String? = null, nombrePublico: String? = null, nuevo: Boolean? = null) = PrestadorDto(
        id = 3, nombres = "Ana", apellidos = "Quispe", rubros = listOf(RubroDto(1, "Gasfitería")),
        latitud = -12.0, longitud = -77.0, distanciaKm = 1.0,
        tipo = tipo, nombrePublico = nombrePublico, nuevo = nuevo,
    )

    @Test
    fun empresaNuevaMuestraSuNombreComercialEInsignias() {
        val prestador = dto("empresa", "Gasfitería Ana SAC", true).aEntity(1, favorito = false).aModelo()
        assertEquals("Gasfitería Ana SAC", prestador.nombreCompleto)
        assertTrue(prestador.empresa)
        assertTrue(prestador.nuevo)
    }

    @Test
    fun conUnServidorAntiguoSinCamposDePerfilQuedaComoAntes() {
        val prestador = dto().aEntity(1, favorito = false).aModelo()
        assertEquals("Ana Quispe", prestador.nombreCompleto)
        assertFalse(prestador.empresa)
        assertFalse(prestador.nuevo)
    }

    @Test
    fun nombrePublicoEnBlancoNoBorraElNombre() {
        val prestador = dto("independiente", "   ", false).aEntity(1, favorito = false).aModelo()
        assertEquals("Ana Quispe", prestador.nombreCompleto)
    }
}
