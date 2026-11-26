package pe.tecsup.chambape.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pe.tecsup.chambape.R
import pe.tecsup.chambape.data.auth.AuthRepository
import pe.tecsup.chambape.domain.model.ErrorAuth
import pe.tecsup.chambape.domain.model.EstadoSesion
import pe.tecsup.chambape.domain.model.TipoErrorAuth

private class AuthDePrueba : AuthRepository {
    override val sesion: StateFlow<EstadoSesion> = MutableStateFlow(EstadoSesion.SinSesion)
    var falla: TipoErrorAuth? = null
    val registrados = mutableListOf<Triple<String, String, String>>()

    override suspend fun ingresar(correo: String, clave: String) {
        falla?.let { throw ErrorAuth(it) }
    }

    override suspend fun registrarCliente(nombre: String, correo: String, clave: String) {
        falla?.let { throw ErrorAuth(it) }
        registrados += Triple(nombre, correo, clave)
    }

    override suspend fun registrarPrestador(
        nombre: String, correo: String, clave: String, registrarEnApi: suspend (token: String) -> Unit,
    ) = Unit

    override fun cerrarSesion() = Unit
    override suspend fun tokenDeAcceso(): String? = null
}

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val auth = AuthDePrueba()

    @Before
    fun preparar() = Dispatchers.setMain(dispatcher)

    @After
    fun limpiar() = Dispatchers.resetMain()

    @Test
    fun `registro de cliente en tres pasos con validacion por paso`() = runTest(dispatcher) {
        val vm = AuthViewModel(auth)
        vm.siguienteRegistro()
        assertEquals(R.string.error_nombre, vm.uiState.value.errorNombre)
        assertEquals(AuthViewModel.PASO_NOMBRE, vm.uiState.value.paso)

        vm.cambiarNombre("  Carla ")
        assertNull(vm.uiState.value.errorNombre) // escribir borra el error
        vm.siguienteRegistro()
        vm.cambiarCorreo("carla")
        vm.siguienteRegistro()
        assertEquals(R.string.error_correo_formato, vm.uiState.value.errorCorreo)

        vm.cambiarCorreo(" carla@example.com ")
        vm.siguienteRegistro()
        vm.cambiarClave("123")
        vm.siguienteRegistro()
        assertEquals(R.string.error_clave_minima, vm.uiState.value.errorClave)
        assertTrue(auth.registrados.isEmpty())

        vm.cambiarClave("secreta1")
        vm.siguienteRegistro()
        assertTrue(vm.uiState.value.cargando)
        advanceUntilIdle()
        assertEquals(listOf(Triple("Carla", "carla@example.com", "secreta1")), auth.registrados)
    }

    @Test
    fun `correo en uso vuelve al paso del correo con el error en el campo`() = runTest(dispatcher) {
        auth.falla = TipoErrorAuth.CORREO_EN_USO
        val vm = AuthViewModel(auth).apply {
            cambiarNombre("Carla"); siguienteRegistro()
            cambiarCorreo("carla@example.com"); siguienteRegistro()
            cambiarClave("secreta1"); siguienteRegistro()
        }
        advanceUntilIdle()
        assertEquals(AuthViewModel.PASO_CORREO, vm.uiState.value.paso)
        assertEquals(R.string.error_correo_en_uso, vm.uiState.value.errorCorreo)
        assertFalse(vm.uiState.value.cargando)
        assertEquals("secreta1", vm.uiState.value.clave)
    }

    @Test
    fun `sin conexion se muestra como error general y atras no sale del primer paso`() = runTest(dispatcher) {
        auth.falla = TipoErrorAuth.SIN_CONEXION
        val vm = AuthViewModel(auth)
        assertFalse(vm.atrasRegistro())
        vm.cambiarCorreo("carla@example.com")
        vm.cambiarClave("x")
        vm.ingresar()
        advanceUntilIdle()
        assertEquals(TipoErrorAuth.SIN_CONEXION, vm.uiState.value.error)
    }
}
