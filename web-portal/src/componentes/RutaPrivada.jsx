import { Navigate, useLocation } from 'react-router-dom'
import Cargando from './Cargando.jsx'

// Sin sesión manda a iniciar sesión (y vuelve aquí después); con otro rol explica que no tiene acceso.
// `rol` es un rol o una lista de roles permitidos.
export default function RutaPrivada({ usuario, rol, children }) {
  const ubicacion = useLocation()
  if (usuario === undefined) return <Cargando className="pagina-estado" texto="Cargando…" />
  if (usuario === null) return <Navigate to="/ingresar" replace state={{ desde: ubicacion.pathname }} />
  const roles = [].concat(rol)
  if (!roles.includes(usuario.rol)) return <Navigate to="/sin-acceso" replace state={{ seccion: roles[0], desde: ubicacion.pathname }} />
  return children
}
