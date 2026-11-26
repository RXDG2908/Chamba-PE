import { useEffect, useState } from 'react'
import { Link, Navigate, NavLink, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import './estilos/App.css'
import './estilos/Cuenta.css'
import { enviarJson, NOMBRE_ROL } from './nucleo/api.js'
import { escuchar, desactivarNotificaciones } from './nucleo/notificaciones.js'
import { INICIO_POR_ROL, MENU_POR_ROL } from './nucleo/roles.js'
import { ICONO_RUTA } from './nucleo/iconos.js'
import { LogIn, LogOut, UserPlus, Wrench } from 'lucide-react'
import RutaPrivada from './componentes/RutaPrivada.jsx'
import SubmenuVerificacion from './componentes/SubmenuVerificacion.jsx'
import LimiteDeError from './componentes/LimiteDeError.jsx'
// Público
import Inicio from './paginas/publico/Inicio.jsx'
import IniciarSesion from './paginas/publico/IniciarSesion.jsx'
import RegistroCliente from './paginas/publico/RegistroCliente.jsx'
import RegistroPrestador from './paginas/publico/RegistroPrestador.jsx'
import ResultadosPrestadores from './paginas/publico/ResultadosPrestadores.jsx'
import SinAcceso from './paginas/publico/SinAcceso.jsx'
import NoEncontrada from './paginas/publico/NoEncontrada.jsx'
// Cliente
import MisServicios from './paginas/cliente/MisServicios.jsx'
// Prestador
import PanelPrestador from './paginas/prestador/PanelPrestador.jsx'
import SolicitudesRecibidas from './paginas/prestador/SolicitudesRecibidas.jsx'
import Disponibilidad from './paginas/prestador/Disponibilidad.jsx'
import VerificacionPrestador from './paginas/prestador/VerificacionPrestador.jsx'
import Consentimiento from './paginas/prestador/Consentimiento.jsx'
import VerificarDni from './paginas/prestador/VerificarDni.jsx'
import VerificarRostro from './paginas/prestador/VerificarRostro.jsx'
import UploadCertificado from './paginas/prestador/UploadCertificado.jsx'
import EliminarDatos from './paginas/prestador/EliminarDatos.jsx'
// Administración
import PanelAdmin from './paginas/admin/PanelAdmin.jsx'
import RevisionDocumentos from './paginas/admin/RevisionDocumentos.jsx'
import GestionCuentas from './paginas/admin/GestionCuentas.jsx'

// Rutas de cada rol: [ruta, componente]. Las URL antiguas se conservan (las usan las notificaciones).
const RUTAS_PRESTADOR = [
  ['/prestador', PanelPrestador],
  ['/disponibilidad', Disponibilidad],
  ['/prestador/verificacion', VerificacionPrestador],
  ['/consentimiento', Consentimiento],
  ['/verificar-dni', VerificarDni],
  ['/verificar-rostro', VerificarRostro],
  ['/certificaciones', UploadCertificado],
  ['/eliminar-datos', EliminarDatos],
]
const RUTAS_ADMIN = [
  ['/administracion', PanelAdmin],
  ['/administracion/revisiones', RevisionDocumentos],
  ['/administracion/cuentas', GestionCuentas],
]

function Menu({ rol }) {
  const { pathname } = useLocation()
  return (
    <nav className="menu" aria-label="Menú principal">
      {(MENU_POR_ROL[rol] ?? []).map(([ruta, texto, tambien]) => {
        const dentro = Boolean(tambien?.includes(pathname))
        const Icono = ICONO_RUTA[ruta]
        return (
          <NavLink key={ruta} to={ruta} end
            className={({ isActive }) => (isActive || dentro ? 'active' : undefined)}
            aria-current={dentro ? 'page' : undefined}>
            {Icono && <Icono className="icono" aria-hidden="true" />}
            {texto}
          </NavLink>
        )
      })}
    </nav>
  )
}

function App() {
  const navegar = useNavigate()
  const { pathname } = useLocation()
  // undefined mientras se consulta, null sin sesión, objeto con la sesión iniciada.
  const [usuario, setUsuario] = useState(undefined)
  const [cerrando, setCerrando] = useState(false)

  useEffect(() => escuchar(usuario), [usuario])

  useEffect(() => {
    // Primero la cookie CSRF y después quién tiene la sesión.
    fetch('/api/usuarios/csrf/')
      .then(() => fetch('/api/usuarios/yo/'))
      .then((r) => (r.ok ? r.json() : null))
      .then(setUsuario)
      .catch(() => setUsuario(null))
  }, [])

  const cerrarSesion = async () => {
    setCerrando(true)
    try {
      try { await desactivarNotificaciones() } catch { /* Django revoca los tokens al cerrar sesión. */ }
      const respuesta = await enviarJson('/api/usuarios/logout/', 'POST')
      if (!respuesta.ok) return
      setUsuario(null)
      navegar('/ingresar')
    } finally {
      setCerrando(false)
    }
  }

  // Mientras se consulta la sesión no se muestra un menú que luego cambie.
  const rol = usuario === undefined ? null : (usuario?.rol ?? 'visitante')
  const privada = (rolRuta, Componente) => (
    <RutaPrivada usuario={usuario} rol={rolRuta}><Componente usuario={usuario} /></RutaPrivada>
  )

  return (
    <>
      <a className="saltar-contenido" href="#contenido">Saltar al contenido</a>
      <header className="cabecera">
        <div className="contenedor cabecera-fila">
          <Link to={INICIO_POR_ROL[rol] ?? '/'} className="marca" aria-label="ChambaPE, ir a mi inicio">
            <span className="marca-icono" aria-hidden="true"><Wrench size={20} strokeWidth={2.4} /></span>
            <span className="marca-texto">Chamba<span>PE</span></span>
          </Link>
          {rol && <Menu rol={rol} />}
          <div className="sesion">
            {usuario && (
              <>
                <span className="sesion-usuario">
                  <span className="sesion-avatar" aria-hidden="true">{(usuario.nombres || usuario.username).charAt(0).toUpperCase()}</span>
                  <span>
                    <strong>{usuario.nombres || usuario.username}</strong>
                    <small>{NOMBRE_ROL[usuario.rol] || 'Sin rol'}</small>
                  </span>
                </span>
                <button type="button" onClick={cerrarSesion} disabled={cerrando}>
                  <LogOut className="icono" size={16} aria-hidden="true" />
                  {cerrando ? 'Cerrando…' : 'Cerrar sesión'}
                </button>
              </>
            )}
            {usuario === null && (
              <>
                <NavLink to="/ingresar" className="enlace-sesion"><LogIn className="icono" size={16} aria-hidden="true" />Iniciar sesión</NavLink>
                <NavLink to="/crear-cuenta" className="boton-cabecera"><UserPlus className="icono" size={16} aria-hidden="true" />Crear cuenta</NavLink>
              </>
            )}
          </div>
        </div>
      </header>
      {usuario?.rol === 'prestador' && <SubmenuVerificacion />}

      <div id="contenido" className="contenido" tabIndex={-1}>
      {/* La clave por ruta reinicia la animación de entrada en cada cambio de página. */}
      <div key={pathname} className="pagina">
      {/* Un error en una pantalla no tumba la web: se muestra un aviso y se puede reintentar. */}
      <LimiteDeError>
      <Routes>
        {/* Público */}
        <Route path="/" element={<Inicio usuario={usuario} />} />
        <Route path="/resultados" element={<ResultadosPrestadores />} />
        <Route path="/registro-prestador" element={usuario ? <Navigate to="/" replace /> : <RegistroPrestador />} />
        <Route path="/ingresar" element={<IniciarSesion usuario={usuario} alIniciar={setUsuario} />} />
        <Route path="/crear-cuenta" element={<RegistroCliente usuario={usuario} alIniciar={setUsuario} />} />
        <Route path="/sin-acceso" element={<SinAcceso usuario={usuario} />} />

        {/* Cliente */}
        <Route path="/mis-servicios" element={privada('cliente', MisServicios)} />

        {/* Prestador */}
        {RUTAS_PRESTADOR.map(([ruta, Componente]) => (
          <Route key={ruta} path={ruta} element={privada('prestador', Componente)} />
        ))}
        {['/solicitudes', '/solicitudes/:id'].map((ruta) => <Route key={ruta} path={ruta}
          element={<RutaPrivada usuario={usuario} rol="prestador"><SolicitudesRecibidas key={ruta} usuario={usuario} alVencer={setUsuario} /></RutaPrivada>} />)}

        {/* Administración */}
        {RUTAS_ADMIN.map(([ruta, Componente]) => (
          <Route key={ruta} path={ruta} element={privada('administrador', Componente)} />
        ))}

        <Route path="*" element={<NoEncontrada usuario={usuario} />} />
      </Routes>
      </LimiteDeError>
      </div>
      </div>

      <footer className="pie">
        <div className="contenedor pie-fila">
          <p><strong>Chamba<span>PE</span></strong> · Talento local, cerca de ti.</p>
          <nav className="pie-enlaces" aria-label="Enlaces del pie">
            <Link to="/resultados">Buscar servicios</Link>
            {!usuario && <Link to="/registro-prestador">Ofrecer mis servicios</Link>}
          </nav>
          <p>Prestadores revisados por administración antes de aparecer en la búsqueda.</p>
        </div>
      </footer>
    </>
  )
}

export default App
