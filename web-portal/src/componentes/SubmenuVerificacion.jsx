import { NavLink, useLocation } from 'react-router-dom'
import { RUTAS_VERIFICACION, SUBMENU_VERIFICACION } from '../nucleo/roles.js'

// Pestañas de la sección «Verificación» del prestador; solo aparecen dentro de esa sección.
export default function SubmenuVerificacion() {
  const { pathname } = useLocation()
  if (!RUTAS_VERIFICACION.includes(pathname)) return null
  return (
    <nav className="submenu contenedor" aria-label="Verificación de identidad">
      {SUBMENU_VERIFICACION.map(([ruta, texto]) => <NavLink key={ruta} to={ruta} end>{texto}</NavLink>)}
    </nav>
  )
}
