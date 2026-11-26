import { Link } from 'react-router-dom'
import { INICIO_POR_ROL } from '../../nucleo/roles.js'
import { Compass } from 'lucide-react'

export default function NoEncontrada({ usuario }) {
  return (
    <main className="pagina-estado contenedor">
      <p className="etiqueta">Error 404</p>
      <h2><span className="icono-titulo" aria-hidden="true"><Compass size={22} /></span>No encontramos esta página</h2>
      <p>Puede que el enlace esté mal escrito o que la página ya no exista.</p>
      <div className="acciones-estado">
        <Link className="boton boton-principal" to={INICIO_POR_ROL[usuario?.rol] ?? '/'}>Ir a mi inicio</Link>
        <Link className="boton-secundario" to="/resultados">Buscar un servicio</Link>
      </div>
    </main>
  )
}
