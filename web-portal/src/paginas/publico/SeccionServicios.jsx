import { Link, Navigate, useParams } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'
import { FichaRubro } from '../../componentes/ExplorarServicios.jsx'
import { useRubros } from '../../nucleo/useRubros.js'
import { rubrosDe, SECCIONES } from '../../nucleo/secciones.js'

/**
 * Una sección («Urgente», «Hogar»…), como la pantalla de sección de la app: cabecera de su color,
 * para qué sirve y sus rubros; cada rubro lleva a los resultados con ese rubro ya elegido.
 */
export default function SeccionServicios() {
  const { clave } = useParams()
  const seccion = SECCIONES.find((s) => s.clave === clave)
  const rubros = useRubros()
  if (!seccion) return <Navigate to="/" replace />
  const propios = rubros ? rubrosDe(seccion, rubros) : []
  const { Icono } = seccion

  return (
    <main className={`pagina-seccion seccion-${seccion.clave}`}>
      <header className="cabecera-seccion">
        <div className="contenedor">
          <Link to="/" className="volver-seccion"><ArrowLeft size={18} aria-hidden="true" />Inicio</Link>
          <h2><Icono size={30} aria-hidden="true" />{seccion.titulo}</h2>
          <p>{seccion.descripcion}</p>
          <Icono className="cabecera-seccion-fondo" size={190} aria-hidden="true" />
        </div>
      </header>
      <section className="contenedor seccion">
        <h3 className="seccion-titulo">¿Qué servicio necesitas?</h3>
        {rubros === null ? (
          <p className="ayuda" role="status">Cargando servicios…</p>
        ) : propios.length === 0 ? (
          <p className="ayuda">Todavía no hay servicios en esta sección.</p>
        ) : (
          <ul className="fichas-rubros fichas-rubros-grilla">
            {propios.map((rubro) => <li key={rubro.id}><FichaRubro rubro={rubro} seccion={seccion} /></li>)}
          </ul>
        )}
        <p className="ayuda pie-seccion">Elige uno y te mostramos a los prestadores verificados cerca de ti, con el recomendado por la IA.</p>
      </section>
    </main>
  )
}
