import { Navigate, useLocation } from 'react-router-dom'

// Sin sesión manda a iniciar sesión (y vuelve aquí después); con otro rol explica que no tiene acceso.
export default function RutaPrivada({ usuario, rol, children }) {
  const ubicacion = useLocation()
  if (usuario === undefined) return <p className="pagina-estado" role="status">Cargando…</p>
  if (usuario === null) return <Navigate to="/ingresar" replace state={{ desde: ubicacion.pathname }} />
  if (usuario.rol !== rol) return <Navigate to="/sin-acceso" replace state={{ seccion: rol, desde: ubicacion.pathname }} />
  return children
}
