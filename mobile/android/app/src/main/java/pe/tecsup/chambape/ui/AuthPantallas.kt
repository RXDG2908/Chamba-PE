package pe.tecsup.chambape.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.tecsup.chambape.R
import pe.tecsup.chambape.domain.model.TipoErrorAuth

@Composable
fun LoginScreen(onIrARegistro: () -> Unit) {
    val vm = chambaViewModel { AuthViewModel(it.authRepository) }
    val estado by vm.uiState.collectAsStateWithLifecycle()
    FormularioAuth(titulo = stringResource(R.string.login_titulo), estado = estado) {
        CampoCorreo(estado.correo, vm::cambiarCorreo, !estado.cargando)
        CampoClave(estado.clave, vm::cambiarClave, !estado.cargando)
        Button(onClick = vm::ingresar, enabled = !estado.cargando, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (estado.cargando) R.string.ingresando else R.string.ingresar))
        }
        TextButton(onClick = onIrARegistro, enabled = !estado.cargando) {
            Text(stringResource(R.string.ir_a_registro))
        }
    }
}

@Composable
fun RegistroScreen(onVolver: () -> Unit) {
    val vm = chambaViewModel { AuthViewModel(it.authRepository) }
    val estado by vm.uiState.collectAsStateWithLifecycle()
    FormularioAuth(titulo = stringResource(R.string.registro_titulo), estado = estado) {
        Text(stringResource(R.string.registro_ayuda), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = estado.nombre,
            onValueChange = vm::cambiarNombre,
            label = { Text(stringResource(R.string.nombre)) },
            singleLine = true,
            enabled = !estado.cargando,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        CampoCorreo(estado.correo, vm::cambiarCorreo, !estado.cargando)
        CampoClave(estado.clave, vm::cambiarClave, !estado.cargando)
        Button(onClick = vm::registrar, enabled = !estado.cargando, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (estado.cargando) R.string.registrando else R.string.crear_cuenta))
        }
        TextButton(onClick = onVolver, enabled = !estado.cargando) {
            Text(stringResource(R.string.ya_tengo_cuenta))
        }
    }
}

@Composable
private fun FormularioAuth(titulo: String, estado: AuthUiState, campos: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Column(
            modifier = Modifier.widthIn(max = 480.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
            Text(titulo, style = MaterialTheme.typography.titleLarge)
            campos()
            val error = when {
                estado.camposVacios -> stringResource(R.string.error_campos_vacios)
                estado.claveCorta -> stringResource(R.string.error_clave_corta, AuthViewModel.CLAVE_MINIMA)
                estado.error != null -> textoErrorAuth(estado.error)
                else -> null
            }
            if (error != null) {
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
            }
        }
    }
}

@Composable
private fun CampoCorreo(valor: String, onCambio: (String) -> Unit, habilitado: Boolean) {
    OutlinedTextField(
        value = valor,
        onValueChange = onCambio,
        label = { Text(stringResource(R.string.correo)) },
        singleLine = true,
        enabled = habilitado,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CampoClave(valor: String, onCambio: (String) -> Unit, habilitado: Boolean) {
    OutlinedTextField(
        value = valor,
        onValueChange = onCambio,
        label = { Text(stringResource(R.string.clave)) },
        singleLine = true,
        enabled = habilitado,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun textoErrorAuth(tipo: TipoErrorAuth): String = stringResource(
    when (tipo) {
        TipoErrorAuth.CREDENCIALES -> R.string.error_credenciales
        TipoErrorAuth.CORREO_EN_USO -> R.string.error_correo_en_uso
        TipoErrorAuth.CLAVE_DEBIL -> R.string.error_clave_debil
        TipoErrorAuth.CORREO_INVALIDO -> R.string.error_correo_invalido
        TipoErrorAuth.SIN_CONEXION -> R.string.error_sin_conexion
        TipoErrorAuth.DESCONOCIDO -> R.string.error_desconocido
    },
)
