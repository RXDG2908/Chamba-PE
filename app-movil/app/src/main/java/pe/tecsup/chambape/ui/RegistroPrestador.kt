package pe.tecsup.chambape.ui

import pe.tecsup.chambape.ui.busqueda.HojaUbicacion
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
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
import kotlin.math.roundToInt

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
    /** Paso del asistente que se ve (ver [RegistroPrestadorViewModel.PASOS]). */
    val paso: Int = 0,
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
            Campo.TELEFONO -> it.copy(telefono = valor.filter(Char::isDigit).take(TELEFONO_DIGITOS))
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

    /** Valida solo el paso actual y avanza; en el último, envía el registro. */
    fun siguiente() {
        val s = _uiState.value
        if (s.enviando) return
        if (s.paso >= PASO_REVISAR) return registrar()
        val errores = validar(s).filterKeys { pasoDe(it) == s.paso }
        _uiState.update {
            if (errores.isEmpty()) it.copy(paso = it.paso + 1, errores = emptyMap(), errorGeneral = null)
            else it.copy(errores = errores)
        }
    }

    /** Retrocede un paso sin perder lo escrito; devuelve false si ya está en el primero. */
    fun atras(): Boolean {
        val s = _uiState.value
        if (s.enviando || s.paso == 0) return false
        _uiState.update { it.copy(paso = it.paso - 1, errorGeneral = null) }
        return true
    }

    /** Desde la revisión, vuelve al paso que se quiere corregir. */
    fun irAPaso(paso: Int) = _uiState.update {
        if (it.enviando) it else it.copy(paso = paso.coerceIn(0, PASO_REVISAR), errorGeneral = null)
    }

    fun registrar() {
        val s = _uiState.value
        if (s.enviando) return
        val errores = validar(s)
        val ubicacion = s.ubicacion
        if (errores.isNotEmpty() || ubicacion == null) {
            // Con datos inválidos no se crea ninguna cuenta: se vuelve al primer paso con errores.
            _uiState.update { it.copy(errores = errores, errorGeneral = REVISA_CAMPOS, paso = primerPaso(errores, it.paso)) }
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
            _uiState.update {
                it.copy(enviando = false, errores = errores2, errorGeneral = general, paso = primerPaso(errores2, it.paso))
            }
        }
    }

    private fun validar(s: RegistroPrestadorUiState): Map<String, MensajeCampo> = buildMap {
        fun error(campo: String, @StringRes id: Int) = put(campo, MensajeCampo.Recurso(id))
        AuthViewModel.errorDeCorreo(s.correo)?.let { error(Campo.CORREO, it) }
        if (s.clave.length < AuthViewModel.CLAVE_MINIMA) error(Campo.CLAVE, R.string.error_clave_minima)
        if (s.nombres.isBlank()) error(Campo.NOMBRES, R.string.error_obligatorio)
        if (s.apellidos.isBlank()) error(Campo.APELLIDOS, R.string.error_obligatorio)
        if (s.dni.length != 8) error(Campo.DNI, R.string.error_dni)
        if (s.telefono.isNotEmpty() && s.telefono.length != TELEFONO_DIGITOS) error(Campo.TELEFONO, R.string.error_telefono)
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
        const val PASO_CUENTA = 0
        const val PASO_DATOS = 1
        const val PASO_RUBROS = 2
        const val PASO_COBERTURA = 3
        const val PASO_REVISAR = 4
        const val TELEFONO_DIGITOS = 9

        /** En qué paso se corrige cada campo (también los que devuelve el servidor). */
        fun pasoDe(campo: String): Int = when (campo) {
            Campo.CORREO, Campo.CLAVE -> PASO_CUENTA
            Campo.NOMBRES, Campo.APELLIDOS, Campo.DNI, Campo.TELEFONO -> PASO_DATOS
            Campo.RUBROS -> PASO_RUBROS
            Campo.UBICACION, Campo.LONGITUD, Campo.RADIO -> PASO_COBERTURA
            else -> PASO_REVISAR
        }

        private fun primerPaso(errores: Map<String, MensajeCampo>, actual: Int) =
            errores.keys.minOfOrNull(::pasoDe) ?: actual

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

private val PASOS_PRESTADOR = listOf(
    Paso(R.string.paso_cuenta, R.string.paso_cuenta_ayuda, Icons.Outlined.AccountCircle),
    Paso(R.string.paso_datos, R.string.paso_datos_ayuda, Icons.Outlined.Badge),
    Paso(R.string.paso_rubros, R.string.paso_rubros_ayuda, Icons.Outlined.Handyman),
    Paso(R.string.paso_cobertura, R.string.paso_cobertura_ayuda, Icons.Outlined.Map),
    Paso(R.string.paso_revisar, R.string.paso_revisar_ayuda, Icons.Outlined.FactCheck),
)

@Composable
fun RegistroPrestadorScreen(onVolver: () -> Unit) {
    val vm = chambaViewModel {
        RegistroPrestadorViewModel(it.authRepository, it.registroPrestadorRepository, it.prestadorRepository)
    }
    val estado by vm.uiState.collectAsStateWithLifecycle()
    val habilitado = !estado.enviando
    val ultimo = estado.paso == RegistroPrestadorViewModel.PASO_REVISAR

    Asistente(
        titulo = R.string.registro_prestador_titulo,
        pasos = PASOS_PRESTADOR,
        actual = estado.paso,
        onAtras = { if (!vm.atras()) onVolver() },
        onSiguiente = vm::siguiente,
        textoSiguiente = if (ultimo) R.string.registro_prestador_boton else R.string.siguiente,
        ocupado = estado.enviando,
        error = estado.errorGeneral?.texto(),
        pie = {
            if (estado.paso == 0) {
                TextButton(onClick = onVolver, enabled = habilitado, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.ya_tengo_cuenta))
                }
            }
        },
    ) { paso ->
        when (paso) {
            RegistroPrestadorViewModel.PASO_CUENTA -> {
                CampoFormulario(
                    estado.correo, { vm.cambiar(Campo.CORREO, it) }, R.string.correo, Icons.Outlined.Email,
                    error = estado.errores[Campo.CORREO]?.texto(), habilitado = habilitado, enfocar = true,
                    teclado = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                CampoFormulario(
                    estado.clave, { vm.cambiar(Campo.CLAVE, it) }, R.string.clave, Icons.Outlined.Lock,
                    error = estado.errores[Campo.CLAVE]?.texto(), ayuda = R.string.clave_ayuda, habilitado = habilitado,
                    teclado = KeyboardOptions(keyboardType = KeyboardType.Password), clave = true, alConfirmar = vm::siguiente,
                )
            }
            RegistroPrestadorViewModel.PASO_DATOS -> {
                val palabras = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                CampoFormulario(
                    estado.nombres, { vm.cambiar(Campo.NOMBRES, it) }, R.string.nombres, Icons.Outlined.Person,
                    error = estado.errores[Campo.NOMBRES]?.texto(), habilitado = habilitado, teclado = palabras, enfocar = true,
                )
                CampoFormulario(
                    estado.apellidos, { vm.cambiar(Campo.APELLIDOS, it) }, R.string.apellidos, Icons.Outlined.Person,
                    error = estado.errores[Campo.APELLIDOS]?.texto(), habilitado = habilitado, teclado = palabras,
                )
                CampoFormulario(
                    estado.dni, { vm.cambiar(Campo.DNI, it) }, R.string.dni, Icons.Outlined.Badge,
                    error = estado.errores[Campo.DNI]?.texto(), ayuda = R.string.dni_ayuda, habilitado = habilitado,
                    teclado = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                CampoFormulario(
                    estado.telefono, { vm.cambiar(Campo.TELEFONO, it) }, R.string.telefono_opcional, Icons.Outlined.Phone,
                    error = estado.errores[Campo.TELEFONO]?.texto(), habilitado = habilitado,
                    teclado = KeyboardOptions(keyboardType = KeyboardType.Phone), alConfirmar = vm::siguiente,
                )
            }
            RegistroPrestadorViewModel.PASO_RUBROS -> PasoRubros(estado, vm)
            RegistroPrestadorViewModel.PASO_COBERTURA -> PasoCobertura(estado, vm)
            else -> PasoRevisar(estado, vm::irAPaso)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PasoRubros(estado: RegistroPrestadorUiState, vm: RegistroPrestadorViewModel) {
    val rubros = estado.rubrosDisponibles
    val errorRubros = estado.errorRubros
    when {
        rubros != null -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            rubros.forEach { rubro ->
                val elegido = rubro.id in estado.rubros
                FilterChip(
                    selected = elegido,
                    onClick = { vm.alternarRubro(rubro.id) },
                    enabled = !estado.enviando,
                    label = { Text(rubro.nombre) },
                    leadingIcon = {
                        AnimatedVisibility(elegido, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
                        }
                    },
                )
            }
        }
        errorRubros != null -> Mensaje(textoError(errorRubros), textoBoton = stringResource(R.string.reintentar), onBoton = vm::cargarRubros)
        else -> Cargando()
    }
    if (estado.rubros.isNotEmpty()) {
        Text(
            pluralStringResource(R.plurals.rubros_elegidos, estado.rubros.size, estado.rubros.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    ErrorCampo(estado.errores[Campo.RUBROS])
}

@Composable
private fun PasoCobertura(estado: RegistroPrestadorUiState, vm: RegistroPrestadorViewModel) {
    // El centro de la zona también se puede escribir (calle o distrito) o tomar del GPS: nadie
    // tiene que adivinar dónde tocar en el mapa.
    var buscandoZona by rememberSaveable { mutableStateOf(false) }
    androidx.compose.material3.OutlinedButton(
        onClick = { buscandoZona = true },
        enabled = !estado.enviando,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.cobertura_buscar_direccion), modifier = Modifier.padding(start = 8.dp))
    }
    if (buscandoZona) {
        HojaUbicacion(
            titulo = stringResource(R.string.cobertura_hoja_titulo),
            actual = null,
            guardada = null,
            onElegida = { vm.elegirUbicacion(it.coordenadas.latitud, it.coordenadas.longitud) },
            onCerrar = { buscandoZona = false },
        )
    }
    MapaBusqueda(
        ubicacion = estado.ubicacion,
        prestadores = emptyList(),
        encuadre = 0,
        onElegirUbicacion = vm::elegirUbicacion,
        radioKm = estado.radioKm,
    )
    val marcada = estado.ubicacion != null
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            if (marcada) Icons.Filled.CheckCircle else Icons.Outlined.TouchApp,
            contentDescription = null,
            tint = if (marcada) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(if (marcada) R.string.cobertura_marcada else R.string.cobertura_toca_mapa),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    ErrorCampo(estado.errores[Campo.UBICACION] ?: estado.errores[Campo.LONGITUD])
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.radio_cobertura), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(
            stringResource(R.string.registro_radio, estado.radioKm),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Slider(
        value = estado.radioKm.toFloat(),
        onValueChange = { vm.cambiarRadio(it.roundToInt()) },
        valueRange = RegistroPrestadorViewModel.RADIO_MINIMO.toFloat()..RegistroPrestadorViewModel.RADIO_MAXIMO.toFloat(),
        enabled = !estado.enviando,
        colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant),
    )
    ErrorCampo(estado.errores[Campo.RADIO])
}

/** Resumen antes de enviar (form-design: revisión en formularios con consecuencias). */
@Composable
private fun PasoRevisar(estado: RegistroPrestadorUiState, onEditar: (Int) -> Unit) {
    val habilitado = !estado.enviando
    val nombresRubros = estado.rubrosDisponibles.orEmpty().filter { it.id in estado.rubros }.joinToString(" · ") { it.nombre }
    FilaResumen(R.string.paso_cuenta, estado.correo.trim(), RegistroPrestadorViewModel.PASO_CUENTA, onEditar, habilitado)
    FilaResumen(
        R.string.paso_datos,
        listOfNotNull(
            "${estado.nombres.trim()} ${estado.apellidos.trim()}",
            stringResource(R.string.resumen_dni, estado.dni),
            estado.telefono.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.solicitud_contacto, it) },
        ).joinToString("\n"),
        RegistroPrestadorViewModel.PASO_DATOS, onEditar, habilitado,
    )
    FilaResumen(R.string.paso_rubros, nombresRubros, RegistroPrestadorViewModel.PASO_RUBROS, onEditar, habilitado)
    FilaResumen(
        R.string.paso_cobertura,
        stringResource(R.string.resumen_cobertura, estado.radioKm),
        RegistroPrestadorViewModel.PASO_COBERTURA, onEditar, habilitado,
    )
    Text(
        stringResource(R.string.registro_prestador_ayuda),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun FilaResumen(@StringRes titulo: Int, valor: String, paso: Int, onEditar: (Int) -> Unit, habilitado: Boolean) {
    val descripcion = stringResource(R.string.editar_seccion, stringResource(titulo))
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(titulo), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(valor, style = MaterialTheme.typography.bodyLarge)
            }
            IconButton(onClick = { onEditar(paso) }, enabled = habilitado) {
                Icon(Icons.Outlined.Edit, contentDescription = descripcion)
            }
        }
    }
}

@Composable
private fun ErrorCampo(mensaje: MensajeCampo?) {
    var ultimo by remember { mutableStateOf(mensaje) }
    if (mensaje != null) ultimo = mensaje
    AnimatedVisibility(mensaje != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            Text(ultimo?.texto().orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
