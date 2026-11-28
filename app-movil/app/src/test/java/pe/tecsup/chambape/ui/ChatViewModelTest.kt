package pe.tecsup.chambape.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pe.tecsup.chambape.data.repository.ChatRepository
import pe.tecsup.chambape.domain.model.Chat
import pe.tecsup.chambape.domain.model.ErrorDatos
import pe.tecsup.chambape.domain.model.MensajeChat
import pe.tecsup.chambape.domain.model.TipoError
import java.time.ZoneId

private class ChatFalso : ChatRepository {
    var chat = Chat(41, "Confirmado", true, "Ana Pérez", "987654321", listOf(MensajeChat(1, false, "Hola", "2026-11-28T15:01:00Z")))
    var errorConsulta: TipoError? = null
    var errorEnvio: TipoError? = null
    var consultas = 0
    val enviados = mutableListOf<String>()

    override suspend fun consultar(solicitudId: Int): Chat {
        consultas++
        errorConsulta?.let { throw ErrorDatos(it) }
        return chat
    }

    override suspend fun enviar(solicitudId: Int, texto: String): MensajeChat {
        enviados += texto
        errorEnvio?.let { throw ErrorDatos(it) }
        val mensaje = MensajeChat(chat.mensajes.size + 1, true, texto, "2026-11-28T15:05:00Z")
        chat = chat.copy(mensajes = chat.mensajes + mensaje)
        return mensaje
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repo = ChatFalso()

    @Before
    fun preparar() = Dispatchers.setMain(dispatcher)

    @After
    fun limpiar() = Dispatchers.resetMain()

    @Test
    fun consulta_al_abrir_y_luego_cada_5_segundos() = runTest(dispatcher) {
        val vm = ChatViewModel(41, repo)
        runCurrent()
        assertEquals(1, repo.consultas)
        assertEquals("Ana Pérez", vm.uiState.value.chat?.contraparte)

        repo.chat = repo.chat.copy(mensajes = repo.chat.mensajes + MensajeChat(2, false, "Llego en 20", "x"))
        advanceTimeBy(5_001)
        assertEquals(2, repo.consultas)
        assertEquals(2, vm.uiState.value.chat?.mensajes?.size)
        vm.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun un_fallo_no_corta_la_consulta_ni_borra_lo_que_se_ve() = runTest(dispatcher) {
        val vm = ChatViewModel(41, repo)
        runCurrent()
        repo.errorConsulta = TipoError.SIN_CONEXION
        advanceTimeBy(5_001)
        assertEquals(TipoError.SIN_CONEXION, vm.uiState.value.error)
        assertEquals(1, vm.uiState.value.chat?.mensajes?.size)

        repo.errorConsulta = null
        advanceTimeBy(5_001)
        assertNull(vm.uiState.value.error)
        vm.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun envia_y_limpia_la_caja() = runTest(dispatcher) {
        val vm = ChatViewModel(41, repo)
        runCurrent()
        vm.cambiarTexto("   ")
        vm.enviar()
        runCurrent()
        assertTrue(repo.enviados.isEmpty())

        vm.cambiarTexto("¿A qué hora?")
        vm.enviar()
        assertTrue(vm.uiState.value.enviando)
        runCurrent()
        assertEquals(listOf("¿A qué hora?"), repo.enviados)
        assertEquals("", vm.uiState.value.texto)
        assertFalse(vm.uiState.value.enviando)
        assertEquals(listOf(false, true), vm.uiState.value.chat?.mensajes?.map { it.mio })
        vm.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun si_falla_el_envio_conserva_el_texto_y_actualiza_el_chat() = runTest(dispatcher) {
        val vm = ChatViewModel(41, repo)
        runCurrent()
        repo.errorEnvio = TipoError.SERVIDOR
        repo.chat = repo.chat.copy(activo = false, estadoTexto = "Finalizada") // finalizó mientras escribía
        vm.cambiarTexto("Gracias")
        vm.enviar()
        runCurrent()
        assertTrue(vm.uiState.value.errorEnvio)
        assertEquals("Gracias", vm.uiState.value.texto)
        assertEquals(false, vm.uiState.value.chat?.activo)
        // Ya en solo lectura no se envía nada más.
        vm.enviar()
        runCurrent()
        assertEquals(1, repo.enviados.size)
        vm.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun el_texto_se_corta_en_500() {
        val vm = ChatViewModel(41, repo)
        vm.cambiarTexto("x".repeat(600))
        assertEquals(500, vm.uiState.value.texto.length)
        vm.viewModelScope.coroutineContext.cancelChildren()
    }

    @Test
    fun hora_del_mensaje_en_la_zona_del_telefono() {
        val lima = ZoneId.of("America/Lima")
        assertEquals("10:05", horaMensaje("2026-11-28T15:05:00Z", lima))
        assertEquals("10:05", horaMensaje("2026-11-28T15:05:00.123456Z", lima))
        assertEquals("", horaMensaje("no es fecha", lima))
        assertEquals("", horaMensaje("", lima))
    }
}
