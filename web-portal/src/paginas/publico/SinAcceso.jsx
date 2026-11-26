import { Link, useLocation } from 'react-router-dom'
import { INICIO_POR_ROL, NOMBRE_SECCION } from '../../nucleo/roles.js'

// Alguien con sesión entró a una pantalla de otro rol: se explica en vez de mandarlo al inicio sin aviso.
export default function SinAcceso({ usuario }) {
  const { state } = useLocation()
  const seccion = NOMBRE_SECCION[state?.seccion]
  const inicio = INICIO_POR_ROL[usuario?.rol] ?? '/'
  return (
    <main className="pagina-estado contenedor">
      <p className="etiqueta">Acceso restringido</p>
      <h2>Esta pantalla no es para tu cuenta</h2>
      <p>
        {seccion ? `Es una sección para ${seccion}. ` : ''}
        Si crees que deberías verla, cierra sesión e ingresa con la cuenta correcta.
      </p>
      <Link className="boton boton-principal" to={inicio}>Ir a mi inicio</Link>
    </main>
  )
}
