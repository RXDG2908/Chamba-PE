package pe.tecsup.chambape.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
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
import pe.tecsup.chambape.data.repository.PrestadorRepository
import pe.tecsup.chambape.data.repository.RegistroPrestadorRepository
import pe.tecsup.chambape.domain.model.Datos
import pe.tecsup.chambape.domain.model.DatosRegistroPrestador
import pe.tecsup.chambape.domain.model.ErrorAuth
import pe.tecsup.chambape.domain.model.ErrorRegistro
import pe.tecsup.chambape.domain.model.EstadoSesion
import pe.tecsup.chambape.domain.model.Prestador
import pe.tecsup.chambape.domain.model.Rubro
import pe.tecsup.chambape.domain.model.TipoErrorAuth

private class AuthFalso : AuthRepository {
    override val sesion: StateFlow<EstadoSesion> = MutableStateFlow(EstadoSesion.SinSesion)
    var errorAlCrearCuenta: TipoErrorAuth? = null
    val cuentasCreadas = mutableListOf<String>()
    val cuentasDeshechas = mutableListOf<String>()

    override suspend fun ingresar(correo: String, clave: String) = Unit
    override suspend fun registrarCliente(nombre: String, correo: String, clave: String) = Unit
    override fun cerrarSesion() = Unit
    override suspend fun tokenDeAcceso(): String? = "token"

    override suspend fun registrarPrestador(
        nombre: String,
        correo: String,
        clave: String,
        registrarEnApi: suspend (token: String) -> Unit,
    ) {
        errorAlCrearCuenta?.let { throw ErrorAuth(it) }
        cuentasCreadas += correo
        try {
            registrarEnApi("token-nuevo")
        } catch (e: Exception) {
            cuentasDeshechas += correo
            throw e
        }
    }
}

private class RegistroFalso : RegistroPrestadorRepository {
    var rechazo: Map<String, String>? = null
    val recibidos = mutableListOf<Pair<String, DatosRegistroPrestador>>()

    override suspend fun registrar(token: String, datos: DatosRegistroPrestador) {
        recibidos += token to datos
        rechazo?.let { throw ErrorRegistro(it) }
    }
}

private class RubrosFalsos : PrestadorRepository {
    override suspend fun rubros() = Datos(listOf(Rubro(1, "Gasfitería"), Rubro(2, "Electricidad")), desdeCache = false)
    override suspend fun buscar(rubroId: Int, latitud: Double, longitud: Double) = Datos(emptyList<Prestador>(), false)
    override suspend fun prestador(id: Int): Prestador? = null
    override fun favoritos(): Flow<List<Prestador>> = emptyFlow()
    override suspend fun alternarFavorito(id: Int) = false
}

@OptIn(ExperimentalCoroutinesApi::class)
class RegistroPrestadorViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val auth = AuthFalso()
    private val registro = RegistroFalso()

    @Before
    fun preparar() = Dispatchers.setMain(dispatcher)

    @After
    fun limpiar() = Dispatchers.resetMain()

    private fun viewModelCompleto() = RegistroPrestadorViewModel(auth, registro, RubrosFalsos()).apply {
        cambiar(Campo.CORREO, "ana@example.com")
        cambiar(Campo.CLAVE, "secreta1")
        cambiar(Campo.NOMBRES, " Ana ")
        cambiar(Campo.APELLIDOS, "Pérez")
        cambiar(Campo.DNI, "12345678")
        alternarRubro(2)
        elegirUbicacion(-12.05, -77.04)
        cambiarRadio(8)
    }

    @Test
    fun `con datos incompletos marca los campos y no crea ninguna cuenta`() = runTest(dispatcher) {
        val vm = RegistroPrestadorViewModel(auth, registro, RubrosFalsos())
        vm.cambiar(Campo.DNI, "12a34")
        vm.registrar()
        advanceUntilIdle()

        val errores = vm.uiState.value.errores
        assertEquals(
            setOf(Campo.CORREO, Campo.CLAVE, Campo.NOMBRES, Campo.APELLIDOS, Campo.DNI, Campo.RUBROS, Campo.UBICACION),
            errores.keys,
        )
        assertEquals("1234", vm.uiState.value.dni) // solo dígitos
        assertTrue(auth.cuentasCreadas.isEmpty())
        assertTrue(registro.recibidos.isEmpty())
    }

    @Test
    fun `con datos validos crea la cuenta y registra al prestador con su token`() = runTest(dispatcher) {
        val vm = viewModelCompleto()
        vm.registrar()
        assertTrue(vm.uiState.value.enviando)
        advanceUntilIdle()

        assertEquals(listOf("ana@example.com"), auth.cuentasCreadas)
        val (token, datos) = registro.recibidos.single()
        assertEquals("token-nuevo", token)
        assertEquals(
            DatosRegistroPrestador(" Ana ", "Pérez", "12345678", "", setOf(2), -12.05, -77.04, 8),
            datos,
        )
        assertFalse(vm.uiState.value.enviando)
        assertNull(vm.uiState.value.errorGeneral)
    }

    @Test
    fun `si el servidor rechaza un campo se muestra su mensaje y la cuenta se deshace`() = runTest(dispatcher) {
        registro.rechazo = mapOf("dni" to "Ya existe un prestador con este DNI.")
        val vm = viewModelCompleto()
        vm.registrar()
        advanceUntilIdle()

        assertEquals(MensajeCampo.Texto("Ya existe un prestador con este DNI."), vm.uiState.value.errores[Campo.DNI])
        assertEquals(listOf("ana@example.com"), auth.cuentasDeshechas)
        assertFalse(vm.uiState.value.enviando)
    }

    @Test
    fun `un rechazo general del servidor se muestra como error general`() = runTest(dispatcher) {
        registro.rechazo = mapOf("detail" to "Esta cuenta de la app ya está registrada.")
        val vm = viewModelCompleto()
        vm.registrar()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.errores.isEmpty())
        assertEquals(MensajeCampo.Texto("Esta cuenta de la app ya está registrada."), vm.uiState.value.errorGeneral)
    }

    @Test
    fun `correo ya usado en Firebase se marca en el campo y no llama a la API`() = runTest(dispatcher) {
        auth.errorAlCrearCuenta = TipoErrorAuth.CORREO_EN_USO
        val vm = viewModelCompleto()
        vm.registrar()
        advanceUntilIdle()

        assertEquals(MensajeCampo.Recurso(R.string.error_correo_en_uso), vm.uiState.value.errores[Campo.CORREO])
        assertTrue(registro.recibidos.isEmpty())
    }

    @Test
    fun `el radio se limita al rango permitido y los rubros se alternan`() = runTest(dispatcher) {
        val vm = RegistroPrestadorViewModel(auth, registro, RubrosFalsos())
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.rubrosDisponibles?.size)

        vm.cambiarRadio(500)
        assertEquals(RegistroPrestadorViewModel.RADIO_MAXIMO, vm.uiState.value.radioKm)
        vm.alternarRubro(1)
        vm.alternarRubro(2)
        vm.alternarRubro(1)
        assertEquals(setOf(2), vm.uiState.value.rubros)
    }

    @Test
    fun `cada paso valida solo sus campos y atras conserva lo escrito`() = runTest(dispatcher) {
        val vm = RegistroPrestadorViewModel(auth, registro, RubrosFalsos())
        vm.cambiar(Campo.CORREO, "ana@")
        vm.siguiente()
        assertEquals(RegistroPrestadorViewModel.PASO_CUENTA, vm.uiState.value.paso)
        assertEquals(setOf(Campo.CORREO, Campo.CLAVE), vm.uiState.value.errores.keys)
        assertEquals(MensajeCampo.Recurso(R.string.error_correo_formato), vm.uiState.value.errores[Campo.CORREO])

        vm.cambiar(Campo.CORREO, "ana@example.com")
        vm.cambiar(Campo.CLAVE, "secreta1")
        vm.siguiente()
        assertEquals(RegistroPrestadorViewModel.PASO_DATOS, vm.uiState.value.paso)
        assertTrue(vm.uiState.value.errores.isEmpty()) // los datos personales aún no se marcan

        vm.cambiar(Campo.TELEFONO, "98 765")
        vm.siguiente()
        assertEquals(setOf(Campo.NOMBRES, Campo.APELLIDOS, Campo.DNI, Campo.TELEFONO), vm.uiState.value.errores.keys)

        assertTrue(vm.atras())
        assertEquals(RegistroPrestadorViewModel.PASO_CUENTA, vm.uiState.value.paso)
        assertEquals("ana@example.com", vm.uiState.value.correo)
        assertEquals("98765", vm.uiState.value.telefono)
        assertFalse(vm.atras()) // en el primer paso, la pantalla sale del asistente
    }

    @Test
    fun `recorre los cinco pasos y envia desde la revision`() = runTest(dispatcher) {
        val vm = viewModelCompleto()
        repeat(RegistroPrestadorViewModel.PASO_REVISAR) { vm.siguiente() }
        assertEquals(RegistroPrestadorViewModel.PASO_REVISAR, vm.uiState.value.paso)
        assertTrue(auth.cuentasCreadas.isEmpty())

        vm.siguiente()
        advanceUntilIdle()
        assertEquals(listOf("ana@example.com"), auth.cuentasCreadas)
    }

    @Test
    fun `un error del servidor lleva al paso donde se corrige`() = runTest(dispatcher) {
        registro.rechazo = mapOf("dni" to "Ya existe un prestador con este DNI.")
        val vm = viewModelCompleto()
        vm.irAPaso(RegistroPrestadorViewModel.PASO_REVISAR)
        vm.siguiente()
        advanceUntilIdle()
        assertEquals(RegistroPrestadorViewModel.PASO_DATOS, vm.uiState.value.paso)

        auth.errorAlCrearCuenta = TipoErrorAuth.CORREO_EN_USO
        vm.irAPaso(RegistroPrestadorViewModel.PASO_REVISAR)
        vm.siguiente()
        advanceUntilIdle()
        assertEquals(RegistroPrestadorViewModel.PASO_CUENTA, vm.uiState.value.paso)
    }
}
