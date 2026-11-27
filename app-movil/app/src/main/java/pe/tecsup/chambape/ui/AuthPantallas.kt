package pe.tecsup.chambape.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.tecsup.chambape.R
import pe.tecsup.chambape.domain.model.TipoErrorAuth

/** Ingreso: marca arriba, formulario corto y dos caminos claros para crear cuenta. */
@Composable
fun LoginScreen(onIrARegistro: () -> Unit, onIrARegistroPrestador: () -> Unit) {
    val vm = chambaViewModel { AuthViewModel(it.authRepository) }
    val estado by vm.uiState.collectAsStateWithLifecycle()
    val habilitado = !estado.cargando
    val error = when {
        estado.camposVacios -> stringResource(R.string.error_campos_vacios)
        estado.error != null -> textoErrorAuth(estado.error!!)
        else -> null
    }
    // Entrada única al abrir: la marca y el formulario suben un poco mientras aparecen.
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedVisibility(
            visibleState = visible,
            enter = fadeIn(tween(Movimiento.LARGA)) +
                slideInVertically(tween(Movimiento.LARGA, easing = Movimiento.Desacelerada)) { it / 8 },
        ) {
            Column(
                modifier = Modifier.widthIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Marca()
                Text(
                    stringResource(R.string.login_titulo),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 12.dp).semantics { heading() },
                )
                CampoFormulario(
                    estado.correo, vm::cambiarCorreo, R.string.correo, Icons.Outlined.Email,
                    habilitado = habilitado, teclado = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                CampoFormulario(
                    estado.clave, vm::cambiarClave, R.string.clave, Icons.Outlined.Lock,
                    habilitado = habilitado, teclado = KeyboardOptions(keyboardType = KeyboardType.Password),
                    clave = true, alConfirmar = vm::ingresar,
                )
                AvisoError(error)
                BotonPrincipal(stringResource(R.string.ingresar), vm::ingresar, cargando = estado.cargando)

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    Text(
                        stringResource(R.string.no_tienes_cuenta),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }
                OpcionCuenta(Icons.Outlined.PersonAdd, R.string.ir_a_registro, R.string.ir_a_registro_ayuda, habilitado, onIrARegistro)
                OpcionCuenta(
                    Icons.Outlined.Storefront, R.string.ir_a_registro_prestador, R.string.ir_a_registro_prestador_ayuda,
                    habilitado, onIrARegistroPrestador,
                )
            }
        }
    }
}

@Composable
private fun Marca() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // El mismo icono del lanzador y del arranque: se muestra la zona visible (72 de 108 dp) de sus capas.
        Box(modifier = Modifier.size(52.dp).clip(MaterialTheme.shapes.medium), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_launcher_background), contentDescription = null, modifier = Modifier.requiredSize(78.dp))
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.requiredSize(78.dp))
        }
        Column {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.lema), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun OpcionCuenta(icono: ImageVector, @StringRes titulo: Int, @StringRes ayuda: Int, habilitado: Boolean, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, enabled = habilitado, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(icono, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(titulo), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(ayuda), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

private val PASOS_CLIENTE = listOf(
    Paso(R.string.paso_nombre, R.string.paso_nombre_ayuda, Icons.Outlined.Person),
    Paso(R.string.paso_correo, R.string.paso_correo_ayuda, Icons.Outlined.Email),
    Paso(R.string.paso_clave, R.string.paso_clave_ayuda, Icons.Outlined.Lock),
)

/** Registro de cliente en tres pasos cortos: una pregunta por pantalla. */
@Composable
fun RegistroScreen(onVolver: () -> Unit) {
    val vm = chambaViewModel { AuthViewModel(it.authRepository) }
    val estado by vm.uiState.collectAsStateWithLifecycle()
    val habilitado = !estado.cargando
    Asistente(
        titulo = R.string.registro_titulo,
        pasos = PASOS_CLIENTE,
        actual = estado.paso,
        onAtras = { if (!vm.atrasRegistro()) onVolver() },
        onSiguiente = vm::siguienteRegistro,
        textoSiguiente = if (estado.paso == AuthViewModel.PASO_CLAVE) R.string.crear_cuenta else R.string.siguiente,
        ocupado = estado.cargando,
        error = estado.error?.let { textoErrorAuth(it) },
    ) { paso ->
        when (paso) {
            AuthViewModel.PASO_NOMBRE -> CampoFormulario(
                estado.nombre, vm::cambiarNombre, R.string.nombre, Icons.Outlined.Person,
                error = estado.errorNombre?.let { stringResource(it) }, habilitado = habilitado,
                teclado = KeyboardOptions(capitalization = KeyboardCapitalization.Words), alConfirmar = vm::siguienteRegistro, enfocar = true,
            )
            AuthViewModel.PASO_CORREO -> CampoFormulario(
                estado.correo, vm::cambiarCorreo, R.string.correo, Icons.Outlined.Email,
                error = estado.errorCorreo?.let { stringResource(it) }, habilitado = habilitado,
                teclado = KeyboardOptions(keyboardType = KeyboardType.Email), alConfirmar = vm::siguienteRegistro, enfocar = true,
            )
            else -> CampoFormulario(
                estado.clave, vm::cambiarClave, R.string.clave, Icons.Outlined.Lock,
                error = estado.errorClave?.let { stringResource(it) }, ayuda = R.string.clave_ayuda, habilitado = habilitado,
                teclado = KeyboardOptions(keyboardType = KeyboardType.Password), clave = true, alConfirmar = vm::siguienteRegistro, enfocar = true,
            )
        }
    }
}

@Composable
private fun textoErrorAuth(tipo: TipoErrorAuth): String = stringResource(
    when (tipo) {
        TipoErrorAuth.CREDENCIALES -> R.string.error_credenciales
        TipoErrorAuth.CORREO_EN_USO -> R.string.error_correo_en_uso
        TipoErrorAuth.CLAVE_DEBIL -> R.string.error_clave_debil
        TipoErrorAuth.CORREO_INVALIDO -> R.string.error_correo_invalido
        TipoErrorAuth.SIN_CONEXION -> R.string.error_sin_conexion
        TipoErrorAuth.CUENTA_SANCIONADA -> R.string.error_cuenta_sancionada
        TipoErrorAuth.DEMASIADOS_INTENTOS -> R.string.error_demasiados_intentos
        TipoErrorAuth.DESCONOCIDO -> R.string.error_desconocido
    },
)
