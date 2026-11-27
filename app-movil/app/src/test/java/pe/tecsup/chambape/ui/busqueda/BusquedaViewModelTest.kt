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
import pe.tecsup.chambape.data.local.AlmacenDireccionEnMemoria
import pe.tecsup.chambape.data.repository.ContextoBusqueda
import pe.tecsup.chambape.device.Direccion
import pe.tecsup.chambape.domain.model.Datos
import pe.tecsup.chambape.domain.model.ErrorDatos
import pe.tecsup.chambape.domain.model.Prestador
import pe.tecsup.chambape.domain.model.Recomendacion
import pe.tecsup.chambape.domain.model.Rubro
import pe.tecsup.chambape.domain.model.TipoError

private fun unPrestador(id: Int, nombre: String = "Ana") =
    Prestador(id, nombre, "Pérez", listOf("Gasfitería"), -12.046, -77.043, 1.5, favorito = false)

/** Repositorio falso: cada búsqueda espera a que la prueba la complete. */
private class RepositorioFalso : PrestadorRepository {
    var rubros: () -> Datos<List<Rubro>> = { Datos(listOf(Rubro(1, "Gasfitería")), desdeCache = false) }
    val busquedas = mutableListOf<CompletableDeferred<Datos<List<Prestador>>>>()
    val favoritosFlow = MutableStateFlow<List<Prestador>>(emptyList())
    var recomendacion: Recomendacion? = null
    var pedidasRecomendacion = 0

    override suspend fun recomendar(rubroId: Int, latitud: Double, longitud: Double): Recomendacion? {
        pedidasRecomendacion++
        return recomendacion
    }

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

private val CASA = Direccion("Av. Arequipa 1200", "Lince, Lima", Coordenadas(-12.08, -77.03))
private val OFICINA = Direccion("Jr. de la Unión 500", "Cercado de Lima", Coordenadas(-12.05, -77.03))

@OptIn(ExperimentalCoroutinesApi::class)
class BusquedaViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repositorio = RepositorioFalso()

    @Before
    fun preparar() = Dispatchers.setMain(dispatcher)

    @After
    fun limpiar() = Dispatchers.resetMain()

    private fun viewModelListo() = BusquedaViewModel(repositorio).apply {
        elegirDireccion(CASA)
        seleccionarRubro(1)
    }

    @Test
    fun `carga los rubros al iniciar`() = runTest(dispatcher) {
        val vm = BusquedaViewModel(repositorio)
        assertEquals(EstadoRubros.Cargando, vm.uiState.value.rubros)
        advanceUntilIdle()
        assertEquals(EstadoRubros.Listo(listOf(Rubro(1, "Gasfitería")), desdeCache = false), vm.uiState.value.rubros)
    }

    @Test
    fun `sin conexion al cargar rubros muestra error y permite reintentar`() = runTest(dispatcher) {
        repositorio.rubros = { throw ErrorDatos(TipoError.SIN_CONEXION) }
        val vm = BusquedaViewModel(repositorio)
        advanceUntilIdle()
        assertEquals(EstadoRubros.Error(TipoError.SIN_CONEXION), vm.uiState.value.rubros)

        repositorio.rubros = { Datos(listOf(Rubro(2, "Electricidad")), desdeCache = true) }
        vm.cargarRubros()
        advanceUntilIdle()
        assertEquals(EstadoRubros.Listo(listOf(Rubro(2, "Electricidad")), desdeCache = true), vm.uiState.value.rubros)
    }

    @Test
    fun `sin rubro ni direccion no llama al repositorio y dice que falta`() = runTest(dispatcher) {
        val vm = BusquedaViewModel(repositorio)
        vm.buscar()
        advanceUntilIdle()

        val estado = vm.uiState.value
        assertTrue(estado.faltaRubro)
        assertTrue(estado.faltaDireccion)
        assertEquals(EstadoResultados.Inicial, estado.resultados)
        assertTrue(repositorio.busquedas.isEmpty())
    }

    @Test
    fun `elegir rubro sin direccion no busca y elegir la direccion despues si`() = runTest(dispatcher) {
        val vm = BusquedaViewModel(repositorio)
        vm.seleccionarRubro(1)
        advanceUntilIdle()
        assertTrue(repositorio.busquedas.isEmpty())

        vm.elegirDireccion(CASA)
        advanceUntilIdle()
        assertEquals(1, repositorio.busquedas.size)
        assertEquals(EstadoResultados.Cargando, vm.uiState.value.resultados)
    }

    @Test
    fun `con rubro y direccion busca solo y pasa de cargando a exito`() = runTest(dispatcher) {
        val vm = viewModelListo()
        advanceUntilIdle()
        assertEquals(EstadoResultados.Cargando, vm.uiState.value.resultados)

        repositorio.busquedas.single().complete(Datos(listOf(unPrestador(7)), desdeCache = false))
        advanceUntilIdle()
        assertEquals(EstadoResultados.Exito(listOf(unPrestador(7)), desdeCache = false), vm.uiState.value.resultados)
    }

    @Test
    fun `sin resultados muestra vacio y un fallo muestra error`() = runTest(dispatcher) {
        val vm = viewModelListo()
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
        advanceUntilIdle()
        vm.elegirDireccion(OFICINA)
        advanceUntilIdle()
        assertEquals(2, repositorio.busquedas.size)

        repositorio.busquedas[1].complete(Datos(listOf(unPrestador(2, "Nueva")), desdeCache = false))
        advanceUntilIdle()
        repositorio.busquedas[0].complete(Datos(listOf(unPrestador(1, "Antigua")), desdeCache = false))
        advanceUntilIdle()

        assertEquals(EstadoResultados.Exito(listOf(unPrestador(2, "Nueva")), desdeCache = false), vm.uiState.value.resultados)
    }

    @Test
    fun `empieza con mi direccion y la sigue mientras no se elija otra`() = runTest(dispatcher) {
        val almacen = AlmacenDireccionEnMemoria(CASA)
        val vm = BusquedaViewModel(repositorio, almacen = almacen)
        advanceUntilIdle()
        assertEquals(CASA, vm.uiState.value.direccion)
        assertTrue(vm.uiState.value.usaDireccionGuardada)

        almacen.guardar(OFICINA)
        advanceUntilIdle()
        assertEquals(OFICINA, vm.uiState.value.direccion)

        // Elegida aquí, ya no la pisa un cambio en Perfil.
        vm.elegirDireccion(CASA)
        almacen.guardar(Direccion("Otra", "", Coordenadas(-12.1, -77.0)))
        advanceUntilIdle()
        assertEquals(CASA, vm.uiState.value.direccion)
        assertFalse(vm.uiState.value.usaDireccionGuardada)
    }

    @Test
    fun `los favoritos del repositorio se reflejan en el estado`() = runTest(dispatcher) {
        val vm = BusquedaViewModel(repositorio)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.favoritos.isEmpty())
        vm.alternarFavorito(7)
        advanceUntilIdle()
        assertEquals(setOf(7), vm.uiState.value.favoritos)
    }

    @Test
    fun `una direccion con coordenadas fuera de rango se ignora`() = runTest(dispatcher) {
        val vm = BusquedaViewModel(repositorio)
        vm.elegirDireccion(Direccion("Mala", "", Coordenadas(95.0, 10.0)))
        assertEquals(null, vm.uiState.value.direccion)

        vm.elegirDireccion(CASA)
        assertEquals(Coordenadas(-12.08, -77.03), vm.uiState.value.ubicacion)
    }

    @Test
    fun `la solicitud recibe el rubro, el punto y la direccion que vio el cliente`() = runTest(dispatcher) {
        val contexto = ContextoBusqueda()
        val vm = BusquedaViewModel(repositorio, contexto)
        vm.elegirDireccion(CASA)
        vm.seleccionarRubro(1)
        advanceUntilIdle()
        assertEquals(ContextoBusqueda.Datos(1, -12.08, -77.03, "Av. Arequipa 1200, Lince"), contexto.ultima)

        // Un punto sin nombre de calle viaja sin texto: la pantalla muestra «Punto marcado en el mapa».
        vm.elegirDireccion(Direccion("", "", Coordenadas(-12.1, -77.0)))
        advanceUntilIdle()
        assertEquals("", contexto.ultima?.direccion)
    }

    @Test
    fun `el lugar elegido en una seccion lo tienen inicio y las demas`() = runTest(dispatcher) {
        val sesion = kotlinx.coroutines.flow.MutableStateFlow<Direccion?>(null)
        val almacen = AlmacenDireccionEnMemoria(CASA)
        val urgente = BusquedaViewModel(repositorio, almacen = almacen, ubicacionSesion = sesion)
        advanceUntilIdle()
        assertEquals(CASA, urgente.uiState.value.direccion)
        urgente.elegirDireccion(OFICINA)
        assertEquals(OFICINA, sesion.value)

        // Otra sección (o Inicio) que se abre después empieza con el lugar elegido, no con «Mi dirección».
        val hogar = BusquedaViewModel(repositorio, almacen = almacen, ubicacionSesion = sesion)
        advanceUntilIdle()
        assertEquals(OFICINA, hogar.uiState.value.direccion)
    }

    @Test
    fun `la recomendacion llega despues de los resultados y solo si es uno de ellos`() = runTest(dispatcher) {
        repositorio.recomendacion = Recomendacion(7, "Es el más cercano.", 90.2, 12)
        val vm = viewModelListo()
        advanceUntilIdle()
        repositorio.busquedas.single().complete(Datos(listOf(unPrestador(7), unPrestador(8)), desdeCache = false))
        advanceUntilIdle()
        assertEquals(7, vm.uiState.value.recomendacion?.prestadorId)

        // Una búsqueda nueva la borra; si el recomendado no está en la lista, no se muestra.
        repositorio.recomendacion = Recomendacion(99, "Otro.", 50.0, 30)
        vm.buscar()
        assertEquals(null, vm.uiState.value.recomendacion)
        advanceUntilIdle()
        repositorio.busquedas[1].complete(Datos(listOf(unPrestador(7)), desdeCache = false))
        advanceUntilIdle()
        assertEquals(null, vm.uiState.value.recomendacion)
    }

    @Test
    fun `sin conexion o sin resultados no se pide recomendacion`() = runTest(dispatcher) {
        val vm = viewModelListo()
        advanceUntilIdle()
        repositorio.busquedas.single().complete(Datos(listOf(unPrestador(7)), desdeCache = true))
        advanceUntilIdle()
        vm.buscar()
        advanceUntilIdle()
        repositorio.busquedas[1].complete(Datos(emptyList(), desdeCache = false))
        advanceUntilIdle()
        assertEquals(0, repositorio.pedidasRecomendacion)
    }
}
