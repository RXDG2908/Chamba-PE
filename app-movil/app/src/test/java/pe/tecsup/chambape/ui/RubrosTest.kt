package pe.tecsup.chambape.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Plumbing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RubrosTest {

    @Test
    fun `el nombre se compara sin tildes ni mayusculas`() {
        assertEquals("albanileria", clave("  Albañilería "))
        assertEquals(Icons.Outlined.Plumbing, iconoRubro("Gasfitería"))
        assertEquals(Icons.Outlined.Plumbing, iconoRubro("GASFITERIA"))
    }

    @Test
    fun `cada rubro de la base tiene su propio icono`() {
        val rubros = listOf(
            "Gasfitería", "Electricidad", "Carpintería", "Pintura", "Albañilería",
            "Cerrajería", "Limpieza", "Jardinería", "Soldadura", "Mecánica",
        )
        val iconos = rubros.map(::iconoRubro)
        assertEquals(rubros.size, iconos.toSet().size)
        iconos.forEach { assertNotEquals(Icons.Outlined.Handyman, it) }
    }

    @Test
    fun `un rubro desconocido recibe la herramienta generica`() {
        assertEquals(Icons.Outlined.Handyman, iconoRubro("Tapicería"))
    }
}
