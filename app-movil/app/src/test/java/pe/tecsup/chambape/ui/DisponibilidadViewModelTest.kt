package pe.tecsup.chambape.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import pe.tecsup.chambape.data.repository.DisponibilidadRepository
import pe.tecsup.chambape.domain.model.Disponibilidad
import pe.tecsup.chambape.domain.model.ErrorDatos
import pe.tecsup.chambape.domain.model.EstadoVerificacion
import pe.tecsup.chambape.domain.model.TipoError

private class DisponibilidadFalsa(var actual: Disponibilidad) : DisponibilidadRepository {
    var errorAlConsultar: TipoError? = null
    var errorAlCambiar: TipoError? = null
    val cambios = mutableListOf<Boolean>()

    override suspend fun consultar(): Disponibilidad {
        errorAlConsultar?.let { throw ErrorDatos(it) }
        return actual
    }

    override suspend fun cambiar(disponible: Boolean): Disponibilidad {
        cambios += disponible
        errorAlCambiar?.let { throw ErrorDatos(it) }
        actual = actual.copy(disponible = disponible)
        return actual
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DisponibilidadViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repositorio = DisponibilidadFalsa(Disponibilidad(disponible = false, estado = EstadoVerificacion.VERIFICADO))

    @Before
    fun preparar() = Dispatchers.setMain(dispatcher)

    @After
    fun limpiar() = Dispatchers.resetMain()

    @Test
    fun `carga la disponibilidad y la cambia cuando el servidor confirma`() = runTest(dispatcher) {
        val vm = DisponibilidadViewModel(repositorio)
        assertTrue(vm.uiState.value.cargando)
        advanceUntilIdle()
        assertEquals(false, vm.uiState.value.datos?.disponible)

        vm.alternar()
        assertTrue(vm.uiState.value.guardando)
        // Mientras guarda, el interruptor conserva el valor anterior.
        assertEquals(false, vm.uiState.value.datos?.disponible)
        advanceUntilIdle()

        assertEquals(true, vm.uiState.value.datos?.disponible)
        assertFalse(vm.uiState.value.guardando)
        assertEquals(listOf(true), repositorio.cambios)
    }

    @Test
    fun `si el cambio falla se conserva el valor anterior y se avisa`() = runTest(dispatcher) {
        val vm = DisponibilidadViewModel(repositorio)
        advanceUntilIdle()
        repositorio.errorAlCambiar = TipoError.SIN_CONEXION

        vm.alternar()
        advanceUntilIdle()

        assertEquals(false, vm.uiState.value.datos?.disponible)
        assertTrue(vm.uiState.value.errorGuardado)
    }

    @Test
    fun `cuenta sin vincular muestra el error y permite reintentar`() = runTest(dispatcher) {
        repositorio.errorAlConsultar = TipoError.CUENTA_NO_VINCULADA
        val vm = DisponibilidadViewModel(repositorio)
        advanceUntilIdle()
        assertEquals(TipoError.CUENTA_NO_VINCULADA, vm.uiState.value.errorCarga)
        assertEquals(null, vm.uiState.value.datos)

        repositorio.errorAlConsultar = null
        vm.cargar()
        advanceUntilIdle()
        assertEquals(null, vm.uiState.value.errorCarga)
        assertEquals(false, vm.uiState.value.datos?.disponible)
    }

    @Test
    fun `un segundo toque mientras guarda no envia otro cambio`() = runTest(dispatcher) {
        val vm = DisponibilidadViewModel(repositorio)
        advanceUntilIdle()
        vm.alternar()
        vm.alternar()
        advanceUntilIdle()
        assertEquals(listOf(true), repositorio.cambios)
    }
}
