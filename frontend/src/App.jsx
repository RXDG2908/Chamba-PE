import { useEffect, useState } from 'react'
import { Link, Navigate, NavLink, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import './App.css'
import './Cuenta.css'
import { enviarJson, NOMBRE_ROL } from './api.js'
import Disponibilidad from './Disponibilidad.jsx'
import IniciarSesion from './IniciarSesion.jsx'
import Inicio from './Inicio.jsx'
import RegistroCliente from './RegistroCliente.jsx'
import RegistroPrestador from './RegistroPrestador.jsx'
import ResultadosPrestadores from './ResultadosPrestadores.jsx'
import UploadCertificado from './UploadCertificado.jsx'
import RevisionDocumentos from './RevisionDocumentos.jsx'

// Qué ve cada quien en el menú. La API protege los datos; el menú solo evita ofrecer lo que no le toca.
const MENU_POR_ROL = {
  visitante: [
    ['/resultados', 'Buscar servicios'],
    ['/registro-prestador', 'Ofrecer mis servicios'],
  ],
  cliente: [['/resultados', 'Buscar servicios']],
  prestador: [
    ['/disponibilidad', 'Mi disponibilidad'],
    ['/certificaciones', 'Cargar certificado'],
  ],
  administrador: [['/administracion/revisiones', 'Revisión de documentos']],
}

// Sin sesión manda a iniciar sesión; con otro rol vuelve al inicio.
function RutaPrivada({ usuario, rol, children }) {
  const ubicacion = useLocation()
  if (usuario === undefined) return <p className="registro cuenta" role="status">Cargando…</p>
  if (usuario === null) return <Navigate to="/ingresar" replace state={{ desde: ubicacion.pathname }} />
  if (usuario.rol !== rol) return <Navigate to="/" replace />
  return children
}

function App() {
  const navegar = useNavigate()
  // undefined mientras se consulta, null sin sesión, objeto con la sesión iniciada.
  const [usuario, setUsuario] = useState(undefined)
  const [cerrando, setCerrando] = useState(false)

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
      await enviarJson('/api/usuarios/logout/', 'POST')
      setUsuario(null)
      navegar('/ingresar')
    } finally {
      setCerrando(false)
    }
  }

  // Mientras se consulta la sesión no se muestra un menú que luego cambie.
  const enlaces = usuario === undefined ? [] : MENU_POR_ROL[usuario?.rol ?? 'visitante'] ?? []

  return (
    <>
      <header className="cabecera">
        <div className="contenedor cabecera-fila">
          <Link to="/" className="marca" aria-label="ChambaPE, ir al inicio">
            <span className="marca-icono" aria-hidden="true">C</span>
            <span className="marca-texto">Chamba<span>PE</span></span>
          </Link>
          <nav className="menu" aria-label="Menú principal">
            <NavLink to="/" end>Inicio</NavLink>
            {enlaces.map(([ruta, texto]) => <NavLink key={ruta} to={ruta}>{texto}</NavLink>)}
          </nav>
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
                  {cerrando ? 'Cerrando…' : 'Cerrar sesión'}
                </button>
              </>
            )}
            {usuario === null && (
              <>
                <NavLink to="/ingresar" className="enlace-sesion">Iniciar sesión</NavLink>
                <NavLink to="/crear-cuenta" className="boton-cabecera">Crear cuenta</NavLink>
              </>
            )}
          </div>
        </div>
      </header>

      <Routes>
        <Route path="/" element={<Inicio usuario={usuario} />} />
        <Route
          path="/registro-prestador"
          element={usuario ? <Navigate to="/" replace /> : <RegistroPrestador />}
        />
        <Route path="/resultados" element={<ResultadosPrestadores />} />
        <Route
          path="/certificaciones"
          element={<RutaPrivada usuario={usuario} rol="prestador"><UploadCertificado /></RutaPrivada>}
        />

        <Route
          path="/administracion/revisiones"
          element={<RutaPrivada usuario={usuario} rol="administrador"><RevisionDocumentos /></RutaPrivada>}
        />

        <Route
          path="/disponibilidad"
          element={<RutaPrivada usuario={usuario} rol="prestador"><Disponibilidad /></RutaPrivada>}
        />
        <Route
          path="/ingresar"
          element={<IniciarSesion usuario={usuario} alIniciar={setUsuario} />}
        />
        <Route
          path="/crear-cuenta"
          element={<RegistroCliente usuario={usuario} alIniciar={setUsuario} />}
        />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>

      <footer className="pie">
        <div className="contenedor pie-fila">
          <p><strong>Chamba<span>PE</span></strong> · Talento local, cerca de ti.</p>
          <p>Prestadores revisados por administración antes de aparecer en la búsqueda.</p>
        </div>
      </footer>
    </>
  )
}

export default App
