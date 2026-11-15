package pe.tecsup.chambape.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import pe.tecsup.chambape.ChambaPeApplication
import pe.tecsup.chambape.R
import pe.tecsup.chambape.domain.model.EstadoSesion
import pe.tecsup.chambape.domain.model.Rol
import pe.tecsup.chambape.domain.model.Sesion
import pe.tecsup.chambape.ui.busqueda.BusquedaRoute

private object Rutas {
    const val LOGIN = "login"
    const val REGISTRO = "registro"
    const val BUSCAR = "buscar"
    const val FAVORITOS = "favoritos"
    const val SOLICITUDES = "solicitudes"
    const val PERFIL = "perfil"
    const val DETALLE = "prestador/{id}"
    fun detalle(id: Int) = "prestador/$id"
}

private data class Pestana(val ruta: String, @StringRes val titulo: Int, val icono: ImageVector)

private val PestanasCliente = listOf(
    Pestana(Rutas.BUSCAR, R.string.tab_buscar, Icons.Filled.Search),
    Pestana(Rutas.FAVORITOS, R.string.tab_favoritos, Icons.Filled.Favorite),
    Pestana(Rutas.SOLICITUDES, R.string.tab_solicitudes, Icons.AutoMirrored.Filled.List),
    Pestana(Rutas.PERFIL, R.string.tab_perfil, Icons.Filled.Person),
)

private val PestanasPrestador = listOf(
    Pestana(Rutas.SOLICITUDES, R.string.tab_recibidas, Icons.AutoMirrored.Filled.List),
    Pestana(Rutas.PERFIL, R.string.tab_perfil, Icons.Filled.Person),
)

/** Raíz de la app: el estado de la sesión decide entre ingreso y zona privada. */
@Composable
fun ChambaPeApp() {
    val auth = (LocalContext.current.applicationContext as ChambaPeApplication).container.authRepository
    val estado by auth.sesion.collectAsStateWithLifecycle()
    Surface(modifier = Modifier.fillMaxSize()) {
        when (val e = estado) {
            EstadoSesion.Cargando -> PantallaCentrada { Cargando() }
            EstadoSesion.SinSesion -> NavegacionIngreso()
            // Al cambiar de cuenta se recrea toda la navegación: no queda estado del usuario anterior.
            is EstadoSesion.Activa -> key(e.sesion.uid) { NavegacionPrincipal(e.sesion, onCerrarSesion = auth::cerrarSesion) }
        }
    }
}

@Composable
private fun NavegacionIngreso() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Rutas.LOGIN) {
        composable(Rutas.LOGIN) { LoginScreen(onIrARegistro = { nav.navigate(Rutas.REGISTRO) }) }
        composable(Rutas.REGISTRO) { RegistroScreen(onVolver = { nav.popBackStack() }) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavegacionPrincipal(sesion: Sesion, onCerrarSesion: () -> Unit) {
    val nav = rememberNavController()
    // El rol (leído de Firestore) decide qué pestañas existen.
    val pestanas = if (sesion.rol == Rol.PRESTADOR) PestanasPrestador else PestanasCliente
    val rutaActual = nav.currentBackStackEntryAsState().value?.destination?.route
    val pestanaActual = pestanas.firstOrNull { it.ruta == rutaActual }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(pestanaActual?.titulo ?: R.string.detalle_titulo)) },
                navigationIcon = {
                    if (pestanaActual == null) {
                        IconButton(onClick = { nav.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.volver))
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (pestanaActual != null) {
                NavigationBar {
                    pestanas.forEach { pestana ->
                        NavigationBarItem(
                            selected = pestana == pestanaActual,
                            onClick = {
                                nav.navigate(pestana.ruta) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(pestana.icono, contentDescription = null) },
                            label = { Text(stringResource(pestana.titulo)) },
                        )
                    }
                }
            }
        },
    ) { relleno ->
        NavHost(navController = nav, startDestination = pestanas.first().ruta, modifier = Modifier.padding(relleno)) {
            if (sesion.rol == Rol.CLIENTE) {
                composable(Rutas.BUSCAR) { BusquedaRoute(onAbrirPrestador = { nav.navigate(Rutas.detalle(it)) }) }
                composable(Rutas.FAVORITOS) { FavoritosScreen(onAbrirPrestador = { nav.navigate(Rutas.detalle(it)) }) }
                composable(Rutas.DETALLE, arguments = listOf(navArgument("id") { type = NavType.IntType })) { entrada ->
                    DetalleScreen(prestadorId = entrada.arguments?.getInt("id") ?: -1, sesion = sesion)
                }
            }
            composable(Rutas.SOLICITUDES) { SolicitudesScreen(sesion) }
            composable(Rutas.PERFIL) { PerfilScreen(sesion, onCerrarSesion) }
        }
    }
}
