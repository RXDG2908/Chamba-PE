import { useEffect, useState } from 'react'
import { Navigate, NavLink, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import './App.css'
import './Cuenta.css'
import { enviarJson, NOMBRE_ROL } from './api.js'
import Disponibilidad from './Disponibilidad.jsx'
import IniciarSesion from './IniciarSesion.jsx'
import RegistroCliente from './RegistroCliente.jsx'
import RegistroPrestador from './RegistroPrestador.jsx'
import ResultadosPrestadores from './ResultadosPrestadores.jsx'
import UploadCertificado from './UploadCertificado.jsx'

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

  return (
    <>
      <header className="cabecera">
        <div className="contenedor">
          <h1>Chamba<span>PE</span></h1>
          <p>Talento local, cerca de ti.</p>
          <nav className="menu">
            <NavLink to="/" end>Registro de prestador</NavLink>
            <NavLink to="/resultados">Resultados</NavLink>
            <NavLink to="/certificaciones">Cargar certificado</NavLink>
            {usuario?.rol === 'prestador' && <NavLink to="/disponibilidad">Mi disponibilidad</NavLink>}
            {usuario === null && <NavLink to="/ingresar">Iniciar sesión</NavLink>}
            {usuario === null && <NavLink to="/crear-cuenta">Crear cuenta</NavLink>}
          </nav>
          {usuario && (
            <div className="sesion">
              <span>Hola, <strong>{usuario.nombres || usuario.username}</strong> · {NOMBRE_ROL[usuario.rol] || 'Sin rol'}</span>
              <button type="button" onClick={cerrarSesion} disabled={cerrando}>
                {cerrando ? 'Cerrando…' : 'Cerrar sesión'}
              </button>
            </div>
          )}
        </div>
      </header>

      <Routes>
        <Route path="/" element={<RegistroPrestador />} />
        <Route path="/resultados" element={<ResultadosPrestadores />} />
        <Route path="/certificaciones" element={<UploadCertificado />} />
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
    </>
  )
}

export default App
