package pe.tecsup.chambape.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pe.tecsup.chambape.R
import pe.tecsup.chambape.data.auth.AuthRepository
import pe.tecsup.chambape.data.repository.PrestadorRepository
import pe.tecsup.chambape.data.repository.RegistroPrestadorRepository
import pe.tecsup.chambape.device.Coordenadas
import pe.tecsup.chambape.domain.model.DatosRegistroPrestador
import pe.tecsup.chambape.domain.model.ErrorAuth
import pe.tecsup.chambape.domain.model.ErrorDatos
import pe.tecsup.chambape.domain.model.ErrorRegistro
import pe.tecsup.chambape.domain.model.Rubro
import pe.tecsup.chambape.domain.model.TipoError
import pe.tecsup.chambape.domain.model.TipoErrorAuth
import pe.tecsup.chambape.ui.busqueda.MapaBusqueda

/** Mensaje de error de un campo: validado en la app (recurso) o devuelto por el servidor (texto). */
sealed interface MensajeCampo {
    data class Recurso(@StringRes val id: Int) : MensajeCampo
    data class Texto(val texto: String) : MensajeCampo
}

/** Nombres de campo: los mismos que usa la API, para colocar sus errores en el sitio correcto. */
object Campo {
    const val CORREO = "email"
    const val CLAVE = "password"
    const val NOMBRES = "nombres"
    const val APELLIDOS = "apellidos"
    const val DNI = "dni"
    const val TELEFONO = "telefono"
    const val RUBROS = "rubros"
    const val UBICACION = "latitud"
    const val LONGITUD = "longitud"
    const val RADIO = "radio_cobertura_km"
    const val GENERAL = "detail"
}

data class RegistroPrestadorUiState(
    val correo: String = "",
    val clave: String = "",
    val nombres: String = "",
    val apellidos: String = "",
    val dni: String = "",
    val telefono: String = "",
    val rubrosDisponibles: List<Rubro>? = null,
    val errorRubros: TipoError? = null,
    val rubros: Set<Int> = emptySet(),
    val ubicacion: Coordenadas? = null,
    val radioKm: Int = 5,
    val errores: Map<String, MensajeCampo> = emptyMap(),
    val enviando: Boolean = false,
    val errorGeneral: MensajeCampo? = null,
)

class RegistroPrestadorViewModel(
    private val auth: AuthRepository,
    private val registro: RegistroPrestadorRepository,
    private val prestadores: PrestadorRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RegistroPrestadorUiState())
    val uiState: StateFlow<RegistroPrestadorUiState> = _uiState.asStateFlow()

    init {
        cargarRubros()
    }

    fun cargarRubros() {
        _uiState.update { it.copy(errorRubros = null) }
        viewModelScope.launch {
            _uiState.update {
                try {
                    it.copy(rubrosDisponibles = prestadores.rubros().valor)
                } catch (e: ErrorDatos) {
                    it.copy(errorRubros = e.tipo)
                }
            }
        }
    }

    fun cambiar(campo: String, valor: String) = _uiState.update {
        when (campo) {
            Campo.CORREO -> it.copy(correo = valor)
            Campo.CLAVE -> it.copy(clave = valor)
            Campo.NOMBRES -> it.copy(nombres = valor)
            Campo.APELLIDOS -> it.copy(apellidos = valor)
            Campo.DNI -> it.copy(dni = valor.filter(Char::isDigit).take(8))
            Campo.TELEFONO -> it.copy(telefono = valor.take(20))
            else -> it
        }.copy(errores = it.errores - campo, errorGeneral = null)
    }

    fun alternarRubro(id: Int) = _uiState.update {
        it.copy(rubros = if (id in it.rubros) it.rubros - id else it.rubros + id, errores = it.errores - Campo.RUBROS)
    }

    fun elegirUbicacion(latitud: Double, longitud: Double) = _uiState.update {
        it.copy(
            ubicacion = Coordenadas(latitud, longitud),
            errores = it.errores - Campo.UBICACION - Campo.LONGITUD,
        )
    }

    fun cambiarRadio(km: Int) = _uiState.update { it.copy(radioKm = km.coerceIn(RADIO_MINIMO, RADIO_MAXIMO)) }

    fun registrar() {
        val s = _uiState.value
        if (s.enviando) return
        val errores = validar(s)
        val ubicacion = s.ubicacion
        if (errores.isNotEmpty() || ubicacion == null) {
            // Con datos inválidos no se crea ninguna cuenta.
            _uiState.update { it.copy(errores = errores, errorGeneral = REVISA_CAMPOS) }
            return
        }
        val datos = DatosRegistroPrestador(
            nombres = s.nombres, apellidos = s.apellidos, dni = s.dni, telefono = s.telefono,
            rubros = s.rubros, latitud = ubicacion.latitud, longitud = ubicacion.longitud, radioKm = s.radioKm,
        )
        _uiState.update { it.copy(enviando = true, errores = emptyMap(), errorGeneral = null) }
        viewModelScope.launch {
            var errores2: Map<String, MensajeCampo> = emptyMap()
            var general: MensajeCampo? = null
            try {
                auth.registrarPrestador("${s.nombres.trim()} ${s.apellidos.trim()}", s.correo, s.clave) { token ->
                    registro.registrar(token, datos)
                }
            } catch (e: ErrorRegistro) {
                // "detail" no pertenece a ningún campo: se muestra como error general.
                errores2 = e.campos.filterKeys { it != Campo.GENERAL }.mapValues { MensajeCampo.Texto(it.value) }
                general = e.campos[Campo.GENERAL]?.let { MensajeCampo.Texto(it) } ?: REVISA_CAMPOS
            } catch (e: ErrorDatos) {
                general = MensajeCampo.Recurso(recursoDe(e.tipo))
            } catch (e: ErrorAuth) {
                when (e.tipo) {
                    TipoErrorAuth.CORREO_EN_USO -> errores2 = mapOf(Campo.CORREO to MensajeCampo.Recurso(R.string.error_correo_en_uso))
                    TipoErrorAuth.CORREO_INVALIDO -> errores2 = mapOf(Campo.CORREO to MensajeCampo.Recurso(R.string.error_correo_invalido))
                    TipoErrorAuth.CLAVE_DEBIL -> errores2 = mapOf(Campo.CLAVE to MensajeCampo.Recurso(R.string.error_clave_debil))
                    TipoErrorAuth.SIN_CONEXION -> general = MensajeCampo.Recurso(R.string.error_sin_conexion)
                    else -> general = MensajeCampo.Recurso(R.string.error_desconocido)
                }
                if (errores2.isNotEmpty()) general = REVISA_CAMPOS
            }
            _uiState.update { it.copy(enviando = false, errores = errores2, errorGeneral = general) }
        }
    }

    private fun validar(s: RegistroPrestadorUiState): Map<String, MensajeCampo> = buildMap {
        fun error(campo: String, @StringRes id: Int) = put(campo, MensajeCampo.Recurso(id))
        if (s.correo.isBlank()) error(Campo.CORREO, R.string.error_obligatorio)
        if (s.clave.length < AuthViewModel.CLAVE_MINIMA) error(Campo.CLAVE, R.string.error_clave_minima)
        if (s.nombres.isBlank()) error(Campo.NOMBRES, R.string.error_obligatorio)
        if (s.apellidos.isBlank()) error(Campo.APELLIDOS, R.string.error_obligatorio)
        if (s.dni.length != 8) error(Campo.DNI, R.string.error_dni)
        if (s.rubros.isEmpty()) error(Campo.RUBROS, R.string.error_rubros)
        if (s.ubicacion == null) error(Campo.UBICACION, R.string.error_ubicacion_cobertura)
    }

    private fun recursoDe(tipo: TipoError) = when (tipo) {
        TipoError.SIN_CONEXION -> R.string.error_sin_conexion
        TipoError.SERVIDOR -> R.string.error_servidor
        TipoError.DATOS_INVALIDOS -> R.string.error_datos_invalidos
        TipoError.CUENTA_NO_VINCULADA -> R.string.error_cuenta_no_vinculada
    }

    companion object {
        const val RADIO_MINIMO = 1
        const val RADIO_MAXIMO = 50
        private val REVISA_CAMPOS = MensajeCampo.Recurso(R.string.registro_revisa_campos)
    }
}

@Composable
private fun MensajeCampo.texto(): String = when (this) {
    is MensajeCampo.Recurso -> stringResource(id)
    is MensajeCampo.Texto -> texto
}

@Composable
private fun CampoTexto(
    estado: RegistroPrestadorUiState,
    campo: String,
    valor: String,
    @StringRes etiqueta: Int,
    onCambio: (String, String) -> Unit,
    teclado: KeyboardOptions = KeyboardOptions.Default,
    oculto: Boolean = false,
) {
    val error = estado.errores[campo]
    OutlinedTextField(
        value = valor,
        onValueChange = { onCambio(campo, it) },
        label = { Text(stringResource(etiqueta)) },
        isError = error != null,
        supportingText = if (error != null) ({ Text(error.texto()) }) else null,
        singleLine = true,
        enabled = !estado.enviando,
        keyboardOptions = teclado,
        visualTransformation = if (oculto) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ErrorDe(estado: RegistroPrestadorUiState, campo: String) {
    estado.errores[campo]?.let { Text(it.texto(), color = MaterialTheme.colorScheme.error) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RegistroPrestadorScreen(onVolver: () -> Unit) {
    val vm = chambaViewModel {
        RegistroPrestadorViewModel(it.authRepository, it.registroPrestadorRepository, it.prestadorRepository)
    }
    val estado by vm.uiState.collectAsStateWithLifecycle()
    val habilitado = !estado.enviando
    val palabras = KeyboardOptions(capitalization = KeyboardCapitalization.Words)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.registro_prestador_titulo), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.registro_prestador_ayuda), style = MaterialTheme.typography.bodyMedium)

        Text(stringResource(R.string.registro_seccion_cuenta), style = MaterialTheme.typography.titleMedium)
        CampoTexto(estado, Campo.CORREO, estado.correo, R.string.correo, vm::cambiar, KeyboardOptions(keyboardType = KeyboardType.Email))
        CampoTexto(estado, Campo.CLAVE, estado.clave, R.string.clave, vm::cambiar, KeyboardOptions(keyboardType = KeyboardType.Password), oculto = true)

        Text(stringResource(R.string.registro_seccion_datos), style = MaterialTheme.typography.titleMedium)
        CampoTexto(estado, Campo.NOMBRES, estado.nombres, R.string.nombres, vm::cambiar, palabras)
        CampoTexto(estado, Campo.APELLIDOS, estado.apellidos, R.string.apellidos, vm::cambiar, palabras)
        CampoTexto(estado, Campo.DNI, estado.dni, R.string.dni, vm::cambiar, KeyboardOptions(keyboardType = KeyboardType.Number))
        CampoTexto(estado, Campo.TELEFONO, estado.telefono, R.string.telefono_opcional, vm::cambiar, KeyboardOptions(keyboardType = KeyboardType.Phone))

        Text(stringResource(R.string.registro_seccion_rubros), style = MaterialTheme.typography.titleMedium)
        val rubros = estado.rubrosDisponibles
        val errorRubros = estado.errorRubros
        when {
            rubros != null -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rubros.forEach { rubro ->
                    FilterChip(
                        selected = rubro.id in estado.rubros,
                        onClick = { vm.alternarRubro(rubro.id) },
                        enabled = habilitado,
                        label = { Text(rubro.nombre) },
                    )
                }
            }
            errorRubros != null -> Mensaje(
                textoError(errorRubros),
                textoBoton = stringResource(R.string.reintentar),
                onBoton = vm::cargarRubros,
            )
            else -> Cargando()
        }
        ErrorDe(estado, Campo.RUBROS)

        Text(stringResource(R.string.registro_seccion_cobertura), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.registro_cobertura_ayuda), style = MaterialTheme.typography.bodyMedium)
        MapaBusqueda(
            ubicacion = estado.ubicacion,
            prestadores = emptyList(),
            encuadre = 0,
            onElegirUbicacion = vm::elegirUbicacion,
            radioKm = estado.radioKm,
        )
        ErrorDe(estado, Campo.UBICACION)
        ErrorDe(estado, Campo.LONGITUD)
        Text(stringResource(R.string.registro_radio, estado.radioKm))
        Slider(
            value = estado.radioKm.toFloat(),
            onValueChange = { vm.cambiarRadio(it.toInt()) },
            valueRange = RegistroPrestadorViewModel.RADIO_MINIMO.toFloat()..RegistroPrestadorViewModel.RADIO_MAXIMO.toFloat(),
            enabled = habilitado,
        )
        ErrorDe(estado, Campo.RADIO)

        estado.errorGeneral?.let {
            Text(
                it.texto(),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
        }
        Button(onClick = vm::registrar, enabled = habilitado, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (estado.enviando) R.string.registrando else R.string.registro_prestador_boton))
        }
        TextButton(onClick = onVolver, enabled = habilitado) { Text(stringResource(R.string.ya_tengo_cuenta)) }
    }
}
