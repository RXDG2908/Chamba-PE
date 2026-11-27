package pe.tecsup.chambape.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.material.icons.outlined.ToggleOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn

private object Rutas {
    const val LOGIN = "login"
    const val REGISTRO = "registro"
    const val REGISTRO_PRESTADOR = "registro-prestador"
    const val BUSCAR = "buscar"
    const val FAVORITOS = "favoritos"
    const val SOLICITUDES = "solicitudes"
    const val DISPONIBILIDAD = "disponibilidad"
    const val PERFIL = "perfil"
    const val DETALLE = "prestador/{id}"
    fun detalle(id: Int) = "prestador/$id"
}

private data class Pestana(val ruta: String, @StringRes val titulo: Int, val icono: ImageVector, val iconoInactivo: ImageVector)

private val PestanasCliente = listOf(
    Pestana(Rutas.BUSCAR, R.string.tab_buscar, Icons.Filled.Search, Icons.Outlined.Search),
    Pestana(Rutas.FAVORITOS, R.string.tab_favoritos, Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder),
    Pestana(Rutas.SOLICITUDES, R.string.tab_solicitudes, Icons.AutoMirrored.Filled.Assignment, Icons.AutoMirrored.Outlined.Assignment),
    Pestana(Rutas.PERFIL, R.string.tab_perfil, Icons.Filled.Person, Icons.Outlined.Person),
)

private val PestanasPrestador = listOf(
    Pestana(Rutas.SOLICITUDES, R.string.tab_recibidas, Icons.Filled.Inbox, Icons.Outlined.Inbox),
    Pestana(Rutas.DISPONIBILIDAD, R.string.tab_disponibilidad, Icons.Filled.ToggleOn, Icons.Outlined.ToggleOff),
    Pestana(Rutas.PERFIL, R.string.tab_perfil, Icons.Filled.Person, Icons.Outlined.Person),
)

/** Raíz de la app: el estado de la sesión decide entre ingreso y zona privada. */
@Composable
fun ChambaPeApp() {
    val container = (LocalContext.current.applicationContext as ChambaPeApplication).container
    val auth = container.authRepository
    val estado by auth.sesion.collectAsStateWithLifecycle()
    Surface(modifier = Modifier.fillMaxSize()) {
        when (val e = estado) {
            EstadoSesion.Cargando -> PantallaCentrada { Cargando() }
            EstadoSesion.SinSesion -> NavegacionIngreso()
            // Al cambiar de cuenta se recrea toda la navegación: no queda estado del usuario anterior.
            is EstadoSesion.Activa -> key(e.sesion.uid) { NavegacionPrincipal(e.sesion, onCerrarSesion = container::cerrarSesion) }
        }
    }
}

@Composable
private fun NavegacionIngreso() {
    val nav = rememberNavController()
    // Abrir un registro entra desde la derecha; volver al ingreso, desde la izquierda (eje X compartido).
    NavHost(
        navController = nav,
        startDestination = Rutas.LOGIN,
        enterTransition = { slideInHorizontally(tween(Movimiento.MEDIA, easing = Movimiento.Enfatizada)) { it / 4 } + fadeIn(tween(Movimiento.MEDIA)) },
        exitTransition = { slideOutHorizontally(tween(Movimiento.MEDIA, easing = Movimiento.Enfatizada)) { -it / 4 } + fadeOut(tween(Movimiento.CORTA)) },
        popEnterTransition = { slideInHorizontally(tween(Movimiento.MEDIA, easing = Movimiento.Enfatizada)) { -it / 4 } + fadeIn(tween(Movimiento.MEDIA)) },
        popExitTransition = { slideOutHorizontally(tween(Movimiento.MEDIA, easing = Movimiento.Enfatizada)) { it / 4 } + fadeOut(tween(Movimiento.CORTA)) },
    ) {
        composable(Rutas.LOGIN) {
            LoginScreen(
                onIrARegistro = { nav.navigate(Rutas.REGISTRO) },
                onIrARegistroPrestador = { nav.navigate(Rutas.REGISTRO_PRESTADOR) },
            )
        }
        composable(Rutas.REGISTRO_PRESTADOR) { RegistroPrestadorScreen(onVolver = { nav.popBackStack() }) }
        composable(Rutas.REGISTRO) { RegistroScreen(onVolver = { nav.popBackStack() }) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavegacionPrincipal(sesion: Sesion, onCerrarSesion: () -> Unit) {
    val nav = rememberNavController()
    // El rol (leído de Django con el token de Firebase) decide qué pestañas existen.
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
                            icon = { Icon(if (pestana == pestanaActual) pestana.icono else pestana.iconoInactivo, contentDescription = null) },
                            label = { Text(stringResource(pestana.titulo)) },
                        )
                    }
                }
            }
        },
    ) { relleno ->
        NavHost(
            navController = nav,
            startDestination = pestanas.first().ruta,
            modifier = Modifier.padding(relleno),
            // Material «fade through» entre pestañas: sale rápido, entra con una leve ampliación.
            enterTransition = { fadeIn(tween(Movimiento.MEDIA, delayMillis = 60)) + scaleIn(tween(Movimiento.MEDIA), initialScale = 0.96f) },
            exitTransition = { fadeOut(tween(Movimiento.CORTA)) },
            popEnterTransition = { fadeIn(tween(Movimiento.MEDIA, delayMillis = 60)) + scaleIn(tween(Movimiento.MEDIA), initialScale = 0.96f) },
            popExitTransition = { fadeOut(tween(Movimiento.CORTA)) },
        ) {
            if (sesion.rol == Rol.CLIENTE) {
                composable(Rutas.BUSCAR) { BusquedaRoute(onAbrirPrestador = { nav.navigate(Rutas.detalle(it)) }) }
                composable(Rutas.FAVORITOS) { FavoritosScreen(onAbrirPrestador = { nav.navigate(Rutas.detalle(it)) }) }
                composable(Rutas.DETALLE, arguments = listOf(navArgument("id") { type = NavType.IntType })) { entrada ->
                    DetalleScreen(prestadorId = entrada.arguments?.getInt("id") ?: -1, sesion = sesion)
                }
            }
            if (sesion.rol == Rol.PRESTADOR) {
                composable(Rutas.DISPONIBILIDAD) { DisponibilidadScreen() }
            }
            composable(Rutas.SOLICITUDES) { SolicitudesScreen(sesion) }
            composable(Rutas.PERFIL) { PerfilScreen(sesion, onCerrarSesion) }
        }
    }
}
