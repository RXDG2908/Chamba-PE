package pe.tecsup.chambape.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Plumbing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.tecsup.chambape.domain.model.Rubro

private val BASE = listOf(
    "Gasfitería", "Electricidad", "Carpintería", "Pintura", "Albañilería",
    "Cerrajería", "Limpieza", "Jardinería", "Soldadura", "Mecánica",
)
private val NUEVOS = listOf(
    "Cámaras de seguridad", "Aire acondicionado", "Fumigación", "Redes e internet",
    "Mudanzas", "Vidriería", "Electrodomésticos", "Techos y drywall",
)

class RubrosTest {

    @Test
    fun `el nombre se compara sin tildes ni mayusculas`() {
        assertEquals("albanileria", clave("  Albañilería "))
        assertEquals(Icons.Outlined.Plumbing, iconoRubro("Gasfitería"))
        assertEquals(Icons.Outlined.Plumbing, iconoRubro("GASFITERIA"))
    }

    @Test
    fun `cada rubro conocido tiene su propio icono`() {
        val rubros = BASE + NUEVOS
        val iconos = rubros.map(::iconoRubro)
        assertEquals(rubros.size, iconos.toSet().size)
        iconos.forEach { assertNotEquals(Icons.Outlined.Handyman, it) }
    }

    @Test
    fun `un rubro desconocido recibe la herramienta generica`() {
        assertEquals(Icons.Outlined.Handyman, iconoRubro("Tapicería"))
    }

    @Test
    fun `cada rubro conocido esta en al menos una seccion y los desconocidos en mas servicios`() {
        val todos = (BASE + NUEVOS + "Tapicería").mapIndexed { i, n -> Rubro(i + 1, n) }
        val enSecciones = Seccion.entries.filter { it != Seccion.OTROS }.flatMap { it.rubrosDe(todos) }.map { it.nombre }.toSet()
        assertTrue((BASE + NUEVOS).all { it in enSecciones })
        assertEquals(listOf("Tapicería"), Seccion.OTROS.rubrosDe(todos).map { it.nombre })
    }

    @Test
    fun `urgente trae lo que no puede esperar, en ese orden`() {
        val todos = (BASE + NUEVOS).mapIndexed { i, n -> Rubro(i + 1, n) }
        assertEquals(listOf("Gasfitería", "Cerrajería", "Electricidad", "Vidriería"), Seccion.URGENTE.rubrosDe(todos).map { it.nombre })
        // Solo con los 10 rubros de la base, la sección muestra los que existen.
        assertEquals(listOf("Gasfitería", "Cerrajería", "Electricidad"), Seccion.URGENTE.rubrosDe(todos.take(10)).map { it.nombre })
    }
}
