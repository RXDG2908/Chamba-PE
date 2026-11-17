package pe.tecsup.chambape.ui.busqueda

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pe.tecsup.chambape.data.repository.PrestadorRepository
import pe.tecsup.chambape.device.Coordenadas
import pe.tecsup.chambape.device.UbicacionProvider
import pe.tecsup.chambape.domain.model.Datos
import pe.tecsup.chambape.domain.model.ErrorDatos
import pe.tecsup.chambape.domain.model.Prestador
import pe.tecsup.chambape.domain.model.Rubro
import pe.tecsup.chambape.domain.model.TipoError

private fun unPrestador(id: Int, nombre: String = "Ana") =
    Prestador(id, nombre, "Pérez", listOf("Gasfitería"), -12.046, -77.043, 1.5, favorito = false)

/** Repositorio falso: cada búsqueda espera a que la prueba la complete. */
private class RepositorioFalso : PrestadorRepository {
    var rubros: () -> Datos<List<Rubro>> = { Datos(listOf(Rubro(1, "Gasfitería")), desdeCache = false) }
    val busquedas = mutableListOf<CompletableDeferred<Datos<List<Prestador>>>>()
    val favoritosFlow = MutableStateFlow<List<Prestador>>(emptyList())

    override suspend fun rubros() = rubros.invoke()

    override suspend fun buscar(rubroId: Int, latitud: Double, longitud: Double): Datos<List<Prestador>> {
        val pendiente = CompletableDeferred<Datos<List<Prestador>>>()
        busquedas += pendiente
        return pendiente.await()
    }

    override suspend fun prestador(id: Int): Prestador? = null
    override fun favoritos(): Flow<List<Prestador>> = favoritosFlow
    override suspend fun alternarFavorito(id: Int): Boolean {
        favoritosFlow.value = listOf(unPrestador(id))
        return true
    }
}

private class UbicacionFalsa(var valor: Coordenadas?) : UbicacionProvider {
    override suspend fun actual() = valor
}

@OptIn(ExperimentalCoroutinesApi::class)
class BusquedaViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repositorio = RepositorioFalso()
    private val ubicacion = UbicacionFalsa(Coordenadas(-12.05, -77.04))

    @Before
    fun preparar() = Dispatchers.setMain(dispatcher)

    @After
    fun limpiar() = Dispatchers.resetMain()

    private fun viewModelListo() = BusquedaViewModel(repositorio, ubicacion).apply {
        seleccionarRubro(1)
        cambiarLatitud("-12.046")
        cambiarLongitud("-77.043")
    }

    @Test
    fun `carga los rubros al iniciar`() = runTest(dispatcher) {
        val vm = BusquedaViewModel(repositorio, ubicacion)
        assertEquals(EstadoRubros.Cargando, vm.uiState.value.rubros)
        advanceUntilIdle()
        assertEquals(EstadoRubros.Listo(listOf(Rubro(1, "Gasfitería")), desdeCache = false), vm.uiState.value.rubros)
    }

    @Test
    fun `sin conexion al cargar rubros muestra error y permite reintentar`() = runTest(dispatcher) {
        repositorio.rubros = { throw ErrorDatos(TipoError.SIN_CONEXION) }
        val vm = BusquedaViewModel(repositorio, ubicacion)
        advanceUntilIdle()
        assertEquals(EstadoRubros.Error(TipoError.SIN_CONEXION), vm.uiState.value.rubros)

        repositorio.rubros = { Datos(listOf(Rubro(2, "Electricidad")), desdeCache = true) }
        vm.cargarRubros()
        advanceUntilIdle()
        assertEquals(EstadoRubros.Listo(listOf(Rubro(2, "Electricidad")), desdeCache = true), vm.uiState.value.rubros)
    }

    @Test
    fun `una entrada invalida no llama al repositorio`() = runTest(dispatcher) {
        val vm = BusquedaViewModel(repositorio, ubicacion)
        vm.cambiarLatitud("91")
        vm.cambiarLongitud("abc")
        vm.buscar()
        advanceUntilIdle()

        val estado = vm.uiState.value
        assertTrue(estado.faltaRubro)
        assertTrue(estado.latitudInvalida)
        assertTrue(estado.longitudInvalida)
        assertEquals(EstadoResultados.Inicial, estado.resultados)
        assertTrue(repositorio.busquedas.isEmpty())
    }

    @Test
    fun `buscar pasa de cargando a exito`() = runTest(dispatcher) {
        val vm = viewModelListo()
        vm.buscar()
        advanceUntilIdle()
        assertEquals(EstadoResultados.Cargando, vm.uiState.value.resultados)

        repositorio.busquedas.single().complete(Datos(listOf(unPrestador(7)), desdeCache = false))
        advanceUntilIdle()
        assertEquals(EstadoResultados.Exito(listOf(unPrestador(7)), desdeCache = false), vm.uiState.value.resultados)
    }

    @Test
    fun `sin resultados muestra vacio y un fallo muestra error`() = runTest(dispatcher) {
        val vm = viewModelListo()
        vm.buscar()
        advanceUntilIdle()
        repositorio.busquedas[0].complete(Datos(emptyList(), desdeCache = false))
        advanceUntilIdle()
        assertEquals(EstadoResultados.Vacio, vm.uiState.value.resultados)

        vm.buscar()
        advanceUntilIdle()
        repositorio.busquedas[1].completeExceptionally(ErrorDatos(TipoError.SERVIDOR))
        advanceUntilIdle()
        assertEquals(EstadoResultados.Error(TipoError.SERVIDOR), vm.uiState.value.resultados)
    }

    @Test
    fun `una respuesta antigua no reemplaza a la busqueda nueva`() = runTest(dispatcher) {
        val vm = viewModelListo()
        vm.buscar()
        advanceUntilIdle()
        vm.buscar()
        advanceUntilIdle()
        assertEquals(2, repositorio.busquedas.size)

        repositorio.busquedas[1].complete(Datos(listOf(unPrestador(2, "Nueva")), desdeCache = false))
        advanceUntilIdle()
        repositorio.busquedas[0].complete(Datos(listOf(unPrestador(1, "Antigua")), desdeCache = false))
        advanceUntilIdle()

        assertEquals(EstadoResultados.Exito(listOf(unPrestador(2, "Nueva")), desdeCache = false), vm.uiState.value.resultados)
    }

    @Test
    fun `usar mi ubicacion rellena las coordenadas o avisa si no hay`() = runTest(dispatcher) {
        val vm = BusquedaViewModel(repositorio, ubicacion)
        vm.usarMiUbicacion()
        advanceUntilIdle()
        assertEquals("-12.050000", vm.uiState.value.latitud)
        assertEquals("-77.040000", vm.uiState.value.longitud)
        assertFalse(vm.uiState.value.ubicacionNoDisponible)

        ubicacion.valor = null
        vm.usarMiUbicacion()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.ubicacionNoDisponible)
        assertEquals("-12.050000", vm.uiState.value.latitud)
    }

    @Test
    fun `los favoritos del repositorio se reflejan en el estado`() = runTest(dispatcher) {
        val vm = BusquedaViewModel(repositorio, ubicacion)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.favoritos.isEmpty())
        vm.alternarFavorito(7)
        advanceUntilIdle()
        assertEquals(setOf(7), vm.uiState.value.favoritos)
    }

    @Test
    fun `tocar el mapa fija la ubicacion y un punto fuera de rango se ignora`() = runTest(dispatcher) {
        val vm = BusquedaViewModel(repositorio, ubicacion)
        assertEquals(null, vm.uiState.value.ubicacion)

        vm.elegirUbicacion(-12.0463741, -77.0427934)
        assertEquals("-12.046374", vm.uiState.value.latitud)
        assertEquals("-77.042793", vm.uiState.value.longitud)
        assertEquals(Coordenadas(-12.046374, -77.042793), vm.uiState.value.ubicacion)

        vm.elegirUbicacion(95.0, 10.0)
        assertEquals("-12.046374", vm.uiState.value.latitud)

        vm.cambiarLatitud("abc")
        assertEquals(null, vm.uiState.value.ubicacion)
    }
}
