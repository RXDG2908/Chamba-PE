package pe.tecsup.chambape.ui.busqueda

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pe.tecsup.chambape.device.BuscadorDirecciones
import pe.tecsup.chambape.device.Coordenadas
import pe.tecsup.chambape.device.Direccion
import pe.tecsup.chambape.device.UbicacionProvider

private val LARCO = Direccion("Av. José Larco 400", "Miraflores, Lima", Coordenadas(-12.12, -77.03))

private class BuscadorFalso : BuscadorDirecciones {
    val consultas = mutableListOf<String>()
    var resultados: List<Direccion> = listOf(LARCO)
    var nombre: Direccion? = LARCO

    override suspend fun buscar(texto: String): List<Direccion> {
        consultas += texto
        return resultados
    }

    override suspend fun nombrar(coordenadas: Coordenadas) = nombre
}

private class UbicacionFalsa(var valor: Coordenadas?) : UbicacionProvider {
    override suspend fun actual() = valor
}

@OptIn(ExperimentalCoroutinesApi::class)
class SelectorUbicacionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val buscador = BuscadorFalso()
    private val gps = UbicacionFalsa(Coordenadas(-12.1201, -77.0301))

    @Before
    fun preparar() = Dispatchers.setMain(dispatcher)

    @After
    fun limpiar() = Dispatchers.resetMain()

    @Test
    fun `espera a que la persona deje de escribir y consulta una sola vez`() = runTest(dispatcher) {
        val vm = SelectorUbicacionViewModel(buscador, gps)
        vm.escribir("Lar")
        vm.escribir("Larc")
        vm.escribir("Larco")
        advanceTimeBy(100)
        assertTrue(buscador.consultas.isEmpty())

        advanceUntilIdle()
        assertEquals(listOf("Larco"), buscador.consultas)
        assertEquals(EstadoSugerencias.Lista(listOf(LARCO)), vm.uiState.value.sugerencias)
    }

    @Test
    fun `menos de tres letras no consulta y sin coincidencias lo dice`() = runTest(dispatcher) {
        val vm = SelectorUbicacionViewModel(buscador, gps)
        vm.escribir("La")
        advanceUntilIdle()
        assertTrue(buscador.consultas.isEmpty())
        assertEquals(EstadoSugerencias.Inicial, vm.uiState.value.sugerencias)

        buscador.resultados = emptyList()
        vm.escribir("zzzz")
        advanceUntilIdle()
        assertEquals(EstadoSugerencias.SinResultados, vm.uiState.value.sugerencias)
    }

    @Test
    fun `mi ubicacion entrega la calle con el punto exacto del gps`() = runTest(dispatcher) {
        val vm = SelectorUbicacionViewModel(buscador, gps)
        vm.usarMiUbicacion()
        advanceUntilIdle()
        assertEquals(LARCO.copy(coordenadas = Coordenadas(-12.1201, -77.0301)), vm.elegida.first())
    }

    @Test
    fun `sin nombre de calle igual entrega el punto y sin gps avisa`() = runTest(dispatcher) {
        buscador.nombre = null
        val vm = SelectorUbicacionViewModel(buscador, gps)
        vm.usarMiUbicacion()
        advanceUntilIdle()
        assertEquals(Direccion("", "", Coordenadas(-12.1201, -77.0301)), vm.elegida.first())

        gps.valor = null
        vm.usarMiUbicacion()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.sinUbicacion)
    }

    @Test
    fun `el mapa nombra el punto bajo el alfiler y se confirma`() = runTest(dispatcher) {
        val vm = SelectorUbicacionViewModel(buscador, gps)
        val punto = Coordenadas(-12.13, -77.02)
        vm.moverMapa(punto)
        assertTrue(vm.uiState.value.nombrandoPunto)
        advanceUntilIdle()
        assertEquals(LARCO.copy(coordenadas = punto), vm.uiState.value.puntoMapa)

        vm.confirmarMapa()
        assertEquals(LARCO.copy(coordenadas = punto), vm.elegida.first())
    }
}
