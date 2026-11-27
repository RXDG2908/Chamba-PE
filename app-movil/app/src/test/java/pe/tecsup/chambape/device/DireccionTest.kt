package pe.tecsup.chambape.device

import org.junit.Assert.assertEquals
import org.junit.Test

class DireccionTest {
    private val punto = Coordenadas(-12.1, -77.0)

    @Test
    fun `la linea corta usa el distrito y no la urbanizacion`() {
        val direccion = Direccion("Avenida Cascanueces 2221", "Asoc Nuevo Horizonte, Santa Anita, Provincia de Lima", punto, "Santa Anita")
        assertEquals("Avenida Cascanueces 2221, Santa Anita", direccion.corta)
    }

    @Test
    fun `sin distrito usa la primera parte del detalle y sin detalle solo la calle`() {
        assertEquals("Av. Larco 400, Miraflores", Direccion("Av. Larco 400", "Miraflores, Lima", punto).corta)
        assertEquals("Av. Larco 400", Direccion("Av. Larco 400", "", punto).corta)
        // Si el título ya es el distrito, no se repite.
        assertEquals("Lince", Direccion("Lince", "Lince, Lima", punto, "Lince").corta)
    }
}
